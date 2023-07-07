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

  public KeyValueStorageTransaction includeInGlobalTransactionStorage(
      final SegmentIdentifier segmentIdentifier, final KeyValueStorage keyValueStorage) {
    final KeyValueStorageTransaction transaction = keyValueStorage.startTransaction(this);
    this.keyValueStorageTransactions.put(segmentIdentifier, transaction);
    return transaction;
  }

  public void put(final SegmentIdentifier segmentIdentifier, final byte[] key, final byte[] value) {
    keyValueStorageTransactions.get(segmentIdentifier).put(key, value);
  }

  public void remove(final SegmentIdentifier segmentIdentifier, final byte[] key) {
    keyValueStorageTransactions.get(segmentIdentifier).remove(key);
  }

  public KeyValueStorageTransaction getKeyValueStorageTransaction(
      final SegmentIdentifier segmentIdentifier) {
    return keyValueStorageTransactions.get(segmentIdentifier);
  }

  public void commit() {
    if (isGlobalTransaction()) {
      commitGlobalTransaction();
    } else {
      keyValueStorageTransactions.values().forEach(KeyValueStorageTransaction::commit);
    }
  }

  public void rollback() {
    if (isGlobalTransaction()) {
      rollbackGlobalTransaction();
    } else {
      keyValueStorageTransactions.values().forEach(KeyValueStorageTransaction::rollback);
    }
  }

  private boolean isGlobalTransaction() {
    return getGlobalTransaction().isPresent();
  }

  public abstract Optional<S> getGlobalTransaction();

  protected abstract void commitGlobalTransaction();

  protected abstract void rollbackGlobalTransaction();

  public static GlobalKeyValueStorageTransaction<Object> NON_GLOBAL_TRANSACTION_FIELD =
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
