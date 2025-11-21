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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;
import static org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage.ARCHIVE_PROOF_BLOCK_NUMBER_KEY;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.BonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.CodeCache;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiArchiveWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.provider.WorldStateQueryParams;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.WorldStateConfig;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BonsaiArchiveProofsWorldStateProvider extends BonsaiWorldStateProvider {

  private static final Logger LOG =
      LoggerFactory.getLogger(BonsaiArchiveProofsWorldStateProvider.class);

  private final Long trieNodeCheckpointInterval;

  public BonsaiArchiveProofsWorldStateProvider(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Blockchain blockchain,
      final Optional<Long> maxLayersToLoad,
      final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader,
      final ServiceManager pluginContext,
      final EvmConfiguration evmConfiguration,
      final Supplier<WorldStateHealer> worldStateHealerSupplier,
      final Long trieNodeCheckpointInterval,
      final CodeCache codeCache) {
    super(
        worldStateKeyValueStorage,
        blockchain,
        maxLayersToLoad,
        bonsaiCachedMerkleTrieLoader,
        pluginContext,
        evmConfiguration,
        worldStateHealerSupplier,
        codeCache);
    this.trieNodeCheckpointInterval = trieNodeCheckpointInterval;
  }

  @Override
  public PathBasedWorldState createWorldState(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage) {
    return new BonsaiArchiveWorldState(
        this, worldStateKeyValueStorage, evmConfiguration, worldStateConfig, codeCache);
  }

  @Override
  public PathBasedWorldState createWorldState(
      final BonsaiWorldStateProvider archive,
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final EvmConfiguration evmConfiguration,
      final WorldStateConfig worldStateConfig,
      final CodeCache codeCache) {
    return new BonsaiArchiveWorldState(
        archive, worldStateKeyValueStorage, evmConfiguration, worldStateConfig, codeCache);
  }

  private Optional<BlockHeader> getCheckpointStateStartBlock(
      final Blockchain blockchain, final Hash targetHash) {
    Optional<Long> targetHashBlockNumber =
        blockchain.getBlockHeader(targetHash).map(BlockHeader::getNumber);

    if (targetHashBlockNumber.isPresent()) {
      long nearestCheckpointBlockNumber =
          ((((targetHashBlockNumber.get() + trieNodeCheckpointInterval)
                      / trieNodeCheckpointInterval)
                  * trieNodeCheckpointInterval)
              - 1);

      return blockchain
          .getBlockHeaderSafe(nearestCheckpointBlockNumber)
          .or(() -> blockchain.getBlockHeader(blockchain.getChainHeadHash()))
          .or(() -> blockchain.getBlockHeaderSafe(blockchain.getChainHeadHash()));
    }
    return Optional.empty();
  }

  @Override
  public Optional<MutableWorldState> getWorldState(final WorldStateQueryParams queryParams) {
    if (queryParams.shouldWorldStateUpdateHead()) {
      return getFullWorldState(queryParams);
    } else {

      final Optional<BlockHeader> checkpointBlock =
          getCheckpointStateStartBlock(blockchain, queryParams.getBlockHeader().getHash());

      if (checkpointBlock.isPresent()) {
        return cachedWorldStorageManager
            .getWorldState(checkpointBlock.get().getHash())
            .or(() -> cachedWorldStorageManager.getHeadWorldState(blockchain::getBlockHeaderSafe))
            .flatMap(
                worldState ->
                    rollArchiveProofWorldStateToBlockHash(
                        worldState, checkpointBlock.get(), queryParams.getBlockHeader().getHash()))
            .map(MutableWorldState::freezeStorage);
      }
      return Optional.empty();
    }
  }

  private Optional<MutableWorldState> rollArchiveProofWorldStateToBlockHash(
      final PathBasedWorldState mutableState,
      final BlockHeader checkpointBlock,
      final Hash targetBlockHash) {

    // Reset the current world state to the desired checkpoint block number by setting its metadata
    // to the checkpoint block number & hash. We know that the archive world state is complete at
    // every checkpoint block, so to use the state for a given checkpoint block we simply need to
    // assert which block it represents.

    // E.g. chain head = 230, target = 120, checkpoint blocks every 100 blocks. Since archive state
    // at chainhead includes everything we need recreate state at any block we simply assert that
    // the current world state is for block 200 (e.g. the nearest checkpoint). Rolling back will
    // then start from 200 and replay trie logs backwards to 120. We will then PUT new trie nodes
    // as if we were at block 120, and after those puts (during mutablestate.persist()) the
    // WORLD_BLOCK_NUMBER_KEY, WORLD_BLOCK_HASH_KEY, and WORLD_ROOT_HASH_KEY will be set to 120.
    ((BonsaiArchiveWorldState) mutableState).resetWorldStateToCheckpoint(checkpointBlock);

    // Roll back from the checkpoint state to the target block hash
    if (targetBlockHash.equals(mutableState.blockHash())) {
      return Optional.of(mutableState);
    } else {
      try {
        final Optional<BlockHeader> maybePersistedHeader =
            blockchain
                .getBlockHeader(mutableState.blockHash())
                .or(() -> blockchain.getBlockHeaderSafe(mutableState.blockHash()))
                .map(BlockHeader.class::cast);

        final List<TrieLog> rollBacks = new ArrayList<>();
        if (maybePersistedHeader.isEmpty()) {
          trieLogManager.getTrieLogLayer(mutableState.blockHash()).ifPresent(rollBacks::add);
        } else {
          BlockHeader targetHeader =
              blockchain
                  .getBlockHeader(targetBlockHash)
                  .or(() -> blockchain.getBlockHeaderSafe(targetBlockHash))
                  .get();
          BlockHeader persistedHeader = maybePersistedHeader.get();

          // roll back from persisted to even with target
          Hash persistedBlockHash = persistedHeader.getBlockHash();
          while (persistedHeader.getNumber() > targetHeader.getNumber()) {
            LOG.debug("Rollback {}", persistedBlockHash);
            rollBacks.add(trieLogManager.getTrieLogLayer(persistedBlockHash).get());
            persistedHeader = blockchain.getBlockHeaderSafe(persistedHeader.getParentHash()).get();
            persistedBlockHash = persistedHeader.getBlockHash();
          }
        }

        // attempt the state rolling
        final PathBasedWorldStateUpdateAccumulator<?> diffBasedUpdater =
            (PathBasedWorldStateUpdateAccumulator<?>) mutableState.updater();
        try {
          for (final TrieLog rollBack : rollBacks) {
            LOG.info("Attempting Rollback of {}", rollBack.getBlockHash());
            diffBasedUpdater.rollBack(rollBack);
          }
          diffBasedUpdater.commit();

          // We now have an updater with all of the account changes necessary. Persisting the
          // mutable state will store the new trie nodes defined in the updater. We can't yet assert
          // that the mutable world state is at the target block because persisting involves reading
          // the current trie nodes to validate the root hash. WORLD_BLOCK_NUMBER_KEY is therefore
          // still set to the origin block. We use an additional key ARCHIVE_PROOF_BLOCK_NUMBER_KEY
          // which persist() will use for PUTs to the state trie.

          // This will finish creating usable world state by setting the WORLD_BLOCK_NUMBER_KEY,
          // WORLD_BLOCK_HASH_KEY and WORLD_ROOT_HASH_KEY to the target block.
          LOG.info(
              "Forcing archive state trie context to {}",
              blockchain.getBlockHeader(targetBlockHash).get().getNumber());
          SegmentedKeyValueStorageTransaction tx =
              mutableState.getWorldStateStorage().getComposedWorldStateStorage().startTransaction();
          tx.put(
              TRIE_BRANCH_STORAGE,
              ARCHIVE_PROOF_BLOCK_NUMBER_KEY,
              Bytes.ofUnsignedLong(blockchain.getBlockHeaderSafe(targetBlockHash).get().getNumber())
                  .toArrayUnsafe());
          tx.commit();

          mutableState.persist(blockchain.getBlockHeaderSafe(targetBlockHash).get());

          LOG.debug(
              "Archive rolling finished, have persisted the new world state, {} now at {}",
              mutableState.getWorldStateStorage().getClass().getSimpleName(),
              targetBlockHash);

          return Optional.of(mutableState);
        } catch (final MerkleTrieException re) {
          // need to throw to trigger the heal
          throw re;
        } catch (final Exception e) {
          // if we fail we must clean up the updater
          diffBasedUpdater.reset();
          LOG.atInfo()
              .setMessage("State rolling failed on {} for block hash {}")
              .addArgument(mutableState.getWorldStateStorage().getClass().getSimpleName())
              .addArgument(targetBlockHash)
              .addArgument(e)
              .log();

          return Optional.empty();
        }
      } catch (final RuntimeException re) {
        LOG.info("Archive rolling failed for block hash " + targetBlockHash, re);
        if (re instanceof MerkleTrieException) {
          // need to throw to trigger the heal
          throw re;
        }
        throw new MerkleTrieException(
            "invalid", Optional.of(Address.ZERO), Hash.EMPTY, Bytes.EMPTY);
      }
    }
  }
}
