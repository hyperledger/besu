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
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogFactoryImpl;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.services.TrieLogService;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
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
  public Optional<BonsaiWorldState> getCheckpointedWorldState(
      final BlockHeader blockHeader,
      final Function<Long, Optional<BlockHeader>> checkpointedBlockHeaderFunction) {

    LOG.atDebug().setMessage("getting checkpointed worldstate").log();

    final long nearestCheckpointedWorldState =
        Math.abs(blockHeader.getNumber() / getMaxLayersToLoad()) * getMaxLayersToLoad();
    return checkpointedBlockHeaderFunction
        .apply(nearestCheckpointedWorldState)
        .flatMap(
            checkpointedBlockHeader ->
                rootWorldStateStorage
                    .getTrieNodeUnsafe(checkpointedBlockHeader.getStateRoot())
                    .flatMap(
                        __ -> {
                          addCachedLayer(
                              blockHeader,
                              blockHeader.getStateRoot(),
                              new BonsaiWorldState(
                                  archive,
                                  checkpointedBlockHeader.getStateRoot(),
                                  checkpointedBlockHeader.getBlockHash(),
                                  rootWorldStateStorage));
                          LOG.atDebug()
                              .setMessage("found checkpointed worldstate {} for block {}")
                              .addArgument(checkpointedBlockHeader.getNumber())
                              .addArgument(blockHeader.getNumber())
                              .log();
                          return getWorldState(blockHeader.getHash());
                        }));
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

  @VisibleForTesting
  @Override
  protected TrieLogFactory setupTrieLogFactory(final BesuContext pluginContext) {
    // if we have a TrieLogService from pluginContext, use it.
    var trieLogServicez =
        Optional.ofNullable(pluginContext)
            .flatMap(context -> context.getService(TrieLogService.class));

    if (trieLogServicez.isPresent()) {
      var trieLogService = trieLogServicez.get();
      // push the TrieLogProvider into the TrieLogService
      trieLogService.configureTrieLogProvider(getTrieLogProvider());

      // configure plugin observers:
      trieLogService.getObservers().forEach(trieLogObservers::subscribe);

      // return the TrieLogFactory implementation from the TrieLogService
      return trieLogService.getTrieLogFactory();
    } else {
      // Otherwise default to TrieLogFactoryImpl
      return new TrieLogFactoryImpl();
    }
  }

  @VisibleForTesting
  TrieLogProvider getTrieLogProvider() {
    return new TrieLogProvider() {
      @Override
      public Optional<TrieLog> getTrieLogLayer(final Hash blockHash) {
        return CachedWorldStorageManager.this.getTrieLogLayer(blockHash);
      }

      @Override
      public Optional<TrieLog> getTrieLogLayer(final long blockNumber) {
        return CachedWorldStorageManager.this
            .blockchain
            .getBlockHeader(blockNumber)
            .map(BlockHeader::getHash)
            .flatMap(CachedWorldStorageManager.this::getTrieLogLayer);
      }

      @Override
      public List<TrieLogRangeTuple> getTrieLogsByRange(
          final long fromBlockNumber, final long toBlockNumber) {
        return rangeAsStream(fromBlockNumber, toBlockNumber)
            .map(blockchain::getBlockHeader)
            .map(
                headerOpt ->
                    headerOpt.flatMap(
                        header ->
                            CachedWorldStorageManager.this
                                .getTrieLogLayer(header.getBlockHash())
                                .map(
                                    layer ->
                                        new TrieLogRangeTuple(
                                            header.getBlockHash(), header.getNumber(), layer))))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
      }

      Stream<Long> rangeAsStream(final long fromBlockNumber, final long toBlockNumber) {
        if (Math.abs(toBlockNumber - fromBlockNumber) > LOG_RANGE_LIMIT) {
          throw new IllegalArgumentException("Requested Range too large");
        }
        long left = Math.min(fromBlockNumber, toBlockNumber);
        long right = Math.max(fromBlockNumber, toBlockNumber);
        return LongStream.range(left, right).boxed();
      }
    };
  }
}
