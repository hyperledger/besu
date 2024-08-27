/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.trie.diffbased.transition.storage;

import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

public class VerkleTransitionWorldStateKeyValueStorage
    implements WorldStateKeyValueStorage, AutoCloseable {

  final BonsaiWorldStateKeyValueStorage bonsaiKeyValueStorage;
  final VerkleWorldStateKeyValueStorage verkleKeyValueStorage;

  public VerkleTransitionWorldStateKeyValueStorage(
      final StorageProvider provider,
      final MetricsSystem metricsSystem,
      final DataStorageConfiguration dataStorageConfiguration) {
    this.bonsaiKeyValueStorage =
        new BonsaiWorldStateKeyValueStorage(provider, metricsSystem, dataStorageConfiguration);
    this.verkleKeyValueStorage = new VerkleWorldStateKeyValueStorage(provider, metricsSystem);
  }

  @Override
  public void close() throws Exception {
    // TODO: it might be safer to close the storage directly
    //  rather than deferring to both, since they share segments
    bonsaiKeyValueStorage.close();
    verkleKeyValueStorage.close();
  }

  @Override
  public DataStorageFormat getDataStorageFormat() {
    return DataStorageFormat.VERKLE_TRANSITION;
  }

  @Override
  public Updater updater() {
    // if we are in transition, we should only use the verkle updater, merkle trie should be frozen
    return verkleKeyValueStorage.updater();
  }

  @Override
  public void clear() {
    bonsaiKeyValueStorage.clear();
    verkleKeyValueStorage.clear();
  }
}
