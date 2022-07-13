/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.sync;

import static java.util.concurrent.CompletableFuture.completedFuture;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.WaitForPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncTarget;
import org.hyperledger.besu.ethereum.eth.sync.tasks.DetermineCommonAncestorTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SyncTargetManager {

  private static final Logger LOG = LoggerFactory.getLogger(SyncTargetManager.class);

  private final SynchronizerConfiguration config;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;

  protected SyncTargetManager(
      final SynchronizerConfiguration config,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final MetricsSystem metricsSystem) {
    this.config = config;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
  }

  public CompletableFuture<SyncTarget> findSyncTarget() {
    return selectBestAvailableSyncTarget()
        .thenCompose(
            maybeBestPeer -> {
              if (maybeBestPeer.isPresent()) {
                final EthPeer bestPeer = maybeBestPeer.get();
                return DetermineCommonAncestorTask.create(
                        protocolSchedule,
                        protocolContext,
                        ethContext,
                        bestPeer,
                        config.getDownloaderHeaderRequestSize(),
                        metricsSystem)
                    .run()
                    .handle(
                        (result, error) -> {
                          if (error != null) {
                            LOG.debug("Failed to find common ancestor", error);
                          }
                          return result;
                        })
                    .thenCompose(
                        (target) -> {
                          if (target == null) {
                            return waitForPeerAndThenSetSyncTarget();
                          }
                          final SyncTarget syncTarget = new SyncTarget(bestPeer, target);
                          LOG.debug(
                              "Found common ancestor with peer {} at block {}",
                              bestPeer,
                              target.getNumber());
                          return completedFuture(syncTarget);
                        })
                    .thenCompose(
                        syncTarget ->
                            finalizeSelectedSyncTarget(syncTarget)
                                .map(CompletableFuture::completedFuture)
                                .orElseGet(this::waitForPeerAndThenSetSyncTarget));
              } else {
                return waitForPeerAndThenSetSyncTarget();
              }
            });
  }

  protected Optional<SyncTarget> finalizeSelectedSyncTarget(final SyncTarget syncTarget) {
    return Optional.of(syncTarget);
  }

  protected abstract CompletableFuture<Optional<EthPeer>> selectBestAvailableSyncTarget();

  private CompletableFuture<SyncTarget> waitForPeerAndThenSetSyncTarget() {
    return waitForNewPeer().handle((r, t) -> r).thenCompose((r) -> findSyncTarget());
  }

  private CompletableFuture<?> waitForNewPeer() {
    return ethContext
        .getScheduler()
        .timeout(WaitForPeerTask.create(ethContext, metricsSystem), Duration.ofSeconds(5));
  }

  public abstract boolean shouldContinueDownloading();
}
