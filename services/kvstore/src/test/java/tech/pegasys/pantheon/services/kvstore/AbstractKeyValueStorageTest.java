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

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.services.kvstore.KeyValueStorage.Transaction;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import org.junit.Ignore;
import org.junit.Test;

@Ignore
public abstract class AbstractKeyValueStorageTest {

  protected abstract KeyValueStorage createStore() throws Exception;

  @Test
  public void twoStoresAreIndependent() throws Exception {
    final KeyValueStorage store1 = createStore();
    final KeyValueStorage store2 = createStore();

    Transaction tx = store1.startTransaction();
    tx.put(BytesValue.fromHexString("0001"), BytesValue.fromHexString("0FFF"));
    tx.commit();
    final Optional<BytesValue> result = store2.get(BytesValue.fromHexString("0001"));
    assertThat(result).isEmpty();
  }

  @Test
  public void put() throws Exception {
    final KeyValueStorage store = createStore();

    Transaction tx = store.startTransaction();
    tx.put(BytesValue.fromHexString("0F"), BytesValue.fromHexString("0ABC"));
    tx.commit();
    assertThat(store.get(BytesValue.fromHexString("0F")))
        .contains(BytesValue.fromHexString("0ABC"));

    tx = store.startTransaction();
    tx.put(BytesValue.fromHexString("0F"), BytesValue.fromHexString("0DEF"));
    tx.commit();
    assertThat(store.get(BytesValue.fromHexString("0F")))
        .contains(BytesValue.fromHexString("0DEF"));
  }

  @Test
  public void removeUnless() throws Exception {
    final KeyValueStorage store = createStore();
    Transaction tx = store.startTransaction();
    tx.put(BytesValue.fromHexString("0F"), BytesValue.fromHexString("0ABC"));
    tx.put(BytesValue.fromHexString("10"), BytesValue.fromHexString("0ABC"));
    tx.put(BytesValue.fromHexString("11"), BytesValue.fromHexString("0ABC"));
    tx.put(BytesValue.fromHexString("12"), BytesValue.fromHexString("0ABC"));
    tx.commit();
    store.removeUnless(bv -> bv.toString().contains("1"));
    assertThat(store.containsKey(BytesValue.fromHexString("0F"))).isFalse();
    assertThat(store.containsKey(BytesValue.fromHexString("10"))).isTrue();
    assertThat(store.containsKey(BytesValue.fromHexString("11"))).isTrue();
    assertThat(store.containsKey(BytesValue.fromHexString("12"))).isTrue();
  }

  @Test
  public void containsKey() throws Exception {
    final KeyValueStorage store = createStore();
    final BytesValue key = BytesValue.fromHexString("ABCD");

    assertThat(store.containsKey(key)).isFalse();

    final Transaction transaction = store.startTransaction();
    transaction.put(key, BytesValue.fromHexString("DEFF"));
    transaction.commit();

    assertThat(store.containsKey(key)).isTrue();
  }

  @Test
  public void removeExisting() throws Exception {
    final KeyValueStorage store = createStore();
    Transaction tx = store.startTransaction();
    tx.put(BytesValue.fromHexString("0F"), BytesValue.fromHexString("0ABC"));
    tx.commit();
    tx = store.startTransaction();
    tx.remove(BytesValue.fromHexString("0F"));
    tx.commit();
    assertThat(store.get(BytesValue.fromHexString("0F"))).isEmpty();
  }

  @Test
  public void removeExistingSameTransaction() throws Exception {
    final KeyValueStorage store = createStore();
    Transaction tx = store.startTransaction();
    tx.put(BytesValue.fromHexString("0F"), BytesValue.fromHexString("0ABC"));
    tx.remove(BytesValue.fromHexString("0F"));
    tx.commit();
    assertThat(store.get(BytesValue.fromHexString("0F"))).isEmpty();
  }

  @Test
  public void removeNonExistent() throws Exception {
    final KeyValueStorage store = createStore();
    Transaction tx = store.startTransaction();
    tx.remove(BytesValue.fromHexString("0F"));
    tx.commit();
    assertThat(store.get(BytesValue.fromHexString("0F"))).isEmpty();
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
                      Transaction tx = store.startTransaction();
                      tx.put(BytesValues.toMinimalBytes(i), value);
                      tx.commit();
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
      assertThat(actual.equals(a) || actual.equals(b)).isTrue();
    }

    store.close();
  }

  @Test
  public void transactionCommit() throws Exception {
    final KeyValueStorage store = createStore();
    // Add some values
    Transaction tx = store.startTransaction();
    tx.put(BytesValue.of(1), BytesValue.of(1));
    tx.put(BytesValue.of(2), BytesValue.of(2));
    tx.put(BytesValue.of(3), BytesValue.of(3));
    tx.commit();

    // Start transaction that adds, modifies, and removes some values
    tx = store.startTransaction();
    tx.put(BytesValue.of(2), BytesValue.of(3));
    tx.put(BytesValue.of(2), BytesValue.of(4));
    tx.remove(BytesValue.of(3));
    tx.put(BytesValue.of(4), BytesValue.of(8));

    // Check values before committing have not changed
    assertThat(store.get(BytesValue.of(1))).contains(BytesValue.of(1));
    assertThat(store.get(BytesValue.of(2))).contains(BytesValue.of(2));
    assertThat(store.get(BytesValue.of(3))).contains(BytesValue.of(3));
    assertThat(store.get(BytesValue.of(4))).isEmpty();

    assertThat(store.get(BytesValue.of(1))).contains(BytesValue.of(1));
    assertThat(store.get(BytesValue.of(2))).contains(BytesValue.of(2));
    assertThat(store.get(BytesValue.of(3))).contains(BytesValue.of(3));
    assertThat(store.get(BytesValue.of(4))).isEmpty();

    tx.commit();

    // Check that values have been updated after commit
    assertThat(store.get(BytesValue.of(1))).contains(BytesValue.of(1));
    assertThat(store.get(BytesValue.of(2))).contains(BytesValue.of(4));
    assertThat(store.get(BytesValue.of(3))).isEmpty();
    assertThat(store.get(BytesValue.of(4))).contains(BytesValue.of(8));
  }

  @Test
  public void transactionRollback() throws Exception {
    final KeyValueStorage store = createStore();
    // Add some values
    Transaction tx = store.startTransaction();
    tx.put(BytesValue.of(1), BytesValue.of(1));
    tx.put(BytesValue.of(2), BytesValue.of(2));
    tx.put(BytesValue.of(3), BytesValue.of(3));
    tx.commit();

    // Start transaction that adds, modifies, and removes some values
    tx = store.startTransaction();
    tx.put(BytesValue.of(2), BytesValue.of(3));
    tx.put(BytesValue.of(2), BytesValue.of(4));
    tx.remove(BytesValue.of(3));
    tx.put(BytesValue.of(4), BytesValue.of(8));

    // Check values before committing have not changed
    assertThat(store.get(BytesValue.of(1))).contains(BytesValue.of(1));
    assertThat(store.get(BytesValue.of(2))).contains(BytesValue.of(2));
    assertThat(store.get(BytesValue.of(3))).contains(BytesValue.of(3));
    assertThat(store.get(BytesValue.of(4))).isEmpty();

    tx.rollback();

    // Check that values have not changed after rollback
    assertThat(store.get(BytesValue.of(1))).contains(BytesValue.of(1));
    assertThat(store.get(BytesValue.of(2))).contains(BytesValue.of(2));
    assertThat(store.get(BytesValue.of(3))).contains(BytesValue.of(3));
    assertThat(store.get(BytesValue.of(4))).isEmpty();
  }

  @Test
  public void transactionCommitEmpty() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.startTransaction();
    tx.commit();
  }

  @Test
  public void transactionRollbackEmpty() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.startTransaction();
    tx.rollback();
  }

  @Test(expected = IllegalStateException.class)
  public void transactionPutAfterCommit() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.startTransaction();
    tx.commit();
    tx.put(BytesValue.of(1), BytesValue.of(1));
  }

  @Test(expected = IllegalStateException.class)
  public void transactionRemoveAfterCommit() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.startTransaction();
    tx.commit();
    tx.remove(BytesValue.of(1));
  }

  @Test(expected = IllegalStateException.class)
  public void transactionPutAfterRollback() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.startTransaction();
    tx.rollback();
    tx.put(BytesValue.of(1), BytesValue.of(1));
  }

  @Test(expected = IllegalStateException.class)
  public void transactionRemoveAfterRollback() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.startTransaction();
    tx.rollback();
    tx.remove(BytesValue.of(1));
  }

  @Test(expected = IllegalStateException.class)
  public void transactionCommitAfterRollback() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.startTransaction();
    tx.rollback();
    tx.commit();
  }

  @Test(expected = IllegalStateException.class)
  public void transactionCommitTwice() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.startTransaction();
    tx.commit();
    tx.commit();
  }

  @Test(expected = IllegalStateException.class)
  public void transactionRollbackAfterCommit() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.startTransaction();
    tx.commit();
    tx.rollback();
  }

  @Test(expected = IllegalStateException.class)
  public void transactionRollbackTwice() throws Exception {
    final KeyValueStorage store = createStore();
    final Transaction tx = store.startTransaction();
    tx.rollback();
    tx.rollback();
  }

  @Test
  public void twoTransactions() throws Exception {
    final KeyValueStorage store = createStore();

    final Transaction tx1 = store.startTransaction();
    final Transaction tx2 = store.startTransaction();

    tx1.put(BytesValue.of(1), BytesValue.of(1));
    tx2.put(BytesValue.of(2), BytesValue.of(2));

    tx1.commit();
    tx2.commit();

    assertThat(store.get(BytesValue.of(1))).contains(BytesValue.of(1));
    assertThat(store.get(BytesValue.of(2))).contains(BytesValue.of(2));
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
                  final Transaction tx = store.startTransaction();
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
    assertThat(finalValues).containsExactly(expectedValues);
    assertThat(finalValues[0].equals(a) || finalValues[0].equals(b)).isTrue();

    store.close();
  }
}
