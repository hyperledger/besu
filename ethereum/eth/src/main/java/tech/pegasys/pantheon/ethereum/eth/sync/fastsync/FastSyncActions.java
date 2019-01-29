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

import static tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncError.CHAIN_TOO_SHORT;
import static tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncError.NO_PEERS_AVAILABLE;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthScheduler;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.WaitForPeersTask;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.util.ExceptionUtils;

import java.util.OptionalLong;
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
  private final LabelledMetric<OperationTimer> ethTasksTimer;

  public FastSyncActions(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final LabelledMetric<OperationTimer> ethTasksTimer) {
    this.syncConfig = syncConfig;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.syncState = syncState;
    this.ethTasksTimer = ethTasksTimer;
  }

  public CompletableFuture<Void> waitForSuitablePeers() {
    final WaitForPeersTask waitForPeersTask =
        WaitForPeersTask.create(
            ethContext, syncConfig.getFastSyncMinimumPeerCount(), ethTasksTimer);

    final EthScheduler scheduler = ethContext.getScheduler();
    final CompletableFuture<Void> result = new CompletableFuture<>();
    scheduler
        .timeout(waitForPeersTask, syncConfig.getFastSyncMaximumPeerWaitTime())
        .handle(
            (waitResult, error) -> {
              if (ExceptionUtils.rootCause(error) instanceof TimeoutException) {
                if (ethContext.getEthPeers().availablePeerCount() > 0) {
                  LOG.warn(
                      "Fast sync timed out before minimum peer count was reached. Continuing with reduced peers.");
                  result.complete(null);
                } else {
                  waitForAnyPeer()
                      .thenAccept(result::complete)
                      .exceptionally(
                          taskError -> {
                            result.completeExceptionally(error);
                            return null;
                          });
                }
              } else if (error != null) {
                LOG.error("Failed to find peers for fast sync", error);
                result.completeExceptionally(error);
              } else {
                result.complete(null);
              }
              return null;
            });

    return result;
  }

  private CompletableFuture<Void> waitForAnyPeer() {
    LOG.warn(
        "Maximum wait time for fast sync reached but no peers available. Continuing to wait for any available peer.");
    final CompletableFuture<Void> result = new CompletableFuture<>();
    waitForAnyPeer(result);
    return result;
  }

  private void waitForAnyPeer(final CompletableFuture<Void> result) {
    ethContext
        .getScheduler()
        .timeout(WaitForPeersTask.create(ethContext, 1, ethTasksTimer))
        .whenComplete(
            (waitResult, throwable) -> {
              if (ExceptionUtils.rootCause(throwable) instanceof TimeoutException) {
                waitForAnyPeer(result);
              } else if (throwable != null) {
                result.completeExceptionally(throwable);
              } else {
                result.complete(waitResult);
              }
            });
  }

  public FastSyncState selectPivotBlock() {
    return ethContext
        .getEthPeers()
        .bestPeer()
        .map(
            peer -> {
              final long pivotBlockNumber =
                  peer.chainState().getEstimatedHeight() - syncConfig.fastSyncPivotDistance();
              if (pivotBlockNumber <= BlockHeader.GENESIS_BLOCK_NUMBER) {
                throw new FastSyncException(CHAIN_TOO_SHORT);
              } else {
                LOG.info("Selecting block number {} as fast sync pivot block.", pivotBlockNumber);
                return new FastSyncState(OptionalLong.of(pivotBlockNumber));
              }
            })
        .orElseThrow(() -> new FastSyncException(NO_PEERS_AVAILABLE));
  }

  public CompletableFuture<FastSyncState> downloadPivotBlockHeader(
      final FastSyncState currentState) {
    return new PivotBlockRetriever<>(
            protocolSchedule,
            ethContext,
            ethTasksTimer,
            currentState.getPivotBlockNumber().getAsLong())
        .downloadPivotBlockHeader();
  }

  public CompletableFuture<Void> downloadChain(final FastSyncState currentState) {
    final FastSyncChainDownloader<C> downloader =
        new FastSyncChainDownloader<>(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            ethTasksTimer,
            currentState.getPivotBlockHeader().get());
    downloader.start();
    return new CompletableFuture<>();
  }
}
