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
package org.hyperledger.besu.ethereum.trie.diffbased.bonsai;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;
import static org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY;
import static org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage.WORLD_BLOCK_NUMBER_KEY;
import static org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache.BonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache.BonsaiCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.provider.WorldStateQueryParams;
import org.hyperledger.besu.ethereum.trie.diffbased.common.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BonsaiArchiveProofsWorldStateProvider extends BonsaiWorldStateProvider {

  private static final Logger LOG =
      LoggerFactory.getLogger(BonsaiArchiveProofsWorldStateProvider.class);

  public BonsaiArchiveProofsWorldStateProvider(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Blockchain blockchain,
      final Optional<Long> maxLayersToLoad,
      final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader,
      final ServiceManager pluginContext,
      final EvmConfiguration evmConfiguration,
      final Supplier<WorldStateHealer> worldStateHealerSupplier) {
    super(
        worldStateKeyValueStorage,
        blockchain,
        maxLayersToLoad,
        bonsaiCachedMerkleTrieLoader,
        pluginContext,
        evmConfiguration,
        worldStateHealerSupplier);
  }

  @VisibleForTesting
  BonsaiArchiveProofsWorldStateProvider(
      final BonsaiCachedWorldStorageManager bonsaiCachedWorldStorageManager,
      final TrieLogManager trieLogManager,
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Blockchain blockchain,
      final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader,
      final EvmConfiguration evmConfiguration,
      final Supplier<WorldStateHealer> worldStateHealerSupplier) {
    super(
        bonsaiCachedWorldStorageManager,
        trieLogManager,
        worldStateKeyValueStorage,
        blockchain,
        bonsaiCachedMerkleTrieLoader,
        evmConfiguration,
        worldStateHealerSupplier);
  }

  @Override
  public Optional<MutableWorldState> getWorldState(final WorldStateQueryParams queryParams) {
    if (queryParams.shouldWorldStateUpdateHead()) {
      return getFullWorldState(queryParams);
    } else {
      final BlockHeader chainHeadBlockHeader = blockchain.getChainHeadHeader();
      if (queryParams.getBlockHeader().getNumber() < 80) {
        System.out.println(
            "Rolling archive proof state back to "
                + queryParams.getBlockHeader().getNumber()
                + ". Taking the current full world state, setting the archive context to "
                + queryParams.getBlockHeader().getNumber()
                + ", and then returning that to use. We'll still need to roll trie logs from there, but this jumps us to the nearest trie log context I think.");
      }
      Optional<MutableWorldState> cachedWorldState =
          cachedWorldStorageManager
              .getWorldState(chainHeadBlockHeader.getHash())
              .flatMap(
                  worldState ->
                      rollMutableArchiveStateToBlockHash( // This is a tiny action for archive
                          // state
                          worldState, queryParams.getBlockHeader().getHash()))
              .map(MutableWorldState::freezeStorage);
      return cachedWorldState;
    }
  }

  // Archive-specific rollback behaviour. There is no trie-log roll forward/backward, we just roll
  // back the state root, block hash and block number
  protected Optional<MutableWorldState> rollMutableArchiveStateToBlockHash(
      final DiffBasedWorldState mutableState, final Hash blockHash) {
    LOG.info("Rolling mutable archive world state to block hash " + blockHash.toHexString());
    try {
      mutableState.resetWorldStateTo(
          blockchain
              .getBlockHeader(blockHash)
              .get()); // MRW TODO - check ramifications on cached layers of this
      SegmentedKeyValueStorageTransaction tx =
          mutableState.getWorldStateStorage().getComposedWorldStateStorage().startTransaction();
      tx.put(
          TRIE_BRANCH_STORAGE,
          WORLD_BLOCK_NUMBER_KEY,
          Bytes.ofUnsignedLong(blockchain.getBlockHeader(blockHash).get().getNumber())
              .toArrayUnsafe());
      tx.put(TRIE_BRANCH_STORAGE, WORLD_BLOCK_HASH_KEY, blockHash.toArrayUnsafe());
      LOG.info(
          "During rollback, setting world state root hash to "
              + blockchain.getBlockHeader(blockHash).get().getStateRoot().toHexString());
      tx.put(
          TRIE_BRANCH_STORAGE,
          WORLD_ROOT_HASH_KEY,
          blockchain.getBlockHeader(blockHash).get().getStateRoot().toArrayUnsafe());
      tx.commit();
      // Simply persist the block hash/number and state root for this archive state
      mutableState.persist(blockchain.getBlockHeader(blockHash).get());

      LOG.info(
          "Archive rolling finished, {} now at {}",
          mutableState.getWorldStateStorage().getClass().getSimpleName(),
          blockHash);
      return Optional.of(mutableState);
    } catch (final MerkleTrieException re) {
      // need to throw to trigger the heal
      System.out.println("Merkle trie exception: " + re);
      re.printStackTrace();
      throw re;
    } catch (final Exception e) {
      LOG.atInfo()
          .setMessage("State rolling failed on {} for block hash {}: {}")
          .addArgument(mutableState.getWorldStateStorage().getClass().getSimpleName())
          .addArgument(blockHash)
          .addArgument(e)
          .log();

      return Optional.empty();
    }
  }
}
