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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;
import static org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY;
import static org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage.WORLD_BLOCK_NUMBER_KEY;
import static org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.BonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.NoopBonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateLayerStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.cache.PathBasedCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.WorldStateConfig;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import org.apache.tuweni.bytes.Bytes;

public class BonsaiArchiveWorldState extends BonsaiWorldState {

  public BonsaiArchiveWorldState(
      final BonsaiWorldStateProvider archive,
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final EvmConfiguration evmConfiguration,
      final WorldStateConfig worldStateConfig) {
    this(
        worldStateKeyValueStorage,
        archive.getCachedMerkleTrieLoader(),
        archive.getCachedWorldStorageManager(),
        archive.getTrieLogManager(),
        evmConfiguration,
        worldStateConfig);
  }

  public BonsaiArchiveWorldState(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader,
      final PathBasedCachedWorldStorageManager cachedWorldStorageManager,
      final TrieLogManager trieLogManager,
      final EvmConfiguration evmConfiguration,
      final WorldStateConfig worldStateConfig) {
    super(
        worldStateKeyValueStorage,
        bonsaiCachedMerkleTrieLoader,
        cachedWorldStorageManager,
        trieLogManager,
        evmConfiguration,
        worldStateConfig);
  }

  private BonsaiArchiveWorldState(
      final BonsaiArchiveWorldState worldState,
      final BonsaiCachedMerkleTrieLoader cachedMerkleTrieLoader) {
    this(
        new BonsaiWorldStateLayerStorage(worldState.getWorldStateStorage()),
        cachedMerkleTrieLoader,
        worldState.cachedWorldStorageManager,
        worldState.trieLogManager,
        worldState.accumulator.getEvmConfiguration(),
        WorldStateConfig.newBuilder(worldState.worldStateConfig).build());
  }

  @Override
  public BonsaiWorldState duplicateWithNoopCachedTrieLoader() {
    return new BonsaiArchiveWorldState(this, new NoopBonsaiCachedMerkleTrieLoader());
  }

  /**
   * Takes an archive world state and asserts that it represents a given block with respect to the
   * state trie. A world state archive can be rolled back from checkpoint blocks to any block before
   * that checkpoint.
   *
   * @param checkpointBlock the checkpoint block to use as the basis for rolling back the state trie
   */
  public void resetWorldStateToCheckpoint(final BlockHeader checkpointBlock) {
    this.resetWorldStateTo(checkpointBlock);
    SegmentedKeyValueStorageTransaction tx =
        this.getWorldStateStorage().getComposedWorldStateStorage().startTransaction();
    tx.put(
        TRIE_BRANCH_STORAGE,
        WORLD_BLOCK_NUMBER_KEY,
        Bytes.ofUnsignedLong(checkpointBlock.getNumber()).toArrayUnsafe());
    tx.put(
        TRIE_BRANCH_STORAGE, WORLD_BLOCK_HASH_KEY, checkpointBlock.getBlockHash().toArrayUnsafe());
    tx.put(
        TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, checkpointBlock.getStateRoot().toArrayUnsafe());
    tx.commit();
  }
}
