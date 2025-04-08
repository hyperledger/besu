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
package org.hyperledger.besu.ethereum.trie.pathbased.verkle.cache;

import org.hyperledger.besu.ethereum.trie.pathbased.common.cache.PathBasedCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.pathbased.common.provider.PathBasedWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.WorldStateConfig;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.VerkleWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.storage.VerkleLayeredWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.storage.VerkleSnapshotWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.storage.VerkleWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.worldview.VerkleWorldState;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

public class VerkleCachedWorldStorageManager extends PathBasedCachedWorldStorageManager {

  public VerkleCachedWorldStorageManager(
      final VerkleWorldStateProvider archive,
      final PathBasedWorldStateKeyValueStorage worldStateKeyValueStorage,
      final WorldStateConfig worldStateConfig) {
    super(archive, worldStateKeyValueStorage, worldStateConfig);
  }

  @Override
  public PathBasedWorldState createWorldState(
      final PathBasedWorldStateProvider archive,
      final PathBasedWorldStateKeyValueStorage worldStateKeyValueStorage,
      final EvmConfiguration evmConfiguration) {
    return new VerkleWorldState(
        (VerkleWorldStateProvider) archive,
        (VerkleWorldStateKeyValueStorage) worldStateKeyValueStorage,
        evmConfiguration,
        WorldStateConfig.newBuilder(worldStateConfig).build());
  }

  @Override
  public PathBasedWorldStateKeyValueStorage createLayeredKeyValueStorage(
      final PathBasedWorldStateKeyValueStorage worldStateKeyValueStorage) {
    return new VerkleLayeredWorldStateKeyValueStorage(
        (VerkleWorldStateKeyValueStorage) worldStateKeyValueStorage);
  }

  @Override
  public PathBasedWorldStateKeyValueStorage createSnapshotKeyValueStorage(
      final PathBasedWorldStateKeyValueStorage worldStateKeyValueStorage) {
    return new VerkleSnapshotWorldStateKeyValueStorage(
        (VerkleWorldStateKeyValueStorage) worldStateKeyValueStorage);
  }
}
