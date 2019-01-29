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
import static tech.pegasys.pantheon.ethereum.eth.sync.fastsync.PivotBlockRetriever.MAX_PIVOT_BLOCK_RETRIES;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncTargetManager;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncTarget;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.RetryingGetHeaderFromPeerByNumberTask;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class FastSyncTargetManager<C> extends SyncTargetManager<C> {
  private static final Logger LOG = LogManager.getLogger();

  private final ProtocolSchedule<C> protocolSchedule;
  private final EthContext ethContext;
  private final LabelledMetric<OperationTimer> ethTasksTimer;
  private final BlockHeader pivotBlockHeader;

  public FastSyncTargetManager(
      final SynchronizerConfiguration config,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final LabelledMetric<OperationTimer> ethTasksTimer,
      final BlockHeader pivotBlockHeader) {
    super(config, protocolSchedule, protocolContext, ethContext, syncState, ethTasksTimer);
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.ethTasksTimer = ethTasksTimer;
    this.pivotBlockHeader = pivotBlockHeader;
  }

  @Override
  protected CompletableFuture<Optional<EthPeer>> selectBestAvailableSyncTarget() {
    final Optional<EthPeer> maybeBestPeer = ethContext.getEthPeers().bestPeer();
    if (!maybeBestPeer.isPresent()) {
      LOG.info("No sync target, wait for peers.");
      return completedFuture(Optional.empty());
    } else {
      final EthPeer bestPeer = maybeBestPeer.get();
      if (bestPeer.chainState().getEstimatedHeight() < pivotBlockHeader.getNumber()) {
        LOG.info("No sync target with sufficient chain height, wait for peers.");
        return completedFuture(Optional.empty());
      } else {
        return confirmPivotBlockHeader(bestPeer);
      }
    }
  }

  private CompletableFuture<Optional<EthPeer>> confirmPivotBlockHeader(final EthPeer bestPeer) {
    final RetryingGetHeaderFromPeerByNumberTask task =
        RetryingGetHeaderFromPeerByNumberTask.forSingleNumber(
            protocolSchedule,
            ethContext,
            ethTasksTimer,
            pivotBlockHeader.getNumber(),
            MAX_PIVOT_BLOCK_RETRIES);
    task.assignPeer(bestPeer);
    return ethContext
        .getScheduler()
        .timeout(task)
        .thenApply(
            result -> {
              if (result.size() != 1 || !result.get(0).equals(pivotBlockHeader)) {
                bestPeer.disconnect(DisconnectReason.USELESS_PEER);
                return Optional.<EthPeer>empty();
              } else {
                return Optional.of(bestPeer);
              }
            })
        .exceptionally(
            error -> {
              LOG.debug("Could not confirm best peer had pivot block", error);
              return Optional.empty();
            });
  }

  @Override
  public boolean shouldSwitchSyncTarget(final SyncTarget currentTarget) {
    return false;
  }
}
