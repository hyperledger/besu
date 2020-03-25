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
package org.hyperledger.besu.ethereum.eth.sync.beamsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.ExceptionUtils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This task attempts to find a non-controversial launch block by confirming the launch block number
 * with a minimum number of peers. If a particular launch block cannot be confirmed, the launch
 * block number is pushed back and the confirmation process repeats. If a maximum number of retries
 * is reached, the task fails with a {@code FastSyncException} containing {@code
 * BeamSyncError.LAUNCH_BLOCK_HEADER_MISMATCH}.
 *
 * @param <C> The consensus context
 */
public class LaunchBlockRetriever<C> {

  private static final Logger LOG = LogManager.getLogger();
  public static final int MAX_QUERY_RETRIES_PER_PEER = 3;
  private static final int DEFAULT_MAX_LAUNCH_BLOCK_RESETS = 5;

  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private final ProtocolSchedule<C> protocolSchedule;

  // The number of peers we need to query to confirm our launch block
  private final int peersToQuery;
  // The max times to push the launch block number back when peers can't agree on a launch
  private final int maxLaunchBlockResets;
  // How far to push back the launch block when we retry on launch disagreement
  private final long launchBlockNumberResetDelta;
  // The current launch block number, gets pushed back if peers disagree on the launch block
  AtomicLong launchBlockNumber;

  private final CompletableFuture<BeamSyncState> result = new CompletableFuture<>();
  private final Map<Long, LaunchBlockConfirmer<C>> confirmationTasks = new ConcurrentHashMap<>();

  private final AtomicBoolean isStarted = new AtomicBoolean(false);

  LaunchBlockRetriever(
      final ProtocolSchedule<C> protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final long launchBlockNumber,
      final int peersToQuery,
      final long launchBlockNumberResetDelta,
      final int maxLaunchBlockResets) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;

    this.launchBlockNumber = new AtomicLong(launchBlockNumber);
    this.peersToQuery = peersToQuery;
    this.launchBlockNumberResetDelta = launchBlockNumberResetDelta;
    this.maxLaunchBlockResets = maxLaunchBlockResets;
  }

  public LaunchBlockRetriever(
      final ProtocolSchedule<C> protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final long launchBlockNumber,
      final int peersToQuery,
      final long launchBlockNumberResetDelta) {
    this(
        protocolSchedule,
        ethContext,
        metricsSystem,
        launchBlockNumber,
        peersToQuery,
        launchBlockNumberResetDelta,
        DEFAULT_MAX_LAUNCH_BLOCK_RESETS);
  }

  public CompletableFuture<BeamSyncState> downloadLaunchBlockHeader() {
    if (isStarted.compareAndSet(false, true)) {
      LOG.info("Retrieve a launch block that can be confirmed by at least {} peers.", peersToQuery);
      confirmBlock(launchBlockNumber.get());
    }

    return result;
  }

  private void confirmBlock(final long blockNumber) {
    final LaunchBlockConfirmer<C> launchBlockConfirmationTask =
        new LaunchBlockConfirmer<>(
            protocolSchedule,
            ethContext,
            metricsSystem,
            launchBlockNumber.get(),
            peersToQuery,
            MAX_QUERY_RETRIES_PER_PEER);
    final LaunchBlockConfirmer<C> preexistingTask =
        confirmationTasks.putIfAbsent(blockNumber, launchBlockConfirmationTask);
    if (preexistingTask != null) {
      // We already set up a task to confirm this block
      return;
    }

    launchBlockConfirmationTask.confirmLaunchBlock().whenComplete(this::handleConfirmationResult);
  }

  private void handleConfirmationResult(final BeamSyncState beamSyncState, final Throwable error) {
    if (error != null) {
      final Throwable rootCause = ExceptionUtils.rootCause(error);
      if (rootCause instanceof LaunchBlockConfirmer.ContestedLaunchBlockException) {
        final long blockNumber =
            ((LaunchBlockConfirmer.ContestedLaunchBlockException) error).getBlockNumber();
        handleContestedLaunchBlock(blockNumber);
      } else {
        LOG.error("Encountered error while requesting launch block header", error);
        result.completeExceptionally(error);
      }
      return;
    }

    result.complete(beamSyncState);
  }

  private void handleContestedLaunchBlock(final long contestedBlockNumber) {
    if (launchBlockNumber.compareAndSet(
        contestedBlockNumber, contestedBlockNumber - launchBlockNumberResetDelta)) {
      LOG.info("Received conflicting launch blocks for {}.", contestedBlockNumber);

      final int retryCount = confirmationTasks.size();
      if (retryCount > maxLaunchBlockResets
          || launchBlockNumber.get() <= BlockHeader.GENESIS_BLOCK_NUMBER) {
        LOG.info("Max retries reached, cancel launch block download.");
        // Launch block selection has failed
        result.completeExceptionally(
            new BeamSyncException(BeamSyncError.LAUNCH_BLOCK_HEADER_MISMATCH));
        return;
      } else {
        LOG.info("Move launch block back to {} and retry.", launchBlockNumber);
      }

      confirmBlock(launchBlockNumber.get());
    }
  }
}
