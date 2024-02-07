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

package org.hyperledger.besu.ethereum.trie.diffbased.common;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.diffbased.common.cache.DiffBasedCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.DiffBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiffBasedWorldStateProvider implements WorldStateArchive {

  private static final Logger LOG = LoggerFactory.getLogger(DiffBasedWorldStateProvider.class);

  protected final Blockchain blockchain;

  protected final TrieLogManager trieLogManager;
  protected DiffBasedCachedWorldStorageManager cachedWorldStorageManager;
  protected DiffBasedWorldState persistedState;
  protected final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage;

  public DiffBasedWorldStateProvider(
      final DataStorageConfiguration dataStorageConfiguration,
      final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Blockchain blockchain,
      final Optional<Long> maxLayersToLoad,
      final BesuContext pluginContext) {

    this.worldStateKeyValueStorage = worldStateKeyValueStorage;
    // TODO: de-dup constructors
    this.trieLogManager =
        new TrieLogManager(
            blockchain,
            dataStorageConfiguration,
            worldStateKeyValueStorage,
            maxLayersToLoad.orElse(DiffBasedCachedWorldStorageManager.RETAINED_LAYERS),
            pluginContext);
    this.blockchain = blockchain;
  }

  public DiffBasedWorldStateProvider(
      final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Blockchain blockchain,
      final TrieLogManager trieLogManager) {

    this.worldStateKeyValueStorage = worldStateKeyValueStorage;
    // TODO: de-dup constructors
    this.trieLogManager = trieLogManager;
    this.blockchain = blockchain;
  }

  protected void provideCachedWorldStorageManager(
      final DiffBasedCachedWorldStorageManager cachedWorldStorageManager) {
    this.cachedWorldStorageManager = cachedWorldStorageManager;
  }

  protected void loadPersistedState(final DiffBasedWorldState persistedState) {
    this.persistedState = persistedState;
    blockchain
        .getBlockHeader(persistedState.getWorldStateBlockHash())
        .ifPresent(
            blockHeader ->
                this.cachedWorldStorageManager.addCachedLayer(
                    blockHeader, persistedState.getWorldStateRootHash(), persistedState));
  }

  @Override
  public Optional<WorldState> get(final Hash rootHash, final Hash blockHash) {
    return cachedWorldStorageManager
        .getWorldState(blockHash)
        .or(
            () -> {
              if (blockHash.equals(persistedState.blockHash())) {
                return Optional.of(persistedState);
              } else {
                return Optional.empty();
              }
            })
        .map(WorldState.class::cast);
  }

  @Override
  public boolean isWorldStateAvailable(final Hash rootHash, final Hash blockHash) {
    return cachedWorldStorageManager.containWorldStateStorage(blockHash)
        || persistedState.blockHash().equals(blockHash)
        || worldStateKeyValueStorage.isWorldStateAvailable(rootHash, blockHash);
  }

  @Override
  public Optional<MutableWorldState> getMutable(
      final BlockHeader blockHeader, final boolean shouldPersistState) {
    if (shouldPersistState) {
      return getMutable(blockHeader.getStateRoot(), blockHeader.getHash());
    } else {
      final BlockHeader chainHeadBlockHeader = blockchain.getChainHeadHeader();
      if (chainHeadBlockHeader.getNumber() - blockHeader.getNumber()
          >= trieLogManager.getMaxLayersToLoad()) {
        LOG.warn(
            "Exceeded the limit of back layers that can be loaded ({})",
            trieLogManager.getMaxLayersToLoad());
        return Optional.empty();
      }
      return cachedWorldStorageManager
          .getWorldState(blockHeader.getHash())
          .or(() -> cachedWorldStorageManager.getNearestWorldState(blockHeader))
          .or(() -> cachedWorldStorageManager.getHeadWorldState(blockchain::getBlockHeader))
          .flatMap(worldState -> rollMutableStateToBlockHash(worldState, blockHeader.getHash()))
          .map(MutableWorldState::freeze);
    }
  }

  @Override
  public synchronized Optional<MutableWorldState> getMutable(
      final Hash rootHash, final Hash blockHash) {


    /*Optional<BlockHeader> blockHeader = blockchain.getBlockHeader(blockHash);
    if(blockHeader.isPresent()){
      Optional<BlockHeader> parentHeader = blockchain.getBlockHeader(blockHeader.get().getParentHash());
      if(parentHeader.isPresent()){
        Optional<MutableWorldState> worldState = rollMutableStateToBlockHash(persistedState, parentHeader.get().getBlockHash());
        if(worldState.isEmpty()){
          System.out.println("failed rollback to "+parentHeader.get().getNumber());
          throw new RuntimeException("invalid trielog");
        }
        System.out.println("rollback to "+parentHeader.get().getNumber());
      }
    }*/
    return rollMutableStateToBlockHash(persistedState, blockHash);

  }

  Optional<MutableWorldState> rollMutableStateToBlockHash(
      final DiffBasedWorldState mutableState, final Hash blockHash) {
    if (blockHash.equals(mutableState.blockHash())) {
      return Optional.of(mutableState);
    } else {
      try {

        final Optional<BlockHeader> maybePersistedHeader =
            blockchain.getBlockHeader(mutableState.blockHash()).map(BlockHeader.class::cast);

        final List<TrieLog> rollBacks = new ArrayList<>();
        final List<TrieLog> rollForwards = new ArrayList<>();
        if (maybePersistedHeader.isEmpty()) {
          trieLogManager.getTrieLogLayer(mutableState.blockHash()).ifPresent(rollBacks::add);
        } else {
          BlockHeader targetHeader = blockchain.getBlockHeader(blockHash).get();
          BlockHeader persistedHeader = maybePersistedHeader.get();
          // roll back from persisted to even with target
          Hash persistedBlockHash = persistedHeader.getBlockHash();
          while (persistedHeader.getNumber() > targetHeader.getNumber()) {
            LOG.debug("Rollback {}", persistedBlockHash);
            rollBacks.add(trieLogManager.getTrieLogLayer(persistedBlockHash).get());
            persistedHeader = blockchain.getBlockHeader(persistedHeader.getParentHash()).get();
            persistedBlockHash = persistedHeader.getBlockHash();
          }
          // roll forward to target
          Hash targetBlockHash = targetHeader.getBlockHash();
          while (persistedHeader.getNumber() < targetHeader.getNumber()) {
            LOG.debug("Rollforward {}", targetBlockHash);
            rollForwards.add(trieLogManager.getTrieLogLayer(targetBlockHash).get());
            targetHeader = blockchain.getBlockHeader(targetHeader.getParentHash()).get();
            targetBlockHash = targetHeader.getBlockHash();
          }

          // roll back in tandem until we hit a shared state
          while (!persistedBlockHash.equals(targetBlockHash)) {
            LOG.debug("Paired Rollback {}", persistedBlockHash);
            LOG.debug("Paired Rollforward {}", targetBlockHash);
            rollForwards.add(trieLogManager.getTrieLogLayer(targetBlockHash).get());
            targetHeader = blockchain.getBlockHeader(targetHeader.getParentHash()).get();

            rollBacks.add(trieLogManager.getTrieLogLayer(persistedBlockHash).get());
            persistedHeader = blockchain.getBlockHeader(persistedHeader.getParentHash()).get();

            targetBlockHash = targetHeader.getBlockHash();
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
          for (int i = rollForwards.size() - 1; i >= 0; i--) {
            final var forward = rollForwards.get(i);
            LOG.debug("Attempting Rollforward of {}", rollForwards.get(i).getBlockHash());
            diffBasedUpdater.rollForward(forward);
          }
          diffBasedUpdater.commit();

          mutableState.persist(blockchain.getBlockHeader(blockHash).get());

          LOG.debug(
              "Archive rolling finished, {} now at {}",
              mutableState.getWorldStateStorage().getClass().getSimpleName(),
              blockHash);
          return Optional.of(mutableState);
        } catch (final MerkleTrieException re) {
          // need to throw to trigger the heal
          throw re;
        } catch (final Exception e) {
          // if we fail we must clean up the updater
          diffBasedUpdater.reset();
          LOG.debug(
              "State rolling failed on "
                  + mutableState.getWorldStateStorage().getClass().getSimpleName()
                  + " for block hash "
                  + blockHash,
              e);

          return Optional.empty();
        }
      } catch (final RuntimeException re) {
        LOG.error("Archive rolling failed for block hash " + blockHash, re);
        if (re instanceof MerkleTrieException) {
          // need to throw to trigger the heal
          throw re;
        }
        throw new MerkleTrieException(
            "invalid", Optional.of(Address.ZERO), Hash.EMPTY, Bytes.EMPTY);
      }
    }
  }

  @Override
  public MutableWorldState getMutable() {
    return persistedState;
  }

  public TrieLogManager getTrieLogManager() {
    return trieLogManager;
  }

  public DiffBasedCachedWorldStorageManager getCachedWorldStorageManager() {
    return cachedWorldStorageManager;
  }

  @Override
  public void resetArchiveStateTo(final BlockHeader blockHeader) {
    persistedState.resetWorldStateTo(blockHeader);
    this.cachedWorldStorageManager.reset();
    this.cachedWorldStorageManager.addCachedLayer(
        blockHeader, persistedState.getWorldStateRootHash(), persistedState);
  }

  @Override
  public <U> Optional<U> getAccountProof(
      final BlockHeader blockHeader,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys,
      final Function<Optional<WorldStateProof>, ? extends Optional<U>> mapper) {
    try (DiffBasedWorldState ws =
        (DiffBasedWorldState) getMutable(blockHeader, false).orElse(null)) {
      if (ws != null) {
        final WorldStateProofProvider worldStateProofProvider =
            new WorldStateProofProvider(
                new WorldStateStorageCoordinator(ws.getWorldStateStorage()));
        return mapper.apply(
            worldStateProofProvider.getAccountProof(
                ws.getWorldStateRootHash(), accountAddress, accountStorageKeys));
      }
    } catch (Exception ex) {
      LOG.error("failed proof query for " + blockHeader.getBlockHash().toShortHexString(), ex);
    }
    return Optional.empty();
  }

  @Override
  public Optional<Bytes> getNodeData(final Hash hash) {
    return Optional.empty();
  }

  @Override
  public void close() {
    try {
      worldStateKeyValueStorage.close();
    } catch (Exception e) {
      // no op
    }
  }
}
