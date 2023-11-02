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
 */
package org.hyperledger.besu.ethereum.trie.bonsai.cache;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiSnapshotWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateLayerStorage;
import org.hyperledger.besu.ethereum.trie.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedWorldStorageManager
    implements BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber {
  public static final long RETAINED_LAYERS = 512; // at least 256 + typical rollbacks
  private static final Logger LOG = LoggerFactory.getLogger(CachedWorldStorageManager.class);
  private final BonsaiWorldStateProvider archive;
  private final EvmConfiguration evmConfiguration;
  private final Cache<Hash, BlockHeader> stateRootToBlockHeaderCache =
      Caffeine.newBuilder().maximumSize(512).expireAfterWrite(100, TimeUnit.MINUTES).build();

  private final BonsaiWorldStateKeyValueStorage rootWorldStateStorage;
  private final Map<Bytes32, CachedBonsaiWorldView> cachedWorldStatesByHash;

  private CachedWorldStorageManager(
      final BonsaiWorldStateProvider archive,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final Map<Bytes32, CachedBonsaiWorldView> cachedWorldStatesByHash,
      final EvmConfiguration evmConfiguration) {
    worldStateStorage.subscribe(this);
    this.rootWorldStateStorage = worldStateStorage;
    this.cachedWorldStatesByHash = cachedWorldStatesByHash;
    this.archive = archive;
    this.evmConfiguration = evmConfiguration;
  }

  public CachedWorldStorageManager(
      final BonsaiWorldStateProvider archive,
      final BonsaiWorldStateKeyValueStorage worldStateStorage) {
    this(archive, worldStateStorage, new ConcurrentHashMap<>(), EvmConfiguration.DEFAULT);
  }

  public synchronized void addCachedLayer(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final BonsaiWorldState forWorldState) {
    final Optional<CachedBonsaiWorldView> cachedBonsaiWorldView =
        Optional.ofNullable(this.cachedWorldStatesByHash.get(blockHeader.getBlockHash()));
    if (cachedBonsaiWorldView.isPresent()) {
      // only replace if it is a layered storage
      if (forWorldState.isPersisted()
          && cachedBonsaiWorldView.get().getWorldStateStorage()
              instanceof BonsaiWorldStateLayerStorage) {
        LOG.atDebug()
            .setMessage("updating layered world state for block {}, state root hash {}")
            .addArgument(blockHeader::toLogString)
            .addArgument(worldStateRootHash::toShortHexString)
            .log();
        cachedBonsaiWorldView
            .get()
            .updateWorldStateStorage(
                new BonsaiSnapshotWorldStateKeyValueStorage(forWorldState.getWorldStateStorage()));
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
            new CachedBonsaiWorldView(
                blockHeader,
                new BonsaiSnapshotWorldStateKeyValueStorage(forWorldState.getWorldStateStorage())));
      } else {
        // otherwise, add the layer to the cache
        cachedWorldStatesByHash.put(
            blockHeader.getHash(),
            new CachedBonsaiWorldView(
                blockHeader,
                ((BonsaiWorldStateLayerStorage) forWorldState.getWorldStateStorage()).clone()));
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

  public Optional<BonsaiWorldState> getWorldState(final Hash blockHash) {
    if (cachedWorldStatesByHash.containsKey(blockHash)) {
      // return a new worldstate using worldstate storage and an isolated copy of the updater
      return Optional.ofNullable(cachedWorldStatesByHash.get(blockHash))
          .map(
              cached ->
                  new BonsaiWorldState(
                      archive,
                      new BonsaiWorldStateLayerStorage(cached.getWorldStateStorage()),
                      evmConfiguration));
    }
    LOG.atDebug()
        .setMessage("did not find worldstate in cache for {}")
        .addArgument(blockHash.toShortHexString())
        .log();

    return Optional.empty();
  }

  public Optional<BonsaiWorldState> getNearestWorldState(final BlockHeader blockHeader) {
    LOG.atDebug()
        .setMessage("getting nearest worldstate for {}")
        .addArgument(blockHeader.toLogString())
        .log();

    return Optional.ofNullable(
            cachedWorldStatesByHash.get(blockHeader.getParentHash())) // search parent block
        .map(CachedBonsaiWorldView::getWorldStateStorage)
        .or(
            () -> {
              // or else search the nearest state in the cache
              LOG.atDebug()
                  .setMessage("searching cache for nearest worldstate for {}")
                  .addArgument(blockHeader.toLogString())
                  .log();

              final List<CachedBonsaiWorldView> cachedBonsaiWorldViews =
                  new ArrayList<>(cachedWorldStatesByHash.values());
              return cachedBonsaiWorldViews.stream()
                  .sorted(
                      Comparator.comparingLong(
                          view -> Math.abs(blockHeader.getNumber() - view.getBlockNumber())))
                  .map(CachedBonsaiWorldView::getWorldStateStorage)
                  .findFirst();
            })
        .map(
            storage ->
                new BonsaiWorldState( // wrap the state in a layered worldstate
                    archive, new BonsaiWorldStateLayerStorage(storage), evmConfiguration));
  }

  public Optional<BonsaiWorldState> getHeadWorldState(
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
                  new BonsaiWorldState(archive, rootWorldStateStorage, evmConfiguration));
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

  public Optional<BonsaiWorldStateKeyValueStorage> getStorageByRootHash(
      final Optional<Hash> rootHash) {
    if (rootHash.isPresent()) {
      // if we supplied a hash, return the worldstate for that hash if it is available:
      return rootHash
          .map(stateRootToBlockHeaderCache::getIfPresent)
          .flatMap(
              header ->
                  Optional.ofNullable(cachedWorldStatesByHash.get(header.getHash()))
                      .map(CachedBonsaiWorldView::getWorldStateStorage)
                      .or(
                          () -> {
                            // if not cached already, maybe fetch and cache this worldstate
                            var maybeWorldState =
                                archive
                                    .getMutable(header, false)
                                    .map(BonsaiWorldState.class::cast)
                                    .map(BonsaiWorldState::getWorldStateStorage);
                            if (maybeWorldState.isPresent()) {
                              cachedWorldStatesByHash.put(
                                  header.getHash(),
                                  new CachedBonsaiWorldView(header, maybeWorldState.get()));
                            }
                            return maybeWorldState;
                          }));
    } else {
      // if we did not supply a hash, return the head worldstate from cachedWorldStates
      return rootWorldStateStorage
          .getWorldStateBlockHash()
          .map(cachedWorldStatesByHash::get)
          .map(CachedBonsaiWorldView::getWorldStateStorage);
    }
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
  public void onCloseStorage() {
    this.cachedWorldStatesByHash.clear();
  }
}
