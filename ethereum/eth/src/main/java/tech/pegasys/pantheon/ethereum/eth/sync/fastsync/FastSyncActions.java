/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncError.CHAIN_TOO_SHORT;
import static tech.pegasys.pantheon.util.FutureUtils.completedExceptionally;
import static tech.pegasys.pantheon.util.FutureUtils.exceptionallyCompose;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.task.WaitForPeersTask;
import tech.pegasys.pantheon.ethereum.eth.sync.ChainDownloader;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.util.ExceptionUtils;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FastSyncActions<C> {

  private static final Logger LOG = LogManager.getLogger();
  private final SynchronizerConfiguration syncConfig;
  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final SyncState syncState;
  private final MetricsSystem metricsSystem;

  public FastSyncActions(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final MetricsSystem metricsSystem) {
    this.syncConfig = syncConfig;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.syncState = syncState;
    this.metricsSystem = metricsSystem;
  }

  public CompletableFuture<FastSyncState> waitForSuitablePeers(final FastSyncState fastSyncState) {
    if (fastSyncState.hasPivotBlockHeader()) {
      return waitForAnyPeer().thenApply(ignore -> fastSyncState);
    }

    final WaitForPeersTask waitForPeersTask =
        WaitForPeersTask.create(
            ethContext, syncConfig.getFastSyncMinimumPeerCount(), metricsSystem);

    LOG.debug("Waiting for at least {} peers.", syncConfig.getFastSyncMinimumPeerCount());
    return ethContext
        .getScheduler()
        .scheduleServiceTask(waitForPeersTask)
        .thenApply(successfulWaitResult -> fastSyncState);
  }

  private CompletableFuture<Void> waitForAnyPeer() {
    final CompletableFuture<Void> waitForPeerResult =
        ethContext.getScheduler().timeout(WaitForPeersTask.create(ethContext, 1, metricsSystem));
    return exceptionallyCompose(
        waitForPeerResult,
        throwable -> {
          if (ExceptionUtils.rootCause(throwable) instanceof TimeoutException) {
            return waitForAnyPeer();
          }
          return completedExceptionally(throwable);
        });
  }

  public CompletableFuture<FastSyncState> selectPivotBlock(final FastSyncState fastSyncState) {
    return fastSyncState.hasPivotBlockHeader()
        ? completedFuture(fastSyncState)
        : selectPivotBlockFromPeers();
  }

  private CompletableFuture<FastSyncState> selectPivotBlockFromPeers() {
    return ethContext
        .getEthPeers()
        .bestPeer()
        .filter(peer -> peer.chainState().hasEstimatedHeight())
        .map(
            peer -> {
              final long pivotBlockNumber =
                  peer.chainState().getEstimatedHeight() - syncConfig.fastSyncPivotDistance();
              if (pivotBlockNumber <= BlockHeader.GENESIS_BLOCK_NUMBER) {
                throw new FastSyncException(CHAIN_TOO_SHORT);
              } else {
                LOG.info("Selecting block number {} as fast sync pivot block.", pivotBlockNumber);
                return completedFuture(new FastSyncState(pivotBlockNumber));
              }
            })
        .orElseGet(this::retrySelectPivotBlockAfterDelay);
  }

  private CompletableFuture<FastSyncState> retrySelectPivotBlockAfterDelay() {
    LOG.info("Waiting for peer with known chain height");
    return ethContext
        .getScheduler()
        .scheduleFutureTask(
            () -> waitForAnyPeer().thenCompose(ignore -> selectPivotBlockFromPeers()),
            Duration.ofSeconds(1));
  }

  public CompletableFuture<FastSyncState> downloadPivotBlockHeader(
      final FastSyncState currentState) {
    if (currentState.getPivotBlockHeader().isPresent()) {
      return completedFuture(currentState);
    }
    return new PivotBlockRetriever<>(
            protocolSchedule,
            ethContext,
            metricsSystem,
            currentState.getPivotBlockNumber().getAsLong())
        .downloadPivotBlockHeader();
  }

  public ChainDownloader createChainDownloader(final FastSyncState currentState) {
    return FastSyncChainDownloader.create(
        syncConfig,
        protocolSchedule,
        protocolContext,
        ethContext,
        syncState,
        metricsSystem,
        currentState.getPivotBlockHeader().get());
  }
}
