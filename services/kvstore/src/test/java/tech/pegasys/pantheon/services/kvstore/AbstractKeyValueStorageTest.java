/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.services.kvstore;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import tech.pegasys.pantheon.services.kvstore.KeyValueStorage.Entry;
import tech.pegasys.pantheon.services.kvstore.KeyValueStorage.Transaction;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Ignore;
import org.junit.Test;

@Ignore
public abstract class AbstractKeyValueStorageTest {

  protected abstract KeyValueStorage createStore() throws Exception;

  @Test
  public void twoStoresAreIndependent() throws Exception {
    final KeyValueStorage store1 = createStore();
    final KeyValueStorage store2 = createStore();

    store1.put(BytesValue.fromHexString("0001"), BytesValue.fromHexString("0FFF"));
    final Optional<BytesValue> result = store2.get(BytesValue.fromHexString("0001"));
    assertEquals(Optional.empty(), result);
  }

  @Test
  public void put() throws Exception {
    final KeyValueStorage store = createStore();

    store.put(BytesValue.fromHexString("0F"), BytesValue.fromHexString("0ABC"));
    assertEquals(
        Optional.of(BytesValue.fromHexString("0ABC")), store.get(BytesValue.fromHexString("0F")));

    store.put(BytesValue.fromHexString("0F"), BytesValue.fromHexString("0DEF"));
    assertEquals(
        Optional.of(BytesValue.fromHexString("0DEF")), store.get(BytesValue.fromHexString("0F")));
  }

  @Test
  public void removeExisting() throws Exception {
    final KeyValueStorage store = createStore();
    store.put(BytesValue.fromHexString("0F"), BytesValue.fromHexString("0ABC"));
    store.remove(BytesValue.fromHexString("0F"));
    assertEquals(Optional.empty(), store.get(BytesValue.fromHexString("0F")));
  }

  @Test
  public void removeNonExistent() throws Exception {
    final KeyValueStorage store = createStore();
    store.remove(BytesValue.fromHexString("0F"));
    assertEquals(Optional.empty(), store.get(BytesValue.fromHexString("0F")));
  }

  @Test
  public void entries() throws Exception {
    final KeyValueStorage store = createStore();

    final List<Entry> testEntries =
        Arrays.asList(
            Entry.create(BytesValue.fromHexString("01"), BytesValue.fromHexString("0ABC")),
            Entry.create(BytesValue.fromHexString("02"), BytesValue.fromHexString("0DEF")));
    for (final Entry testEntry : testEntries) {
      store.put(testEntry.getKey(), testEntry.getValue());
    }

    final List<Entry> actualEntries = store.entries().collect(Collectors.toList());
    testEntries.sort(Comparator.comparing(Entry::getKey));
    actualEntries.sort(Comparator.comparing(Entry::getKey));
    assertEquals(2, actualEntries.size());
    assertEquals(testEntries, actualEntries);
  }

  @Test
  public void concurrentUpdate() throws Exception {
    final int keyCount = 1000;
    final KeyValueStorage store = createStore();

    final CountDownLatch finishedLatch = new CountDownLatch(2);
    final Function<BytesValue, Thread> updater =
        (value) ->
            new Thread(
                () -> {
                  try {
                    for (int i = 0; i < keyCount; i++) {
                      store.put(BytesValues.toMinimalBytes(i), value);
                    }
                  } finally {
                    finishedLatch.countDown();
                  }
                });

    // Run 2 concurrent transactions that write a bunch of values to the same keys
    final BytesValue a = BytesValue.of(10);
    final BytesValue b = BytesValue.of(20);
    updater.apply(a).start();
    updater.apply(b).start();

    finishedLatch.await();

    for (int i = 0; i < keyCount; i++) {
      final BytesValue key = BytesValues.toMinimalBytes(i);
      final BytesValue actual = store.get(key).get();
      assertTrue(actual.equals(a) || actual.equals(b));
    }

    if (store instanceof Closeable) {
      ((Closeable) store).close();
    }
  }

  @Test
  public void transactionCommit() throws Exception {
    final KeyValueStorage store = createStore();
    // Add some values
    store.put(BytesValue.of(1), BytesValue.of(1));
    store.put(BytesValue.of(2), BytesValue.of(2));
    store.put(BytesValue.of(3), BytesValue.of(3));

    // Start transaction that adds, modifies, and removes some values
    final Transaction tx = store.getStartTransaction();
    tx.put(BytesValue.of(2), BytesValue.of(3));
    tx.put(BytesValue.of(2), BytesValue.of(4));
    tx.remove(BytesValue.of(3));
    tx.put(BytesValue.of(4), BytesValue.of(8));

    // Check values before committing have not changed
    assertEquals(store.get(BytesValue.of(1)).get(), BytesValue.of(1));
    assertEquals(store.get(BytesValue.of(2)).get(), BytesValue.of(2));
    assertEquals(store.get(BytesValue.of(3)).get(), BytesValue.of(3));
    assertEquals(store.get(BytesValue.of(4)), Optional.empty());

    tx.commit();

    // Check that values have been updated after commit
    assertEquals(store.get(BytesValue.of(1)).get(), BytesValue.of(1));
    assertEquals(store.get(BytesValue.of(2)).get(), BytesValue.of(4));
    assertEquals(store.get(BytesValue.of(3)), Optional.empty());
    assertEquals(store.get(BytesValue.of(4)).get(), BytesValue.of(8));
  }

  @Test
  public void transactionRollback() throws Exception {
    final KeyValueStorage store = createStore();
    // Add some values
    store.put(BytesValue.of(1), BytesValue.of(1));
    store.put(BytesValue.of(2), BytesValue.of(2));
    store.put(BytesValue.of(3), BytesValue.of(3));

    // Start transaction that adds, modifies, and removes some values
    final Transaction tx = store.getStartTransaction();
    tx.put(BytesValue.of(2), BytesValue.of(3));
    tx.put(BytesValue.of(2), BytesValue.of(4));
    tx.remove(BytesValue.of(3));
    tx.put(BytesValue.of(4), BytesValue.of(8));

    // Check values before committing have not changed
    assertEquals(store.get(BytesValue.of(1)).get(), BytesValue.of(1));
    assertEquals(store.get(BytesValue.of(2)).get(), BytesValue.of(2));
    assertEquals(store.get(BytesValue.of(3)).get(), BytesValue.of(3));
    assertEquals(store.get(BytesValue.of(4)), Optional.empty());

    tx.rollback();

    // Check that values have not changed after rollback
    assertEquals(store.get(BytesValue.of(1)).get(), BytesValue.of(1));
    assertEquals(store.get(BytesValue.of(2)).get(), BytesValue.of(2));
    assertEquals(store.get(BytesValue.of(3)).get(), BytesValue.of(3));
    assertEquals(store.get(BytesValue.of(4)), Optional.empty());
  }

  @Test
  public void transactionCommitEmpty() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.getStartTransaction();
    tx.commit();
  }

  @Test
  public void transactionRollbackEmpty() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.getStartTransaction();
    tx.rollback();
  }

  @Test(expected = IllegalStateException.class)
  public void transactionPutAfterCommit() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.getStartTransaction();
    tx.commit();
    tx.put(BytesValue.of(1), BytesValue.of(1));
  }

  @Test(expected = IllegalStateException.class)
  public void transactionRemoveAfterCommit() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.getStartTransaction();
    tx.commit();
    tx.remove(BytesValue.of(1));
  }

  @Test(expected = IllegalStateException.class)
  public void transactionPutAfterRollback() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.getStartTransaction();
    tx.rollback();
    tx.put(BytesValue.of(1), BytesValue.of(1));
  }

  @Test(expected = IllegalStateException.class)
  public void transactionRemoveAfterRollback() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.getStartTransaction();
    tx.rollback();
    tx.remove(BytesValue.of(1));
  }

  @Test(expected = IllegalStateException.class)
  public void transactionCommitAfterRollback() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.getStartTransaction();
    tx.rollback();
    tx.commit();
  }

  @Test(expected = IllegalStateException.class)
  public void transactionCommitTwice() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.getStartTransaction();
    tx.commit();
    tx.commit();
  }

  @Test(expected = IllegalStateException.class)
  public void transactionRollbackAfterCommit() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.getStartTransaction();
    tx.commit();
    tx.rollback();
  }

  @Test(expected = IllegalStateException.class)
  public void transactionRollbackTwice() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.getStartTransaction();
    tx.rollback();
    tx.rollback();
  }

  @Test
  public void twoTransactions() throws Exception {
    final KeyValueStorage store = createStore();

    final Transaction tx1 = store.getStartTransaction();
    final Transaction tx2 = store.getStartTransaction();

    tx1.put(BytesValue.of(1), BytesValue.of(1));
    tx2.put(BytesValue.of(2), BytesValue.of(2));

    tx1.commit();
    tx2.commit();

    assertEquals(store.get(BytesValue.of(1)).get(), BytesValue.of(1));
    assertEquals(store.get(BytesValue.of(2)).get(), BytesValue.of(2));
  }

  @Test
  public void transactionIsolation() throws Exception {
    final int keyCount = 1000;
    final KeyValueStorage store = createStore();

    final CountDownLatch finishedLatch = new CountDownLatch(2);
    final Function<BytesValue, Thread> txRunner =
        (value) ->
            new Thread(
                () -> {
                  final Transaction tx = store.getStartTransaction();
                  for (int i = 0; i < keyCount; i++) {
                    tx.put(BytesValues.toMinimalBytes(i), value);
                  }
                  try {
                    tx.commit();
                  } finally {
                    finishedLatch.countDown();
                  }
                });

    // Run 2 concurrent transactions that write a bunch of values to the same keys
    final BytesValue a = BytesValue.of(10);
    final BytesValue b = BytesValue.of(20);
    txRunner.apply(a).start();
    txRunner.apply(b).start();

    finishedLatch.await();

    // Check that transaction results are isolated (not interleaved)
    final BytesValue[] finalValues = new BytesValue[keyCount];
    final BytesValue[] expectedValues = new BytesValue[keyCount];
    for (int i = 0; i < keyCount; i++) {
      final BytesValue key = BytesValues.toMinimalBytes(i);
      finalValues[i] = store.get(key).get();
    }
    Arrays.fill(expectedValues, 0, keyCount, finalValues[0]);
    assertArrayEquals(expectedValues, finalValues);
    assertTrue(finalValues[0].equals(a) || finalValues[0].equals(b));

    if (store instanceof Closeable) {
      ((Closeable) store).close();
    }
  }
}
