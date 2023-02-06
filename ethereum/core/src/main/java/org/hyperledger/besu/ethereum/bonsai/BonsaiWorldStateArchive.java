/*
 * Copyright Hyperledger Besu contributors.
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

package org.hyperledger.besu.ethereum.bonsai;

import static org.hyperledger.besu.datatypes.Hash.fromPlugin;
import static org.hyperledger.besu.ethereum.bonsai.LayeredTrieLogManager.RETAINED_LAYERS;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.SnapshotMutableWorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BonsaiWorldStateArchive implements WorldStateArchive {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiWorldStateArchive.class);

  private final Blockchain blockchain;

  private final TrieLogManager trieLogManager;
  private final BonsaiPersistedWorldState persistedState;
  private final BonsaiWorldStateKeyValueStorage worldStateStorage;

  private final CachedMerkleTrieLoader cachedMerkleTrieLoader;

  private final boolean useSnapshots;

  public BonsaiWorldStateArchive(
      final StorageProvider provider,
      final Blockchain blockchain,
      final CachedMerkleTrieLoader cachedMerkleTrieLoader) {
    this(
        (BonsaiWorldStateKeyValueStorage)
            provider.createWorldStateStorage(DataStorageFormat.BONSAI),
        blockchain,
        Optional.empty(),
        provider.isWorldStateSnappable(),
        cachedMerkleTrieLoader);
  }

  public BonsaiWorldStateArchive(
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final Blockchain blockchain,
      final Optional<Long> maxLayersToLoad,
      final CachedMerkleTrieLoader cachedMerkleTrieLoader) {
    // overload while snapshots are an experimental option:
    this(
        worldStateStorage,
        blockchain,
        maxLayersToLoad,
        DataStorageConfiguration.DEFAULT_BONSAI_USE_SNAPSHOTS,
        cachedMerkleTrieLoader);
  }

  public BonsaiWorldStateArchive(
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final Blockchain blockchain,
      final Optional<Long> maxLayersToLoad,
      final boolean useSnapshots,
      final CachedMerkleTrieLoader cachedMerkleTrieLoader) {
    this(
        useSnapshots
            ? new SnapshotTrieLogManager(
                blockchain, worldStateStorage, maxLayersToLoad.orElse(RETAINED_LAYERS))
            : new LayeredTrieLogManager(
                blockchain, worldStateStorage, maxLayersToLoad.orElse(RETAINED_LAYERS)),
        worldStateStorage,
        blockchain,
        useSnapshots,
        cachedMerkleTrieLoader);
  }

  @VisibleForTesting
  BonsaiWorldStateArchive(
      final TrieLogManager trieLogManager,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final Blockchain blockchain,
      final boolean useSnapshots,
      final CachedMerkleTrieLoader cachedMerkleTrieLoader) {
    this.trieLogManager = trieLogManager;
    this.blockchain = blockchain;
    this.worldStateStorage = worldStateStorage;
    this.persistedState = new BonsaiPersistedWorldState(this, worldStateStorage);
    // TODO: https://github.com/hyperledger/besu/issues/4641
    // useSnapshots is disabled for now
    this.useSnapshots = false;
    this.cachedMerkleTrieLoader = cachedMerkleTrieLoader;
    blockchain.observeBlockAdded(this::blockAddedHandler);
  }

  private void blockAddedHandler(final BlockAddedEvent event) {
    LOG.debug("New block add event {}", event);
    if (event.isNewCanonicalHead()) {
      final BlockHeader eventBlockHeader = event.getBlock().getHeader();
      trieLogManager.updateCachedLayers(
          eventBlockHeader.getParentHash(), eventBlockHeader.getHash());
    }
  }

  @Override
  public Optional<WorldState> get(final Hash rootHash, final Hash blockHash) {
    final Optional<MutableWorldState> layeredWorldState =
        trieLogManager.getBonsaiCachedWorldState(blockHash);
    if (layeredWorldState.isPresent()) {
      return Optional.of(layeredWorldState.get());
    } else if (rootHash.equals(persistedState.blockHash())) {
      return Optional.of(persistedState);
    } else {
      return Optional.empty();
    }
  }

  @Override
  public boolean isWorldStateAvailable(final Hash rootHash, final Hash blockHash) {
    return trieLogManager.getBonsaiCachedWorldState(blockHash).isPresent()
        || persistedState.blockHash().equals(blockHash)
        || worldStateStorage.isWorldStateAvailable(rootHash, blockHash);
  }

  public Optional<MutableWorldState> getMutableSnapshot(final Hash blockHash) {
    return rollMutableStateToBlockHash(
            BonsaiSnapshotWorldState.create(this, worldStateStorage), blockHash)
        .map(SnapshotMutableWorldState.class::cast);
  }

  @Override
  public Optional<MutableWorldState> getMutable(
      final Hash rootHash, final Hash blockHash, final boolean shouldPersistState) {
    if (shouldPersistState) {
      return getMutable(rootHash, blockHash);
    } else {
      return trieLogManager
          .getBonsaiCachedWorldState(blockHash)
          .or(
              () ->
                  blockchain
                      .getBlockHeader(blockHash)
                      .filter(
                          header -> {
                            if (blockchain.getChainHeadHeader().getNumber() - header.getNumber()
                                >= trieLogManager.getMaxLayersToLoad()) {
                              LOG.warn(
                                  "Exceeded the limit of back layers that can be loaded ({})",
                                  trieLogManager.getMaxLayersToLoad());
                              return false;
                            }
                            return true;
                          })
                      .flatMap(header -> snapshotOrLayeredWorldState(blockHash, header)));
    }
  }

  private Optional<MutableWorldState> snapshotOrLayeredWorldState(
      final Hash blockHash, final BlockHeader blockHeader) {
    if (useSnapshots) {
      // use snapshots:
      return getMutableSnapshot(blockHash);
    } else {
      // otherwise use layered worldstate:
      final Optional<TrieLogLayer> trieLogLayer = trieLogManager.getTrieLogLayer(blockHash);
      return trieLogLayer.map(
          layer ->
              new BonsaiLayeredWorldState(
                  blockchain,
                  this,
                  Optional.empty(),
                  blockHeader.getNumber(),
                  fromPlugin(blockHeader.getStateRoot()),
                  layer));
    }
  }

  @Override
  public Optional<MutableWorldState> getMutable(final Hash rootHash, final Hash blockHash) {
    return rollMutableStateToBlockHash(persistedState, blockHash);
  }

  Optional<MutableWorldState> rollMutableStateToBlockHash(
      final BonsaiPersistedWorldState mutableState, final Hash blockHash) {
    if (blockHash.equals(mutableState.blockHash())) {
      return Optional.of(mutableState);
    } else {
      try {

        final Optional<BlockHeader> maybePersistedHeader =
            blockchain.getBlockHeader(mutableState.blockHash()).map(BlockHeader.class::cast);

        final List<TrieLogLayer> rollBacks = new ArrayList<>();
        final List<TrieLogLayer> rollForwards = new ArrayList<>();
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
        final BonsaiWorldStateUpdater bonsaiUpdater = getUpdaterFromPersistedState(mutableState);
        try {
          for (final TrieLogLayer rollBack : rollBacks) {
            LOG.debug("Attempting Rollback of {}", rollBack.getBlockHash());
            bonsaiUpdater.rollBack(rollBack);
          }
          for (int i = rollForwards.size() - 1; i >= 0; i--) {
            LOG.debug("Attempting Rollforward of {}", rollForwards.get(i).getBlockHash());
            bonsaiUpdater.rollForward(rollForwards.get(i));
          }
          bonsaiUpdater.commit();

          mutableState.persist(blockchain.getBlockHeader(blockHash).get());

          LOG.debug("Archive rolling finished, now at {}", blockHash);
          return Optional.of(mutableState);
        } catch (final MerkleTrieException re) {
          // need to throw to trigger the heal
          throw re;
        } catch (final Exception e) {
          // if we fail we must clean up the updater
          bonsaiUpdater.reset();
          LOG.debug("State rolling failed for block hash " + blockHash, e);
          return Optional.empty();
        }
      } catch (final RuntimeException re) {
        LOG.trace("Archive rolling failed for block hash " + blockHash, re);
        if (re instanceof MerkleTrieException) {
          // need to throw to trigger the heal
          throw re;
        }
        return Optional.empty();
      }
    }
  }

  BonsaiWorldStateUpdater getUpdaterFromPersistedState(
      final BonsaiPersistedWorldState mutableState) {
    return (BonsaiWorldStateUpdater) mutableState.updater();
  }

  public CachedMerkleTrieLoader getCachedMerkleTrieLoader() {
    return cachedMerkleTrieLoader;
  }

  @Override
  public MutableWorldState getMutable() {
    return persistedState;
  }

  public void prepareStateHealing(final Address address, final Bytes location) {
    final Set<Bytes> keysToDelete = new HashSet<>();
    final BonsaiWorldStateKeyValueStorage.BonsaiUpdater updater = worldStateStorage.updater();
    final Hash accountHash = Hash.hash(address);
    final StoredMerklePatriciaTrie<Bytes, Bytes> accountTrie =
        new StoredMerklePatriciaTrie<>(
            (l, h) -> {
              final Optional<Bytes> node = worldStateStorage.getAccountStateTrieNode(l, h);
              if (node.isPresent()) {
                keysToDelete.add(l);
              }
              return node;
            },
            persistedState.worldStateRootHash,
            Function.identity(),
            Function.identity());
    try {
      accountTrie
          .get(accountHash)
          .map(RLP::input)
          .map(StateTrieAccountValue::readFrom)
          .ifPresent(
              account -> {
                final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
                    new StoredMerklePatriciaTrie<>(
                        (l, h) -> {
                          Optional<Bytes> node =
                              worldStateStorage.getAccountStorageTrieNode(accountHash, l, h);
                          if (node.isPresent()) {
                            keysToDelete.add(Bytes.concatenate(accountHash, l));
                          }
                          return node;
                        },
                        account.getStorageRoot(),
                        Function.identity(),
                        Function.identity());
                try {
                  storageTrie.getPath(location);
                } catch (Exception eA) {
                  LOG.warn("Invalid slot found for account {} at location {}", address, location);
                  // ignore
                }
              });
    } catch (Exception eA) {
      LOG.warn("Invalid node for account {} at location {}", address, location);
      // ignore
    }
    keysToDelete.forEach(bytes -> updater.removeAccountStateTrieNode(bytes, null));
    updater.commit();
  }

  public TrieLogManager getTrieLogManager() {
    return trieLogManager;
  }

  @Override
  public void setArchiveStateUnSafe(final BlockHeader blockHeader) {
    persistedState.setArchiveStateUnSafe(blockHeader);
  }

  @Override
  public Optional<Bytes> getNodeData(final Hash hash) {
    return Optional.empty();
  }

  @Override
  public Optional<WorldStateProof> getAccountProof(
      final Hash worldStateRoot,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys) {
    // FIXME we can do proofs for layered tries and the persisted trie
    return Optional.empty();
  }
}
