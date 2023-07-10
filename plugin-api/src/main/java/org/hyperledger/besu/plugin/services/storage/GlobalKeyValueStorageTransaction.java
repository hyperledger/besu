/*
 * Copyright Hyperledger Besu Contributors.
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
 *
 */
package org.hyperledger.besu.plugin.services.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The GlobalKeyValueStorageTransaction class allows managing multiple columns with a single
 * transaction. In cases where we have a single transaction (Persisted), it handles committing the
 * transaction for all the columns. However, there are scenarios where we might not have a single
 * transaction (e.g., InMemory, Snapshot, Layered). This class detects the presence of a global
 * transaction and, if present, only commits the global transaction. Otherwise, it commits each
 * transaction per column.
 *
 * @param <S> transaction type
 */
public abstract class GlobalKeyValueStorageTransaction<S> {

  private final Map<SegmentIdentifier, KeyValueStorageTransaction> keyValueStorageTransactions;

  public GlobalKeyValueStorageTransaction() {
    this.keyValueStorageTransactions = new HashMap<>();
  }

  /**
   * Includes a KeyValueStorage in the global transaction storage.
   *
   * @param segmentIdentifier The identifier of the segment.
   * @param keyValueStorage The KeyValueStorage to be included.
   * @return The KeyValueStorageTransaction associated with the included storage.
   */
  public KeyValueStorageTransaction includeInGlobalTransactionStorage(
      final SegmentIdentifier segmentIdentifier, final KeyValueStorage keyValueStorage) {
    final KeyValueStorageTransaction transaction = keyValueStorage.startTransaction(this);
    this.keyValueStorageTransactions.put(segmentIdentifier, transaction);
    return transaction;
  }

  /**
   * Puts a key-value pair into the transaction identified by the segmentIdentifier.
   *
   * @param segmentIdentifier The identifier of the segment.
   * @param key The key to be stored.
   * @param value The value to be associated with the key.
   */
  public void put(final SegmentIdentifier segmentIdentifier, final byte[] key, final byte[] value) {
    keyValueStorageTransactions.get(segmentIdentifier).put(key, value);
  }

  /**
   * Removes a key from the transaction identified by the segmentIdentifier.
   *
   * @param segmentIdentifier The identifier of the segment.
   * @param key The key to be removed.
   */
  public void remove(final SegmentIdentifier segmentIdentifier, final byte[] key) {
    keyValueStorageTransactions.get(segmentIdentifier).remove(key);
  }

  /**
   * Retrieves the KeyValueStorageTransaction associated with the provided segmentIdentifier.
   *
   * @param segmentIdentifier The identifier of the segment.
   * @return The KeyValueStorageTransaction associated with the segmentIdentifier.
   */
  public KeyValueStorageTransaction getKeyValueStorageTransaction(
      final SegmentIdentifier segmentIdentifier) {
    return keyValueStorageTransactions.get(segmentIdentifier);
  }

  /**
   * Commits the global transaction or commits each individual KeyValueStorageTransaction if it is
   * not a global transaction.
   */
  public void commit() {
    if (isGlobalTransaction()) {
      commitGlobalTransaction();
    } else {
      keyValueStorageTransactions.values().forEach(KeyValueStorageTransaction::commit);
    }
  }

  /**
   * Rolls back the global transaction or rolls back each individual KeyValueStorageTransaction if
   * it is not a global transaction.
   */
  public void rollback() {
    if (isGlobalTransaction()) {
      rollbackGlobalTransaction();
    } else {
      keyValueStorageTransactions.values().forEach(KeyValueStorageTransaction::rollback);
    }
  }

  /**
   * Checks if the current transaction is a global transaction.
   *
   * @return true if a global transaction is present, false otherwise.
   */
  private boolean isGlobalTransaction() {
    return getGlobalTransaction().isPresent();
  }

  /**
   * Returns the global transaction.
   *
   * @return An Optional object representing the global transaction.
   */
  public abstract Optional<S> getGlobalTransaction();

  /**
   * Commits the global transaction. This method should be implemented by subclasses to define the
   * specific logic for committing the global transaction.
   */
  protected abstract void commitGlobalTransaction();

  /**
   * Rolls back the global transaction. This method should be implemented by subclasses to define
   * the specific logic for rolling back the global transaction.
   */
  protected abstract void rollbackGlobalTransaction();

  /**
   * Supplier that returns a disabled global transaction object for a global key-value storage. The
   * returned transaction object does not support global transactions and throws
   * UnsupportedOperationException when attempting to commit or rollback. This supplier is generally
   * used for non-persistent cases that do not require the global transaction mechanism (in memory
   * etc).
   */
  public static Supplier<GlobalKeyValueStorageTransaction<Object>>
      DISABLED_GLOBAL_TRANSACTION_SUPPLIER =
          () ->
              new GlobalKeyValueStorageTransaction<>() {
                @Override
                public Optional<Object> getGlobalTransaction() {
                  return Optional.empty();
                }

                @Override
                protected void commitGlobalTransaction() {
                  throw new UnsupportedOperationException("no global transaction for storage type");
                }

                @Override
                protected void rollbackGlobalTransaction() {
                  throw new UnsupportedOperationException("no global transaction for storage type");
                }
              };
}
