/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.kvstore;

import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** The Abstract key value storage test. */
@Disabled
public abstract class AbstractKeyValueStorageTest {
  /** Default Constructor */
  protected AbstractKeyValueStorageTest() {}

  /**
   * Create store key value storage.
   *
   * @return the key value storage
   * @throws Exception the exception
   */
  protected abstract KeyValueStorage createStore() throws Exception;

  /**
   * Two stores are independent.
   *
   * @throws Exception the exception
   */
  @Test
  public void twoStoresAreIndependent() throws Exception {
    try (final KeyValueStorage store1 = createStore()) {
      try (final KeyValueStorage store2 = createStore()) {

        final KeyValueStorageTransaction tx = store1.startTransaction();
        final byte[] key = bytesFromHexString("0001");
        final byte[] value = bytesFromHexString("0FFF");

        tx.put(key, value);
        tx.commit();

        final Optional<byte[]> result = store2.get(key);
        assertThat(result).isEmpty();
      }
    }
  }

  /**
   * Put.
   *
   * @throws Exception the exception
   */
  @Test
  public void put() throws Exception {
    try (final KeyValueStorage store = createStore()) {
      final byte[] key = bytesFromHexString("0F");
      final byte[] firstValue = bytesFromHexString("0ABC");
      final byte[] secondValue = bytesFromHexString("0DEF");

      KeyValueStorageTransaction tx = store.startTransaction();
      tx.put(key, firstValue);
      tx.commit();
      assertThat(store.get(key)).contains(firstValue);

      tx = store.startTransaction();
      tx.put(key, secondValue);
      tx.commit();
      assertThat(store.get(key)).contains(secondValue);
    }
  }

  /**
   * Stream keys.
   *
   * @throws Exception the exception
   */
  @Test
  public void streamKeys() throws Exception {
    try (final KeyValueStorage store = createStore()) {
      final KeyValueStorageTransaction tx = store.startTransaction();
      final List<byte[]> keys =
          Stream.of("0F", "10", "11", "12")
              .map(this::bytesFromHexString)
              .collect(toUnmodifiableList());
      keys.forEach(key -> tx.put(key, bytesFromHexString("0ABC")));
      tx.commit();
      assertThat(store.stream().map(Pair::getKey).collect(toUnmodifiableSet()))
          .containsExactlyInAnyOrder(keys.toArray(new byte[][] {}));
    }
  }

  /**
   * Gets all keys that.
   *
   * @throws Exception the exception
   */
  @Test
  public void getAllKeysThat() throws Exception {
    try (final KeyValueStorage store = createStore()) {
      final KeyValueStorageTransaction tx = store.startTransaction();
      tx.put(bytesFromHexString("0F"), bytesFromHexString("0ABC"));
      tx.put(bytesFromHexString("10"), bytesFromHexString("0ABC"));
      tx.put(bytesFromHexString("11"), bytesFromHexString("0ABC"));
      tx.put(bytesFromHexString("12"), bytesFromHexString("0ABC"));
      tx.commit();
      Set<byte[]> keys = store.getAllKeysThat(bv -> Bytes.wrap(bv).toString().contains("1"));
      assertThat(keys.size()).isEqualTo(3);
      assertThat(keys)
          .containsExactlyInAnyOrder(
              bytesFromHexString("10"), bytesFromHexString("11"), bytesFromHexString("12"));
    }
  }

  /**
   * Contains key.
   *
   * @throws Exception the exception
   */
  @Test
  public void containsKey() throws Exception {
    try (final KeyValueStorage store = createStore()) {
      final byte[] key = bytesFromHexString("ABCD");
      final byte[] value = bytesFromHexString("DEFF");

      assertThat(store.containsKey(key)).isFalse();

      final KeyValueStorageTransaction transaction = store.startTransaction();
      transaction.put(key, value);
      transaction.commit();

      assertThat(store.containsKey(key)).isTrue();
    }
  }

  /**
   * Remove existing.
   *
   * @throws Exception the exception
   */
  @Test
  public void removeExisting() throws Exception {
    try (final KeyValueStorage store = createStore()) {
      final byte[] key = bytesFromHexString("0F");
      final byte[] value = bytesFromHexString("0ABC");

      KeyValueStorageTransaction tx = store.startTransaction();
      tx.put(key, value);
      tx.commit();

      tx = store.startTransaction();
      tx.remove(key);
      tx.commit();
      assertThat(store.get(key)).isEmpty();
    }
  }

  /**
   * Remove existing same transaction.
   *
   * @throws Exception the exception
   */
  @Test
  public void removeExistingSameTransaction() throws Exception {
    try (final KeyValueStorage store = createStore()) {
      final byte[] key = bytesFromHexString("0F");
      final byte[] value = bytesFromHexString("0ABC");

      KeyValueStorageTransaction tx = store.startTransaction();
      tx.put(key, value);
      tx.remove(key);
      tx.commit();
      assertThat(store.get(key)).isEmpty();
    }
  }

  /**
   * Remove non existent.
   *
   * @throws Exception the exception
   */
  @Test
  public void removeNonExistent() throws Exception {
    try (final KeyValueStorage store = createStore()) {
      final byte[] key = bytesFromHexString("0F");

      KeyValueStorageTransaction tx = store.startTransaction();
      tx.remove(key);
      tx.commit();
      assertThat(store.get(key)).isEmpty();
    }
  }

  /**
   * Concurrent update.
   *
   * @throws Exception the exception
   */
  @Test
  public void concurrentUpdate() throws Exception {
    final int keyCount = 1000;
    try (final KeyValueStorage store = createStore()) {

      final CountDownLatch finishedLatch = new CountDownLatch(2);
      final Function<byte[], Thread> updater =
          (value) ->
              new Thread(
                  () -> {
                    try {
                      for (int i = 0; i < keyCount; i++) {
                        KeyValueStorageTransaction tx = store.startTransaction();
                        tx.put(Bytes.minimalBytes(i).toArrayUnsafe(), value);
                        tx.commit();
                      }
                    } finally {
                      finishedLatch.countDown();
                    }
                  });

      // Run 2 concurrent transactions that write a bunch of values to the same keys
      final byte[] a = Bytes.of(10).toArrayUnsafe();
      final byte[] b = Bytes.of(20).toArrayUnsafe();
      updater.apply(a).start();
      updater.apply(b).start();

      finishedLatch.await();

      for (int i = 0; i < keyCount; i++) {
        final byte[] key = Bytes.minimalBytes(i).toArrayUnsafe();
        final byte[] actual = store.get(key).get();
        assertThat(Arrays.equals(actual, a) || Arrays.equals(actual, b)).isTrue();
      }
    }
  }

  /**
   * Transaction commit.
   *
   * @throws Exception the exception
   */
  @Test
  public void transactionCommit() throws Exception {
    try (final KeyValueStorage store = createStore()) {
      // Add some values
      KeyValueStorageTransaction tx = store.startTransaction();
      tx.put(bytesOf(1), bytesOf(1));
      tx.put(bytesOf(2), bytesOf(2));
      tx.put(bytesOf(3), bytesOf(3));
      tx.commit();

      // Start transaction that adds, modifies, and removes some values
      tx = store.startTransaction();
      tx.put(bytesOf(2), bytesOf(3));
      tx.put(bytesOf(2), bytesOf(4));
      tx.remove(bytesOf(3));
      tx.put(bytesOf(4), bytesOf(8));

      // Check values before committing have not changed
      assertThat(store.get(bytesOf(1))).contains(bytesOf(1));
      assertThat(store.get(bytesOf(2))).contains(bytesOf(2));
      assertThat(store.get(bytesOf(3))).contains(bytesOf(3));
      assertThat(store.get(bytesOf(4))).isEmpty();

      tx.commit();

      // Check that values have been updated after commit
      assertThat(store.get(bytesOf(1))).contains(bytesOf(1));
      assertThat(store.get(bytesOf(2))).contains(bytesOf(4));
      assertThat(store.get(bytesOf(3))).isEmpty();
      assertThat(store.get(bytesOf(4))).contains(bytesOf(8));
    }
  }

  /**
   * Transaction rollback.
   *
   * @throws Exception the exception
   */
  @Test
  public void transactionRollback() throws Exception {
    try (final KeyValueStorage store = createStore()) {
      // Add some values
      KeyValueStorageTransaction tx = store.startTransaction();
      tx.put(bytesOf(1), bytesOf(1));
      tx.put(bytesOf(2), bytesOf(2));
      tx.put(bytesOf(3), bytesOf(3));
      tx.commit();

      // Start transaction that adds, modifies, and removes some values
      tx = store.startTransaction();
      tx.put(bytesOf(2), bytesOf(3));
      tx.put(bytesOf(2), bytesOf(4));
      tx.remove(bytesOf(3));
      tx.put(bytesOf(4), bytesOf(8));

      // Check values before committing have not changed
      assertThat(store.get(bytesOf(1))).contains(bytesOf(1));
      assertThat(store.get(bytesOf(2))).contains(bytesOf(2));
      assertThat(store.get(bytesOf(3))).contains(bytesOf(3));
      assertThat(store.get(bytesOf(4))).isEmpty();

      tx.rollback();

      // Check that values have not changed after rollback
      assertThat(store.get(bytesOf(1))).contains(bytesOf(1));
      assertThat(store.get(bytesOf(2))).contains(bytesOf(2));
      assertThat(store.get(bytesOf(3))).contains(bytesOf(3));
      assertThat(store.get(bytesOf(4))).isEmpty();
    }
  }

  /**
   * Transaction commit empty.
   *
   * @throws Exception the exception
   */
  @Test
  public void transactionCommitEmpty() throws Exception {
    try (final KeyValueStorage store = createStore()) {
      final KeyValueStorageTransaction tx = store.startTransaction();
      tx.commit();
    }
  }

  /**
   * Transaction rollback empty.
   *
   * @throws Exception the exception
   */
  @Test
  public void transactionRollbackEmpty() throws Exception {
    try (final KeyValueStorage store = createStore()) {
      final KeyValueStorageTransaction tx = store.startTransaction();
      tx.rollback();
    }
  }

  /** Transaction put after commit. */
  @Test
  public void transactionPutAfterCommit() {
    Assertions.assertThatThrownBy(
            () -> {
              try (final KeyValueStorage store = createStore()) {
                final KeyValueStorageTransaction tx = store.startTransaction();
                tx.commit();
                tx.put(bytesOf(1), bytesOf(1));
              }
            })
        .isInstanceOf(IllegalStateException.class);
  }

  /** Transaction remove after commit. */
  @Test
  public void transactionRemoveAfterCommit() {
    Assertions.assertThatThrownBy(
            () -> {
              try (final KeyValueStorage store = createStore()) {
                final KeyValueStorageTransaction tx = store.startTransaction();
                tx.commit();
                tx.remove(bytesOf(1));
              }
            })
        .isInstanceOf(IllegalStateException.class);
  }

  /** Transaction put after rollback. */
  @Test
  public void transactionPutAfterRollback() {
    Assertions.assertThatThrownBy(
            () -> {
              try (final KeyValueStorage store = createStore()) {
                final KeyValueStorageTransaction tx = store.startTransaction();
                tx.rollback();
                tx.put(bytesOf(1), bytesOf(1));
              }
            })
        .isInstanceOf(IllegalStateException.class);
  }

  /** Transaction remove after rollback. */
  @Test
  public void transactionRemoveAfterRollback() {
    Assertions.assertThatThrownBy(
            () -> {
              try (final KeyValueStorage store = createStore()) {
                final KeyValueStorageTransaction tx = store.startTransaction();
                tx.rollback();
                tx.remove(bytesOf(1));
              }
            })
        .isInstanceOf(IllegalStateException.class);
  }

  /** Transaction commit after rollback. */
  @Test
  public void transactionCommitAfterRollback() {
    Assertions.assertThatThrownBy(
            () -> {
              try (final KeyValueStorage store = createStore()) {
                final KeyValueStorageTransaction tx = store.startTransaction();
                tx.rollback();
                tx.commit();
              }
            })
        .isInstanceOf(IllegalStateException.class);
  }

  /** Transaction commit twice. */
  @Test
  public void transactionCommitTwice() {
    Assertions.assertThatThrownBy(
            () -> {
              try (final KeyValueStorage store = createStore()) {
                final KeyValueStorageTransaction tx = store.startTransaction();
                tx.commit();
                tx.commit();
              }
            })
        .isInstanceOf(IllegalStateException.class);
  }

  /** Transaction rollback after commit. */
  @Test
  public void transactionRollbackAfterCommit() {
    Assertions.assertThatThrownBy(
            () -> {
              try (final KeyValueStorage store = createStore()) {
                final KeyValueStorageTransaction tx = store.startTransaction();
                tx.commit();
                tx.rollback();
              }
            })
        .isInstanceOf(IllegalStateException.class);
  }

  /** Transaction rollback twice. */
  @Test
  public void transactionRollbackTwice() {
    Assertions.assertThatThrownBy(
            () -> {
              try (final KeyValueStorage store = createStore()) {
                final KeyValueStorageTransaction tx = store.startTransaction();
                tx.rollback();
                tx.rollback();
              }
            })
        .isInstanceOf(IllegalStateException.class);
  }

  /**
   * Two transactions.
   *
   * @throws Exception the exception
   */
  @Test
  public void twoTransactions() throws Exception {
    try (final KeyValueStorage store = createStore()) {

      final KeyValueStorageTransaction tx1 = store.startTransaction();
      final KeyValueStorageTransaction tx2 = store.startTransaction();

      tx1.put(bytesOf(1), bytesOf(1));
      tx2.put(bytesOf(2), bytesOf(2));

      tx1.commit();
      tx2.commit();

      assertThat(store.get(bytesOf(1))).contains(bytesOf(1));
      assertThat(store.get(bytesOf(2))).contains(bytesOf(2));
    }
  }

  /**
   * Transaction isolation.
   *
   * @throws Exception the exception
   */
  @Test
  public void transactionIsolation() throws Exception {
    final int keyCount = 1000;
    final KeyValueStorage store = createStore();

    final CountDownLatch finishedLatch = new CountDownLatch(2);
    final Function<byte[], Thread> txRunner =
        (value) ->
            new Thread(
                () -> {
                  final KeyValueStorageTransaction tx = store.startTransaction();
                  for (int i = 0; i < keyCount; i++) {
                    tx.put(Bytes.minimalBytes(i).toArrayUnsafe(), value);
                  }
                  try {
                    tx.commit();
                  } finally {
                    finishedLatch.countDown();
                  }
                });

    // Run 2 concurrent transactions that write a bunch of values to the same keys
    final byte[] a = bytesOf(10);
    final byte[] b = bytesOf(20);
    txRunner.apply(a).start();
    txRunner.apply(b).start();

    finishedLatch.await();

    // Check that transaction results are isolated (not interleaved)
    final List<byte[]> finalValues = new ArrayList<>(keyCount);
    for (int i = 0; i < keyCount; i++) {
      final byte[] key = Bytes.minimalBytes(i).toArrayUnsafe();
      finalValues.add(store.get(key).get());
    }

    // Expecting the same value for all entries
    final byte[] expected = finalValues.get(0);
    for (final byte[] actual : finalValues) {
      assertThat(actual).containsExactly(expected);
    }

    assertThat(Arrays.equals(expected, a) || Arrays.equals(expected, b)).isTrue();

    store.close();
  }

  /**
   * Bytes from hex string byte [ ].
   *
   * @param hex the hex
   * @return the byte [ ]
   */
  /*
   * Used to mimic the wrapping with Bytes performed in Besu
   */
  protected byte[] bytesFromHexString(final String hex) {
    return Bytes.fromHexString(hex).toArrayUnsafe();
  }

  /**
   * Bytes of byte [ ].
   *
   * @param bytes the bytes
   * @return the byte [ ]
   */
  protected byte[] bytesOf(final int... bytes) {
    return Bytes.of(bytes).toArrayUnsafe();
  }

  /**
   * Create a sub folder from the given path, that will not conflict with other folders.
   *
   * @param folder the folder in which to create the sub folder
   * @return the path representing the sub folder
   * @throws Exception if the folder cannot be created
   */
  protected Path getTempSubFolder(final Path folder) throws Exception {
    return java.nio.file.Files.createTempDirectory(folder, null);
  }
}
