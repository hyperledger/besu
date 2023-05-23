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
package org.hyperledger.besu.ethereum.bonsai.storage;

import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber;
import org.hyperledger.besu.ethereum.bonsai.storage.flat.FlatDbMode;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;
import org.hyperledger.besu.services.kvstore.LayeredKeyValueStorage;

public class BonsaiWorldStateLayerStorage extends BonsaiSnapshotWorldStateKeyValueStorage
    implements BonsaiStorageSubscriber {

  public BonsaiWorldStateLayerStorage(final BonsaiWorldStateKeyValueStorage parent) {
    this(
        new LayeredKeyValueStorage(parent.accountStorage),
        new LayeredKeyValueStorage(parent.codeStorage),
        new LayeredKeyValueStorage(parent.storageStorage),
        new LayeredKeyValueStorage(parent.trieBranchStorage),
        parent.trieLogStorage,
        parent,
        parent.metricsSystem);
  }

  public BonsaiWorldStateLayerStorage(
      final SnappedKeyValueStorage accountStorage,
      final SnappedKeyValueStorage codeStorage,
      final SnappedKeyValueStorage storageStorage,
      final SnappedKeyValueStorage trieBranchStorage,
      final KeyValueStorage trieLogStorage,
      final BonsaiWorldStateKeyValueStorage parent,
      final ObservableMetricsSystem metricsSystem) {
    super(
        parent,
        accountStorage,
        codeStorage,
        storageStorage,
        trieBranchStorage,
        trieLogStorage,
        metricsSystem);
  }

  @Override
  public FlatDbMode getFlatDbMode() {
    return parentWorldStateStorage.getFlatDbMode();
  }

  @Override
  public BonsaiWorldStateLayerStorage clone() {
    return new BonsaiWorldStateLayerStorage(
        ((LayeredKeyValueStorage) accountStorage).clone(),
        ((LayeredKeyValueStorage) codeStorage).clone(),
        ((LayeredKeyValueStorage) storageStorage).clone(),
        ((LayeredKeyValueStorage) trieBranchStorage).clone(),
        trieLogStorage,
        parentWorldStateStorage,
        metricsSystem);
  }
}
