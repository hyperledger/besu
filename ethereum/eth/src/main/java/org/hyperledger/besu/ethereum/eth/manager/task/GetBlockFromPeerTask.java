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
package org.hyperledger.besu.ethereum.eth.manager.task;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.IncompleteResultsException;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.PeerDisconnectedException;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask.Direction;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Downloads a block from a peer. Will complete exceptionally if block cannot be downloaded. */
public class GetBlockFromPeerTask extends AbstractPeerTask<Block> {
  private static final Logger LOG = LoggerFactory.getLogger(GetBlockFromPeerTask.class);

  private final ProtocolSchedule protocolSchedule;
  private final SynchronizerConfiguration synchronizerConfiguration;
  private final Optional<Hash> hash;
  private final long blockNumber;
  private final MetricsSystem metricsSystem;

  protected GetBlockFromPeerTask(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final SynchronizerConfiguration synchronizerConfiguration,
      final Optional<Hash> hash,
      final long blockNumber,
      final MetricsSystem metricsSystem) {
    super(ethContext, metricsSystem);
    this.synchronizerConfiguration = synchronizerConfiguration;
    this.blockNumber = blockNumber;
    this.metricsSystem = metricsSystem;
    this.protocolSchedule = protocolSchedule;
    this.hash = hash;
  }

  public static GetBlockFromPeerTask create(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final SynchronizerConfiguration synchronizerConfiguration,
      final Optional<Hash> hash,
      final long blockNumber,
      final MetricsSystem metricsSystem) {
    return new GetBlockFromPeerTask(
        protocolSchedule, ethContext, synchronizerConfiguration, hash, blockNumber, metricsSystem);
  }

  @Override
  protected void executeTask() {
    final String blockIdentifier = blockNumber + " (" + hash + ")";
    LOG.debug(
        "Downloading block {} from peer {}.",
        blockIdentifier,
        assignedPeer.map(EthPeer::toString).orElse("<any>"));
    if (synchronizerConfiguration.isPeerTaskSystemEnabled()) {
      ethContext
          .getScheduler()
          .scheduleServiceTask(
              () -> {
                downloadHeaderUsingPeerTaskSystem()
                    .thenCompose(this::completeBlock)
                    .whenComplete((r, t) -> completeTask(r, t, blockIdentifier));
              });
    } else {
      downloadHeader()
          .thenCompose(
              (peerTaskResult) -> CompletableFuture.completedFuture(peerTaskResult.getResult()))
          .thenCompose(this::completeBlock)
          .whenComplete((r, t) -> completeTask(r, t, blockIdentifier));
    }
  }

  private CompletableFuture<PeerTaskResult<List<BlockHeader>>> downloadHeader() {
    return executeSubTask(
        () -> {
          final AbstractGetHeadersFromPeerTask task;
          task =
              hash.map(
                      value ->
                          GetHeadersFromPeerByHashTask.forSingleHash(
                              protocolSchedule, ethContext, value, blockNumber, metricsSystem))
                  .orElseGet(
                      () ->
                          GetHeadersFromPeerByNumberTask.forSingleNumber(
                              protocolSchedule, ethContext, blockNumber, metricsSystem));
          assignedPeer.ifPresent(task::assignPeer);
          return task.run();
        });
  }

  private CompletableFuture<List<BlockHeader>> downloadHeaderUsingPeerTaskSystem() {
    GetHeadersFromPeerTask task =
        hash.map(
                (h) ->
                    new GetHeadersFromPeerTask(
                        h, blockNumber, 1, 0, Direction.FORWARD, protocolSchedule))
            .orElseGet(
                () ->
                    new GetHeadersFromPeerTask(
                        blockNumber, 1, 0, Direction.FORWARD, protocolSchedule));
    PeerTaskExecutorResult<List<BlockHeader>> taskResult;
    if (assignedPeer.isPresent()) {
      taskResult = ethContext.getPeerTaskExecutor().executeAgainstPeer(task, assignedPeer.get());
    } else {
      taskResult = ethContext.getPeerTaskExecutor().execute(task);
    }

    CompletableFuture<List<BlockHeader>> returnValue = new CompletableFuture<List<BlockHeader>>();
    if (taskResult.responseCode() == PeerTaskExecutorResponseCode.PEER_DISCONNECTED
        && taskResult.ethPeer().isPresent()) {
      returnValue.completeExceptionally(new PeerDisconnectedException(taskResult.ethPeer().get()));
    } else if (taskResult.responseCode() != PeerTaskExecutorResponseCode.SUCCESS
        || taskResult.result().isEmpty()) {
      String logMessage =
          "Peer "
              + taskResult.ethPeer().map(EthPeer::getLoggableId).orElse("UNKNOWN")
              + " failed to successfully return requested block headers. Response code was "
              + taskResult.responseCode();
      returnValue.completeExceptionally(new RuntimeException(logMessage));
      LOG.debug(logMessage);
    } else {
      returnValue.complete(taskResult.result().get());
    }
    return returnValue;
  }

  private CompletableFuture<PeerTaskResult<List<Block>>> completeBlock(
      final List<BlockHeader> headers) {
    if (headers.isEmpty()) {
      LOG.debug("header result is empty.");
      return CompletableFuture.failedFuture(new IncompleteResultsException());
    }

    return executeSubTask(
        () -> {
          final GetBodiesFromPeerTask task =
              GetBodiesFromPeerTask.forHeaders(
                  protocolSchedule, ethContext, headers, metricsSystem);
          assignedPeer.ifPresent(task::assignPeer);
          return task.run();
        });
  }

  private void completeTask(
      final PeerTaskResult<List<Block>> blockTaskResult,
      final Throwable throwable,
      final String blockIdentifier) {
    if (throwable != null) {
      LOG.debug(
          "Failed to download block {} from peer {} with message '{}' and cause '{}'",
          blockIdentifier,
          assignedPeer.map(EthPeer::toString).orElse("<any>"),
          throwable.getMessage(),
          throwable.getCause());
      result.completeExceptionally(throwable);
    } else if (blockTaskResult.getResult().isEmpty()) {
      blockTaskResult.getPeer().recordUselessResponse("Download block returned an empty result");
      LOG.debug(
          "Failed to download block {} from peer {} with empty result.",
          blockIdentifier,
          blockTaskResult.getPeer());
      result.completeExceptionally(new IncompleteResultsException());
    } else {
      LOG.debug(
          "Successfully downloaded block {} from peer {}.",
          blockIdentifier,
          blockTaskResult.getPeer());
      result.complete(
          new PeerTaskResult<>(blockTaskResult.getPeer(), blockTaskResult.getResult().get(0)));
    }
  }
}
