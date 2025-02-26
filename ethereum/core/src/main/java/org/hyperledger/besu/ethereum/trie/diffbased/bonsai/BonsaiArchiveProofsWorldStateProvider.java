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
import static org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage.ARCHIVE_PROOF_BLOCK_NUMBER_KEY;
import static org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY;
import static org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage.WORLD_BLOCK_NUMBER_KEY;
import static org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;

import org.hyperledger.besu.datatypes.Address;
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
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.DiffBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;
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
      final Long trieNodeCheckpointInterval) {
    super(
        worldStateKeyValueStorage,
        blockchain,
        maxLayersToLoad,
        bonsaiCachedMerkleTrieLoader,
        pluginContext,
        evmConfiguration,
        worldStateHealerSupplier);
    this.trieNodeCheckpointInterval = trieNodeCheckpointInterval;
  }

  @VisibleForTesting
  BonsaiArchiveProofsWorldStateProvider(
      final BonsaiCachedWorldStorageManager bonsaiCachedWorldStorageManager,
      final TrieLogManager trieLogManager,
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Blockchain blockchain,
      final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader,
      final EvmConfiguration evmConfiguration,
      final Supplier<WorldStateHealer> worldStateHealerSupplier,
      final Long trieNodeCheckpointInterval) {
    super(
        bonsaiCachedWorldStorageManager,
        trieLogManager,
        worldStateKeyValueStorage,
        blockchain,
        bonsaiCachedMerkleTrieLoader,
        evmConfiguration,
        worldStateHealerSupplier);
    this.trieNodeCheckpointInterval = trieNodeCheckpointInterval;
  }

  private Optional<BlockHeader> getCheckpointStateStartBlock(
      final Blockchain blockchain, final Hash targetHash) {
    long nearestCheckpointBlock =
        (((blockchain.getBlockHeader(targetHash).get().getNumber() + trieNodeCheckpointInterval)
                    / trieNodeCheckpointInterval)
                * trieNodeCheckpointInterval)
            - 1;

    Hash chainHeadHash = blockchain.getChainHeadHash();

    Optional<BlockHeader> block =
        blockchain
            .getBlockHeaderSafe(nearestCheckpointBlock)
            .or(() -> blockchain.getBlockHeader(chainHeadHash))
            .or(() -> blockchain.getBlockHeaderSafe(chainHeadHash));
    return block;
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
                    rollMutableArchiveStateToBlockHash(
                        worldState, checkpointBlock.get(), queryParams.getBlockHeader().getHash()))
            .map(MutableWorldState::freezeStorage);
      }
      return Optional.empty();
    }
  }

  // Archive state-proof specific rollback behaviour. Choose a world state (it can be the current
  // chain head if the exact block isn't in the cache). Then roll back the trie-logs to create
  // the intermediate state trie nodes from the checkpoint state trie nodes
  protected Optional<MutableWorldState> rollMutableArchiveStateToBlockHash(
      final DiffBasedWorldState mutableState,
      final BlockHeader checkpointBlock,
      final Hash blockHash) {

    final Hash rollFromBlockHash = checkpointBlock.getBlockHash();

    try {
      mutableState.resetWorldStateTo(checkpointBlock);
      SegmentedKeyValueStorageTransaction tx =
          mutableState.getWorldStateStorage().getComposedWorldStateStorage().startTransaction();
      tx.put(
          TRIE_BRANCH_STORAGE,
          WORLD_BLOCK_NUMBER_KEY,
          Bytes.ofUnsignedLong(checkpointBlock.getNumber()).toArrayUnsafe());
      tx.put(TRIE_BRANCH_STORAGE, WORLD_BLOCK_HASH_KEY, rollFromBlockHash.toArrayUnsafe());
      tx.put(
          TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, checkpointBlock.getStateRoot().toArrayUnsafe());
      tx.commit();

      rollArchiveProofWorldStateToBlockHash(mutableState, blockHash);

      LOG.debug(
          "Archive rolling finished, {} now at {}",
          mutableState.getWorldStateStorage().getClass().getSimpleName(),
          blockHash);
      return Optional.of(mutableState);
    } catch (final MerkleTrieException re) {
      // need to throw to trigger the heal
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

  private Optional<MutableWorldState> rollArchiveProofWorldStateToBlockHash(
      final DiffBasedWorldState mutableState, final Hash blockHash) {

    if (blockHash.equals(mutableState.blockHash())) {
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
                  .getBlockHeader(blockHash)
                  .or(() -> blockchain.getBlockHeaderSafe(blockHash))
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
        final DiffBasedWorldStateUpdateAccumulator<?> diffBasedUpdater =
            (DiffBasedWorldStateUpdateAccumulator<?>) mutableState.updater();
        try {
          for (final TrieLog rollBack : rollBacks) {
            LOG.debug("Attempting Rollback of {}", rollBack.getBlockHash());
            diffBasedUpdater.rollBack(rollBack);
          }
          diffBasedUpdater.commit();

          LOG.info(
              "Forcing archive state trie context to {}",
              blockchain.getBlockHeader(blockHash).get().getNumber());
          SegmentedKeyValueStorageTransaction tx =
              mutableState.getWorldStateStorage().getComposedWorldStateStorage().startTransaction();
          tx.put(
              TRIE_BRANCH_STORAGE,
              ARCHIVE_PROOF_BLOCK_NUMBER_KEY,
              Bytes.ofUnsignedLong(blockchain.getBlockHeaderSafe(blockHash).get().getNumber())
                  .toArrayUnsafe());
          tx.commit();

          mutableState.persist(blockchain.getBlockHeaderSafe(blockHash).get());

          LOG.debug(
              "Archive rolling finished, have persisted the new world state, {} now at {}",
              mutableState.getWorldStateStorage().getClass().getSimpleName(),
              blockHash);

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
              .addArgument(blockHash)
              .addArgument(e)
              .log();

          return Optional.empty();
        }
      } catch (final RuntimeException re) {
        LOG.info("Archive rolling failed for block hash " + blockHash, re);
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
