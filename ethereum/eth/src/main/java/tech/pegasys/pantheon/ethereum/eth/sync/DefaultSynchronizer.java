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

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.SyncStatus;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.state.PendingBlocks;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultSynchronizer<C> implements Synchronizer {

  private static final Logger LOG = LogManager.getLogger();

  private final SyncState syncState;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final BlockPropagationManager<C> blockPropagationManager;
  private final Downloader<C> downloader;

  public DefaultSynchronizer(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final MetricsSystem metricsSystem) {
    this.syncState = syncState;
    this.blockPropagationManager =
        new BlockPropagationManager<>(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            new PendingBlocks(),
            metricsSystem);
    this.downloader =
        new Downloader<>(syncConfig, protocolSchedule, protocolContext, ethContext, syncState);

    ChainHeadTracker.trackChainHeadForPeers(
        ethContext, protocolSchedule, protocolContext.getBlockchain(), syncConfig);
    if (syncConfig.syncMode().equals(SyncMode.FAST)) {
      LOG.info("Fast sync enabled.");
    }
  }

  @Override
  public void start() {
    if (started.compareAndSet(false, true)) {
      LOG.info("Starting synchronizer.");
      blockPropagationManager.start();
      downloader.start();
    } else {
      throw new IllegalStateException("Attempt to start an already started synchronizer.");
    }
  }

  @Override
  public Optional<SyncStatus> getSyncStatus() {
    if (!started.get()) {
      return Optional.empty();
    }
    return Optional.of(syncState.syncStatus());
  }
}
