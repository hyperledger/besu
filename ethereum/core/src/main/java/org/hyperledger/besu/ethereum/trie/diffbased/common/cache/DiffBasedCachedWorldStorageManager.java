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
package org.hyperledger.besu.ethereum.trie.diffbased.common.cache;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.common.StorageSubscriber;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedLayeredWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldStateConfig;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DiffBasedCachedWorldStorageManager implements StorageSubscriber {
  public static final long RETAINED_LAYERS = 512; // at least 256 + typical rollbacks
  private static final Logger LOG =
      LoggerFactory.getLogger(DiffBasedCachedWorldStorageManager.class);
  private final DiffBasedWorldStateProvider archive;
  private final EvmConfiguration evmConfiguration;
  protected final Supplier<DiffBasedWorldStateConfig> defaultBonsaiWorldStateConfigSupplier;
  private final Cache<Hash, BlockHeader> stateRootToBlockHeaderCache =
      Caffeine.newBuilder()
          .maximumSize(RETAINED_LAYERS)
          .expireAfterWrite(100, TimeUnit.MINUTES)
          .build();

  private final DiffBasedWorldStateKeyValueStorage rootWorldStateStorage;
  private final Map<Bytes32, DiffBasedCachedWorldView> cachedWorldStatesByHash;

  private DiffBasedCachedWorldStorageManager(
      final DiffBasedWorldStateProvider archive,
      final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Map<Bytes32, DiffBasedCachedWorldView> cachedWorldStatesByHash,
      final EvmConfiguration evmConfiguration,
      final Supplier<DiffBasedWorldStateConfig> defaultBonsaiWorldStateConfigSupplier) {
    worldStateKeyValueStorage.subscribe(this);
    this.rootWorldStateStorage = worldStateKeyValueStorage;
    this.cachedWorldStatesByHash = cachedWorldStatesByHash;
    this.archive = archive;
    this.evmConfiguration = evmConfiguration;
    this.defaultBonsaiWorldStateConfigSupplier = defaultBonsaiWorldStateConfigSupplier;
  }

  public DiffBasedCachedWorldStorageManager(
      final DiffBasedWorldStateProvider archive,
      final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Supplier<DiffBasedWorldStateConfig> defaultBonsaiWorldStateConfigSupplier) {
    this(
        archive,
        worldStateKeyValueStorage,
        new ConcurrentHashMap<>(),
        EvmConfiguration.DEFAULT,
        defaultBonsaiWorldStateConfigSupplier);
  }

  public synchronized void addCachedLayer(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final DiffBasedWorldState forWorldState) {
    final Optional<DiffBasedCachedWorldView> cachedDiffBasedWorldView =
        Optional.ofNullable(this.cachedWorldStatesByHash.get(blockHeader.getBlockHash()));
    if (cachedDiffBasedWorldView.isPresent()) {
      // only replace if it is a layered storage
      if (forWorldState.isPersisted()
          && cachedDiffBasedWorldView.get().getWorldStateStorage()
              instanceof DiffBasedLayeredWorldStateKeyValueStorage) {
        LOG.atDebug()
            .setMessage("updating layered world state for block {}, state root hash {}")
            .addArgument(blockHeader::toLogString)
            .addArgument(worldStateRootHash::toShortHexString)
            .log();
        cachedDiffBasedWorldView
            .get()
            .updateWorldStateStorage(
                createSnapshotKeyValueStorage(forWorldState.getWorldStateStorage()));
      }
    } else {
      LOG.atDebug()
          .setMessage("adding layered world state for block {}, state root hash {}")
          .addArgument(blockHeader::toLogString)
          .addArgument(worldStateRootHash::toShortHexString)
          .log();
      if (forWorldState.isPersisted()) {
        cachedWorldStatesByHash.put(
            blockHeader.getHash(),
            new DiffBasedCachedWorldView(
                blockHeader, createSnapshotKeyValueStorage(forWorldState.getWorldStateStorage())));
      } else {
        // otherwise, add the layer to the cache
        cachedWorldStatesByHash.put(
            blockHeader.getHash(),
            new DiffBasedCachedWorldView(
                blockHeader,
                ((DiffBasedLayeredWorldStateKeyValueStorage) forWorldState.getWorldStateStorage())
                    .clone()));
      }
      // add stateroot -> blockHeader cache entry
      stateRootToBlockHeaderCache.put(blockHeader.getStateRoot(), blockHeader);
    }
    scrubCachedLayers(blockHeader.getNumber());
  }

  private synchronized void scrubCachedLayers(final long newMaxHeight) {
    if (cachedWorldStatesByHash.size() > RETAINED_LAYERS) {
      final long waterline = newMaxHeight - RETAINED_LAYERS;
      cachedWorldStatesByHash.values().stream()
          .filter(layer -> layer.getBlockNumber() < waterline)
          .toList()
          .forEach(
              layer -> {
                cachedWorldStatesByHash.remove(layer.getBlockHash());
                layer.close();
              });
    }
  }

  public Optional<DiffBasedWorldState> getWorldState(final Hash blockHash) {
    if (cachedWorldStatesByHash.containsKey(blockHash)) {
      // return a new worldstate using worldstate storage and an isolated copy of the updater
      return Optional.ofNullable(cachedWorldStatesByHash.get(blockHash))
          .map(
              cached ->
                  createWorldState(
                      archive,
                      createLayeredKeyValueStorage(cached.getWorldStateStorage()),
                      evmConfiguration));
    }
    LOG.atDebug()
        .setMessage("did not find worldstate in cache for {}")
        .addArgument(blockHash.toShortHexString())
        .log();

    return Optional.empty();
  }

  public Optional<DiffBasedWorldState> getNearestWorldState(final BlockHeader blockHeader) {
    LOG.atDebug()
        .setMessage("getting nearest worldstate for {}")
        .addArgument(blockHeader.toLogString())
        .log();

    return Optional.ofNullable(
            cachedWorldStatesByHash.get(blockHeader.getParentHash())) // search parent block
        .map(DiffBasedCachedWorldView::getWorldStateStorage)
        .or(
            () -> {
              // or else search the nearest state in the cache
              LOG.atDebug()
                  .setMessage("searching cache for nearest worldstate for {}")
                  .addArgument(blockHeader.toLogString())
                  .log();

              final List<DiffBasedCachedWorldView> cachedDiffBasedWorldViews =
                  new ArrayList<>(cachedWorldStatesByHash.values());
              return cachedDiffBasedWorldViews.stream()
                  .sorted(
                      Comparator.comparingLong(
                          view -> Math.abs(blockHeader.getNumber() - view.getBlockNumber())))
                  .map(DiffBasedCachedWorldView::getWorldStateStorage)
                  .findFirst();
            })
        .map(
            storage ->
                createWorldState( // wrap the state in a layered worldstate
                    archive, createLayeredKeyValueStorage(storage), evmConfiguration));
  }

  public Optional<DiffBasedWorldState> getHeadWorldState(
      final Function<Hash, Optional<BlockHeader>> hashBlockHeaderFunction) {

    LOG.atDebug().setMessage("getting head worldstate").log();

    return rootWorldStateStorage
        .getWorldStateBlockHash()
        .flatMap(hashBlockHeaderFunction)
        .flatMap(
            blockHeader -> {
              // add the head to the cache
              addCachedLayer(
                  blockHeader,
                  blockHeader.getStateRoot(),
                  createWorldState(archive, rootWorldStateStorage, evmConfiguration));
              return getWorldState(blockHeader.getHash());
            });
  }

  public boolean contains(final Hash blockHash) {
    return cachedWorldStatesByHash.containsKey(blockHash);
  }

  public void reset() {
    this.cachedWorldStatesByHash.clear();
  }

  public void primeRootToBlockHashCache(final Blockchain blockchain, final int numEntries) {
    // prime the stateroot-to-blockhash cache
    long head = blockchain.getChainHeadHeader().getNumber();
    for (long i = head; i > Math.max(0, head - numEntries); i--) {
      blockchain
          .getBlockHeader(i)
          .ifPresent(header -> stateRootToBlockHeaderCache.put(header.getStateRoot(), header));
    }
  }

  /**
   * Returns the worldstate for the supplied root hash. If the worldstate is not already in cache,
   * this method will attempt to fetch it and add it to the cache. synchronized to prevent
   * concurrent loads/adds to the cache of the same root hash.
   *
   * @param rootHash rootHash to supply worldstate storage for
   * @return Optional worldstate storage
   */
  public synchronized Optional<DiffBasedWorldStateKeyValueStorage> getStorageByRootHash(
      final Hash rootHash) {
    return Optional.ofNullable(stateRootToBlockHeaderCache.getIfPresent(rootHash))
        .flatMap(
            header ->
                Optional.ofNullable(cachedWorldStatesByHash.get(header.getHash()))
                    .map(DiffBasedCachedWorldView::getWorldStateStorage)
                    .or(
                        () -> {
                          // if not cached already, maybe fetch and cache this worldstate
                          var maybeWorldState =
                              archive.getMutable(header, false).map(BonsaiWorldState.class::cast);
                          maybeWorldState.ifPresent(
                              ws -> addCachedLayer(header, header.getStateRoot(), ws));
                          return maybeWorldState.map(BonsaiWorldState::getWorldStateStorage);
                        }));
  }

  @Override
  public void onClearStorage() {
    this.cachedWorldStatesByHash.clear();
  }

  @Override
  public void onClearFlatDatabaseStorage() {
    this.cachedWorldStatesByHash.clear();
  }

  @Override
  public void onClearTrieLog() {
    this.cachedWorldStatesByHash.clear();
  }

  @Override
  public void onClearTrie() {
    this.cachedWorldStatesByHash.clear();
  }

  @Override
  public void onCloseStorage() {
    this.cachedWorldStatesByHash.clear();
  }

  public abstract DiffBasedWorldState createWorldState(
      final DiffBasedWorldStateProvider archive,
      final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage,
      final EvmConfiguration evmConfiguration);

  public abstract DiffBasedWorldStateKeyValueStorage createLayeredKeyValueStorage(
      final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage);

  public abstract DiffBasedWorldStateKeyValueStorage createSnapshotKeyValueStorage(
      final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage);
}
