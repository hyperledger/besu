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
import org.hyperledger.besu.ethereum.bonsai.trielog.AbstractTrieLogManager;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.BesuContext;

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

public class CachedWorldStorageManager extends AbstractTrieLogManager
    implements BonsaiStorageSubscriber {
  private static final Logger LOG = LoggerFactory.getLogger(CachedWorldStorageManager.class);
  private final BonsaiWorldStateProvider archive;
  private final ObservableMetricsSystem metricsSystem;

  CachedWorldStorageManager(
      final BonsaiWorldStateProvider archive,
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad,
      final Map<Bytes32, CachedBonsaiWorldView> cachedWorldStatesByHash,
      final BesuContext pluginContext,
      final ObservableMetricsSystem metricsSystem) {
    super(blockchain, worldStateStorage, maxLayersToLoad, cachedWorldStatesByHash, pluginContext);
    worldStateStorage.subscribe(this);
    this.archive = archive;
    this.metricsSystem = metricsSystem;
  }

  public CachedWorldStorageManager(
      final BonsaiWorldStateProvider archive,
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final ObservableMetricsSystem metricsSystem,
      final long maxLayersToLoad,
      final BesuContext pluginContext) {
    this(
        archive,
        blockchain,
        worldStateStorage,
        maxLayersToLoad,
        new ConcurrentHashMap<>(),
        pluginContext,
        metricsSystem);
  }

  @Override
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

  @Override
  public Optional<BonsaiWorldState> getWorldState(final Hash blockHash) {
    if (cachedWorldStatesByHash.containsKey(blockHash)) {
      // return a new worldstate using worldstate storage and an isolated copy of the updater
      return Optional.ofNullable(cachedWorldStatesByHash.get(blockHash))
          .map(
              cached ->
                  new BonsaiWorldState(
                      archive, new BonsaiWorldStateLayerStorage(cached.getWorldStateStorage())));
    }
    LOG.atDebug()
        .setMessage("did not find worldstate in cache for {}")
        .addArgument(blockHash.toShortHexString())
        .log();

    return Optional.empty();
  }

  @Override
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
                    archive, new BonsaiWorldStateLayerStorage(storage)));
  }

  @Override
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
                  new BonsaiWorldState(archive, rootWorldStateStorage));
              return getWorldState(blockHeader.getHash());
            });
  }

  @Override
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
