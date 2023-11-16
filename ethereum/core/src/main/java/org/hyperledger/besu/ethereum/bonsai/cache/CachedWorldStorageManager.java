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
package org.hyperledger.besu.ethereum.bonsai.cache;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiSnapshotWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateLayerStorage;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiVerkleWorldState;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedWorldStorageManager implements BonsaiStorageSubscriber {
  public static final long RETAINED_LAYERS = 512; // at least 256 + typical rollbacks
  private static final Logger LOG = LoggerFactory.getLogger(CachedWorldStorageManager.class);
  private final BonsaiWorldStateProvider archive;
  private final ObservableMetricsSystem metricsSystem;
  private final EvmConfiguration evmConfiguration;

  private final BonsaiWorldStateKeyValueStorage rootWorldStateStorage;
  private final Map<Bytes32, CachedBonsaiWorldView> cachedWorldStatesByHash;

  private CachedWorldStorageManager(
      final BonsaiWorldStateProvider archive,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final Map<Bytes32, CachedBonsaiWorldView> cachedWorldStatesByHash,
      final ObservableMetricsSystem metricsSystem,
      final EvmConfiguration evmConfiguration) {
    worldStateStorage.subscribe(this);
    this.rootWorldStateStorage = worldStateStorage;
    this.cachedWorldStatesByHash = cachedWorldStatesByHash;
    this.archive = archive;
    this.metricsSystem = metricsSystem;
    this.evmConfiguration = evmConfiguration;
  }

  public CachedWorldStorageManager(
      final BonsaiWorldStateProvider archive,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final ObservableMetricsSystem metricsSystem) {
    this(
        archive,
        worldStateStorage,
        new ConcurrentHashMap<>(),
        metricsSystem,
        EvmConfiguration.DEFAULT);
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
                new BonsaiSnapshotWorldStateKeyValueStorage(
                    forWorldState.getWorldStateStorage(), metricsSystem));
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
                new BonsaiSnapshotWorldStateKeyValueStorage(
                    forWorldState.getWorldStateStorage(), metricsSystem)));
      } else {
        // otherwise, add the layer to the cache
        cachedWorldStatesByHash.put(
            blockHeader.getHash(),
            new CachedBonsaiWorldView(
                blockHeader,
                ((BonsaiWorldStateLayerStorage) forWorldState.getWorldStateStorage()).clone()));
      }
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

  public Optional<BonsaiVerkleWorldState> getWorldState(final Hash blockHash) {
    if (cachedWorldStatesByHash.containsKey(blockHash)) {
      // return a new worldstate using worldstate storage and an isolated copy of the updater
      return Optional.ofNullable(cachedWorldStatesByHash.get(blockHash))
          .map(
              cached ->
                  new BonsaiVerkleWorldState(
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

  public Optional<BonsaiVerkleWorldState> getNearestWorldState(final BlockHeader blockHeader) {
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
                new BonsaiVerkleWorldState( // wrap the state in a layered worldstate
                    archive, new BonsaiWorldStateLayerStorage(storage), evmConfiguration));
  }

  public Optional<BonsaiVerkleWorldState> getHeadWorldState(
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
                  new BonsaiVerkleWorldState(archive, rootWorldStateStorage, evmConfiguration));
              return getWorldState(blockHeader.getHash());
            });
  }

  public boolean containWorldStateStorage(final Hash blockHash) {
    return cachedWorldStatesByHash.containsKey(blockHash);
  }

  public void reset() {
    this.cachedWorldStatesByHash.clear();
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
