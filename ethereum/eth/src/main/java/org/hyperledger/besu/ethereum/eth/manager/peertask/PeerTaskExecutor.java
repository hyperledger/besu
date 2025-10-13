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
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages the execution of PeerTasks, respecting their PeerTaskRetryBehavior */
public class PeerTaskExecutor {
  private static final Logger LOG = LoggerFactory.getLogger(PeerTaskExecutor.class);

  private final PeerSelector peerSelector;
  private final PeerTaskRequestSender requestSender;

  private final LabelledMetric<OperationTimer> requestTimer;
  private final LabelledMetric<Counter> timeoutCounter;
  private final LabelledMetric<Counter> invalidResponseCounter;
  private final LabelledMetric<Counter> internalExceptionCounter;
  private final LabelledSuppliedMetric inflightRequestGauge;
  private final Map<String, AtomicInteger> inflightRequestCountByClassName;

  public PeerTaskExecutor(
      final PeerSelector peerSelector,
      final PeerTaskRequestSender requestSender,
      final MetricsSystem metricsSystem) {
    this.peerSelector = peerSelector;
    this.requestSender = requestSender;
    requestTimer =
        metricsSystem.createLabelledTimer(
            BesuMetricCategory.PEERS,
            "request_time",
            "Time taken to send a request and receive a response",
            "taskName");
    timeoutCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.PEERS,
            "timeout_total",
            "Counter of the number of timeouts occurred",
            "taskName");
    invalidResponseCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.PEERS,
            "invalid_response_total",
            "Counter of the number of invalid responses received",
            "taskName");
    internalExceptionCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.PEERS,
            "internal_exception_total",
            "Counter of the number of internal exceptions occurred",
            "taskName");
    inflightRequestGauge =
        metricsSystem.createLabelledSuppliedGauge(
            BesuMetricCategory.PEERS,
            "inflight_request_gauge",
            "Gauge of the number of inflight requests",
            "taskName");
    inflightRequestCountByClassName = new ConcurrentHashMap<>();
  }

  public <T> PeerTaskExecutorResult<T> execute(final PeerTask<T> peerTask) {
    PeerTaskExecutorResult<T> executorResult;
    int retriesRemaining = peerTask.getRetriesWithOtherPeer();
    final Collection<EthPeer> usedEthPeers = new HashSet<>();
    do {
      Optional<EthPeer> peer =
          peerSelector.getPeer(
              (candidatePeer) ->
                  peerTask.getPeerRequirementFilter().test(candidatePeer)
                      && !usedEthPeers.contains(candidatePeer));
      if (peer.isEmpty()) {
        executorResult =
            new PeerTaskExecutorResult<>(
                Optional.empty(), PeerTaskExecutorResponseCode.NO_PEER_AVAILABLE, Optional.empty());
        break;
      }
      usedEthPeers.add(peer.get());
      executorResult = executeAgainstPeer(peerTask, peer.get());
    } while (retriesRemaining-- > 0
        && executorResult.responseCode() != PeerTaskExecutorResponseCode.SUCCESS);

    return executorResult;
  }

  public <T> PeerTaskExecutorResult<T> executeAgainstPeer(
      final PeerTask<T> peerTask, final EthPeer peer) {
    String taskClassName = peerTask.getClass().getSimpleName();
    AtomicInteger inflightRequestCountForThisTaskClass =
        inflightRequestCountByClassName.computeIfAbsent(
            taskClassName,
            (k) -> {
              AtomicInteger inflightRequests = new AtomicInteger(0);
              inflightRequestGauge.labels(inflightRequests::get, taskClassName);
              return inflightRequests;
            });
    MessageData requestMessageData = peerTask.getRequestMessage();
    PeerTaskExecutorResult<T> executorResult;
    int retriesRemaining = peerTask.getRetriesWithSamePeer();
    do {
      try {
        T result;
        try (final OperationTimer.TimingContext ignored =
            requestTimer.labels(taskClassName).startTimer()) {
          inflightRequestCountForThisTaskClass.incrementAndGet();

          MessageData responseMessageData =
              requestSender.sendRequest(peerTask.getSubProtocol(), requestMessageData, peer);

          if (responseMessageData == null) {
            throw new InvalidPeerTaskResponseException();
          }

          result = peerTask.processResponse(responseMessageData);
        } finally {
          inflightRequestCountForThisTaskClass.decrementAndGet();
        }

        PeerTaskValidationResponse validationResponse = peerTask.validateResult(result);
        if (validationResponse == PeerTaskValidationResponse.RESULTS_VALID_AND_GOOD) {
          peer.recordUsefulResponse();
          executorResult =
              new PeerTaskExecutorResult<>(
                  Optional.ofNullable(result),
                  PeerTaskExecutorResponseCode.SUCCESS,
                  Optional.of(peer));
          peerTask.postProcessResult(executorResult);
        } else {
          LOG.debug(
              "Invalid response found for {} from peer {}", taskClassName, peer.getLoggableId());
          validationResponse
              .getDisconnectReason()
              .ifPresent((disconnectReason) -> peer.disconnect(disconnectReason));
          executorResult =
              new PeerTaskExecutorResult<>(
                  Optional.ofNullable(result),
                  PeerTaskExecutorResponseCode.INVALID_RESPONSE,
                  Optional.of(peer));
        }

      } catch (PeerNotConnected e) {
        executorResult =
            new PeerTaskExecutorResult<>(
                Optional.empty(),
                PeerTaskExecutorResponseCode.PEER_DISCONNECTED,
                Optional.of(peer));

      } catch (InterruptedException | TimeoutException e) {
        peer.recordRequestTimeout(requestMessageData.getCode());
        timeoutCounter.labels(taskClassName).inc();
        executorResult =
            new PeerTaskExecutorResult<>(
                Optional.empty(), PeerTaskExecutorResponseCode.TIMEOUT, Optional.of(peer));

      } catch (InvalidPeerTaskResponseException e) {
        peer.recordUselessResponse(e.getMessage());
        invalidResponseCounter.labels(taskClassName).inc();
        LOG.debug(
            "Invalid response found for {} from peer {}", taskClassName, peer.getLoggableId(), e);
        executorResult =
            new PeerTaskExecutorResult<>(
                Optional.empty(), PeerTaskExecutorResponseCode.INVALID_RESPONSE, Optional.of(peer));

      } catch (Exception e) {
        internalExceptionCounter.labels(taskClassName).inc();
        LOG.error("Server error found for {} from peer {}", taskClassName, peer.getLoggableId(), e);
        executorResult =
            new PeerTaskExecutorResult<>(
                Optional.empty(),
                PeerTaskExecutorResponseCode.INTERNAL_SERVER_ERROR,
                Optional.of(peer));
      }
    } while (retriesRemaining-- > 0
        && executorResult.responseCode() != PeerTaskExecutorResponseCode.SUCCESS
        && executorResult.responseCode() != PeerTaskExecutorResponseCode.PEER_DISCONNECTED
        && sleepBetweenRetries());

    return executorResult;
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
