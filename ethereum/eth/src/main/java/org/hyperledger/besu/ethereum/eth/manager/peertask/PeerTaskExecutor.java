/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.manager.peertask;

import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.NoAvailablePeersException;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** Manages the execution of PeerTasks, respecting their PeerTaskRetryBehavior */
public class PeerTaskExecutor {

  public static final int RETRIES_WITH_SAME_PEER = 3;
  public static final int RETRIES_WITH_OTHER_PEER = 3;
  public static final int NO_RETRIES = 1;
  private final PeerSelector peerSelector;
  private final PeerTaskRequestSender requestSender;
  private final EthScheduler ethScheduler;

  private final LabelledMetric<OperationTimer> requestTimer;

  public PeerTaskExecutor(
      final PeerSelector peerSelector,
      final PeerTaskRequestSender requestSender,
      final EthScheduler ethScheduler,
      final MetricsSystem metricsSystem) {
    this.peerSelector = peerSelector;
    this.requestSender = requestSender;
    this.ethScheduler = ethScheduler;
    requestTimer =
        metricsSystem.createLabelledTimer(
            BesuMetricCategory.PEERS,
            "PeerTaskExecutor:RequestTime",
            "Time taken to send a request and receive a response",
            "className");
  }

  public <T> PeerTaskExecutorResult<T> execute(final PeerTask<T> peerTask) {
    PeerTaskExecutorResult<T> executorResult;
    int triesRemaining =
        peerTask.getPeerTaskBehaviors().contains(PeerTaskRetryBehavior.RETRY_WITH_OTHER_PEERS)
            ? RETRIES_WITH_OTHER_PEER
            : NO_RETRIES;
    final Collection<EthPeer> usedEthPeers = new HashSet<>();
    do {
      EthPeer peer;
      try {
        peer =
            peerSelector.getPeer(
                (candidatePeer) ->
                    peerTask.getPeerRequirementFilter().test(candidatePeer)
                        && !usedEthPeers.contains(candidatePeer));
        usedEthPeers.add(peer);
        executorResult = executeAgainstPeer(peerTask, peer);
      } catch (NoAvailablePeersException e) {
        executorResult =
            new PeerTaskExecutorResult<>(
                Optional.empty(), PeerTaskExecutorResponseCode.NO_PEER_AVAILABLE);
      }
    } while (--triesRemaining > 0
        && executorResult.responseCode() != PeerTaskExecutorResponseCode.SUCCESS);

    return executorResult;
  }

  public <T> CompletableFuture<PeerTaskExecutorResult<T>> executeAsync(final PeerTask<T> peerTask) {
    return ethScheduler.scheduleSyncWorkerTask(
        () -> CompletableFuture.completedFuture(execute(peerTask)));
  }

  public <T> PeerTaskExecutorResult<T> executeAgainstPeer(
      final PeerTask<T> peerTask, final EthPeer peer) {
    MessageData requestMessageData = peerTask.getRequestMessage();
    PeerTaskExecutorResult<T> executorResult;
    int triesRemaining =
        peerTask.getPeerTaskBehaviors().contains(PeerTaskRetryBehavior.RETRY_WITH_SAME_PEER)
            ? RETRIES_WITH_SAME_PEER
            : NO_RETRIES;
    do {
      try {
        T result;
        try (final OperationTimer.TimingContext ignored =
            requestTimer.labels(peerTask.getClass().getSimpleName()).startTimer()) {
          MessageData responseMessageData =
              requestSender.sendRequest(peerTask.getSubProtocol(), requestMessageData, peer);

          result = peerTask.parseResponse(responseMessageData);
        }
        peer.recordUsefulResponse();
        executorResult =
            new PeerTaskExecutorResult<>(
                Optional.ofNullable(result), PeerTaskExecutorResponseCode.SUCCESS);

      } catch (PeerConnection.PeerNotConnected e) {
        executorResult =
            new PeerTaskExecutorResult<>(
                Optional.empty(), PeerTaskExecutorResponseCode.PEER_DISCONNECTED);

      } catch (InterruptedException | TimeoutException e) {
        peer.recordRequestTimeout(requestMessageData.getCode());
        executorResult =
            new PeerTaskExecutorResult<>(Optional.empty(), PeerTaskExecutorResponseCode.TIMEOUT);

      } catch (InvalidPeerTaskResponseException e) {
        peer.recordUselessResponse(e.getMessage());
        executorResult =
            new PeerTaskExecutorResult<>(
                Optional.empty(), PeerTaskExecutorResponseCode.INVALID_RESPONSE);

      } catch (ExecutionException e) {
        executorResult =
            new PeerTaskExecutorResult<>(
                Optional.empty(), PeerTaskExecutorResponseCode.INTERNAL_SERVER_ERROR);
      }
    } while (--triesRemaining > 0
        && executorResult.responseCode() != PeerTaskExecutorResponseCode.SUCCESS
        && executorResult.responseCode() != PeerTaskExecutorResponseCode.PEER_DISCONNECTED
        && sleepBetweenRetries());

    return executorResult;
  }

  public <T> CompletableFuture<PeerTaskExecutorResult<T>> executeAgainstPeerAsync(
      final PeerTask<T> peerTask, final EthPeer peer) {
    return ethScheduler.scheduleSyncWorkerTask(
        () -> CompletableFuture.completedFuture(executeAgainstPeer(peerTask, peer)));
  }

  private boolean sleepBetweenRetries() {
    try {
      // sleep for 1 second to match implemented wait between retries in AbstractRetryingPeerTask
      Thread.sleep(1000);
      return true;
    } catch (InterruptedException e) {
      return false;
    }
  }
}
