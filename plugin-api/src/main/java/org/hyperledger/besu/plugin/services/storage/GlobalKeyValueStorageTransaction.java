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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class GlobalKeyValueStorageTransaction<S> {

  private final List<KeyValueStorageTransaction> keyValueStorageTransactions;

  public GlobalKeyValueStorageTransaction() {
    this.keyValueStorageTransactions = new ArrayList<>();
  }

  public KeyValueStorageTransaction includeInGlobalTransactionStorage(
      final KeyValueStorage keyValueStorage) {
    final KeyValueStorageTransaction transaction = keyValueStorage.startTransaction(this);
    this.keyValueStorageTransactions.add(transaction);
    return transaction;
  }

  public void commit() {
    getGlobalTransaction()
        .ifPresentOrElse(
            s -> commitGlobalTransaction(),
            () -> keyValueStorageTransactions.forEach(KeyValueStorageTransaction::commit));
  }

  public void rollback() {
    getGlobalTransaction()
        .ifPresentOrElse(
            s -> rollbackGlobalTransaction(),
            () -> keyValueStorageTransactions.forEach(KeyValueStorageTransaction::rollback));
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
