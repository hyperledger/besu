/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.SyncStatus;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncException;
import tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.fullsync.FullSyncDownloader;
import tech.pegasys.pantheon.ethereum.eth.sync.state.PendingBlocks;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.util.ExceptionUtils;
import tech.pegasys.pantheon.util.Subscribers;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultSynchronizer<C> implements Synchronizer {

  private static final Logger LOG = LogManager.getLogger();

  private final SynchronizerConfiguration syncConfig;
  private final EthContext ethContext;
  private final SyncState syncState;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final BlockPropagationManager<C> blockPropagationManager;
  private final FullSyncDownloader<C> fullSyncDownloader;
  private final Optional<FastSynchronizer<C>> fastSynchronizer;
  private final Subscribers<SyncStatusListener> syncStatusListeners = new Subscribers<>();

  public DefaultSynchronizer(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final WorldStateStorage worldStateStorage,
      final EthContext ethContext,
      final SyncState syncState,
      final Path dataDirectory,
      final MetricsSystem metricsSystem) {
    this.syncConfig = syncConfig;
    this.ethContext = ethContext;
    this.syncState = syncState;

    this.blockPropagationManager =
        new BlockPropagationManager<>(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            new PendingBlocks(),
            metricsSystem,
            new BlockBroadcaster(ethContext));

    ChainHeadTracker.trackChainHeadForPeers(
        ethContext, protocolSchedule, protocolContext.getBlockchain(), syncConfig, metricsSystem);

    this.fullSyncDownloader =
        new FullSyncDownloader<>(
            syncConfig, protocolSchedule, protocolContext, ethContext, syncState, metricsSystem);

    fastSynchronizer =
        FastSynchronizer.create(
            syncConfig,
            dataDirectory,
            protocolSchedule,
            protocolContext,
            metricsSystem,
            ethContext,
            worldStateStorage,
            syncState);
  }

  @Override
  public void start() {
    if (started.compareAndSet(false, true)) {
      syncState.addSyncStatusListener(this::syncStatusCallback);
      if (fastSynchronizer.isPresent()) {
        fastSynchronizer.get().start().whenComplete(this::handleFastSyncResult);
      } else {
        startFullSync();
      }
    } else {
      throw new IllegalStateException("Attempt to start an already started synchronizer.");
    }
  }

  private void handleFastSyncResult(final FastSyncState result, final Throwable error) {
    final Throwable rootCause = ExceptionUtils.rootCause(error);
    if (rootCause instanceof FastSyncException) {
      LOG.error(
          "Fast sync failed ({}), switching to full sync.",
          ((FastSyncException) rootCause).getError());
    } else if (error != null) {
      LOG.error("Fast sync failed, switching to full sync.", error);
    } else {
      LOG.info(
          "Fast sync completed successfully with pivot block {}",
          result.getPivotBlockNumber().getAsLong());
    }
    fastSynchronizer.ifPresent(FastSynchronizer::deleteFastSyncState);

    startFullSync();
  }

  private void startFullSync() {
    LOG.info("Starting synchronizer.");
    blockPropagationManager.start();
    fullSyncDownloader.start();
  }

  @Override
  public Optional<SyncStatus> getSyncStatus() {
    if (!started.get()) {
      return Optional.empty();
    }
    return Optional.of(syncState.syncStatus());
  }

  @Override
  public boolean hasSufficientPeers() {
    final int requiredPeerCount =
        fastSynchronizer.isPresent() ? syncConfig.getFastSyncMinimumPeerCount() : 1;
    return ethContext.getEthPeers().availablePeerCount() >= requiredPeerCount;
  }

  @Override
  public long observeSyncStatus(final SyncStatusListener listener) {
    checkNotNull(listener);
    return syncStatusListeners.subscribe(listener);
  }

  @Override
  public boolean removeObserver(final long observerId) {
    return syncStatusListeners.unsubscribe(observerId);
  }

  private void syncStatusCallback(final SyncStatus status) {
    syncStatusListeners.forEach(c -> c.onSyncStatus(status));
  }
}
