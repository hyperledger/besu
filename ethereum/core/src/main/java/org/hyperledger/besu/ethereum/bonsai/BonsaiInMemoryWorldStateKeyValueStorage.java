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
package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BonsaiInMemoryWorldStateKeyValueStorage extends BonsaiWorldStateKeyValueStorage
    implements WorldStateStorage {

  private static final Logger LOG =
      LoggerFactory.getLogger(BonsaiInMemoryWorldStateKeyValueStorage.class);

  public BonsaiInMemoryWorldStateKeyValueStorage(
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage,
      final KeyValueStorage trieBranchStorage,
      final KeyValueStorage trieLogStorage) {
    super(accountStorage, codeStorage, storageStorage, trieBranchStorage, trieLogStorage);
  }

  @Override
  public InMemoryUpdater updater() {
    return new InMemoryUpdater(
        accountStorage.startTransaction(),
        codeStorage.startTransaction(),
        storageStorage.startTransaction(),
        trieBranchStorage.startTransaction(),
        trieLogStorage.startTransaction());
  }

  public static class InMemoryUpdater extends BonsaiWorldStateKeyValueStorage.Updater
      implements WorldStateStorage.Updater {

    public InMemoryUpdater(
        final KeyValueStorageTransaction accountStorageTransaction,
        final KeyValueStorageTransaction codeStorageTransaction,
        final KeyValueStorageTransaction storageStorageTransaction,
        final KeyValueStorageTransaction trieBranchStorageTransaction,
        final KeyValueStorageTransaction trieLogStorageTransaction) {
      super(
          accountStorageTransaction,
          codeStorageTransaction,
          storageStorageTransaction,
          trieBranchStorageTransaction,
          trieLogStorageTransaction);
    }

    @Override
    public void commit() {
      LOG.trace("Cannot commit using an in memory key value storage");
    }
  }
}
