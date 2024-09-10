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
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages the execution of PeerTasks, respecting their PeerTaskBehavior */
public class PeerTaskExecutor {
  private static final Logger LOG = LoggerFactory.getLogger(PeerTaskExecutor.class);
  private static final long[] WAIT_TIME_BEFORE_RETRY = {0, 20000, 5000};

  private final PeerManager peerManager;
  private final RequestSender requestSender;

  public PeerTaskExecutor(final PeerManager peerManager, final RequestSender requestSender) {
    this.peerManager = peerManager;
    this.requestSender = requestSender;
  }

  public <T> PeerTaskExecutorResult<T> execute(final PeerTask<T> peerTask) {
    PeerTaskExecutorResult<T> executorResult;
    int triesRemaining =
        peerTask.getPeerTaskBehaviors().contains(PeerTaskBehavior.RETRY_WITH_OTHER_PEERS) ? 3 : 1;
    final Collection<EthPeer> usedEthPeers = new ArrayList<>();
    do {
      EthPeer peer;
      try {
        peer =
            peerManager.getPeer(
                (candidatePeer) ->
                    isPeerUnused(candidatePeer, usedEthPeers)
                        && isPeerHeightHighEnough(candidatePeer, peerTask.getRequiredBlockNumber())
                        && isPeerProtocolSuitable(candidatePeer, peerTask.getSubProtocol()));
        usedEthPeers.add(peer);
        executorResult = executeAgainstPeer(peerTask, peer);
      } catch (NoAvailablePeerException e) {
        LOG.info("NoAvailablePeerException exception");
        executorResult =
            new PeerTaskExecutorResult<>(null, PeerTaskExecutorResponseCode.NO_PEER_AVAILABLE);
      }
    } while (--triesRemaining > 0
        && executorResult.getResponseCode() != PeerTaskExecutorResponseCode.SUCCESS);

    LOG.info("Finishing execution of task with response code " + executorResult.getResponseCode());
    return executorResult;
  }

  public <T> Future<PeerTaskExecutorResult<T>> executeAsync(final PeerTask<T> peerTask) {
    return CompletableFuture.supplyAsync(() -> execute(peerTask));
  }

  public <T> PeerTaskExecutorResult<T> executeAgainstPeer(
      final PeerTask<T> peerTask, final EthPeer peer) {
    MessageData requestMessageData = peerTask.getRequestMessage();
    PeerTaskExecutorResult<T> executorResult;
    int triesRemaining =
        peerTask.getPeerTaskBehaviors().contains(PeerTaskBehavior.RETRY_WITH_SAME_PEER) ? 3 : 1;
    do {
      try {
        MessageData responseMessageData =
            requestSender.sendRequest(peerTask.getSubProtocol(), requestMessageData, peer);
        T result = peerTask.parseResponse(responseMessageData);
        executorResult = new PeerTaskExecutorResult<>(result, PeerTaskExecutorResponseCode.SUCCESS);
      } catch (PeerConnection.PeerNotConnected e) {
        LOG.info("PeerConnection.PeerNotConnected exception");
        executorResult =
            new PeerTaskExecutorResult<>(null, PeerTaskExecutorResponseCode.PEER_DISCONNECTED);
      } catch (InterruptedException | TimeoutException e) {
        LOG.info("InterruptedException | TimeoutException exception");
        executorResult = new PeerTaskExecutorResult<>(null, PeerTaskExecutorResponseCode.TIMEOUT);
      } catch (Exception e) {
        LOG.info("Other Exception", e);
        executorResult =
            new PeerTaskExecutorResult<>(null, PeerTaskExecutorResponseCode.INTERNAL_SERVER_ERROR);
      }
    } while (--triesRemaining > 0
        && executorResult.getResponseCode() != PeerTaskExecutorResponseCode.SUCCESS
        && sleepBetweenRetries(WAIT_TIME_BEFORE_RETRY[triesRemaining]));

    return executorResult;
  }

  public <T> Future<PeerTaskExecutorResult<T>> executeAgainstPeerAsync(
      final PeerTask<T> peerTask, final EthPeer peer) {
    return CompletableFuture.supplyAsync(() -> executeAgainstPeer(peerTask, peer));
  }

  private boolean sleepBetweenRetries(final long sleepTime) {
    try {
      Thread.sleep(sleepTime);
      return true;
    } catch (InterruptedException e) {
      return false;
    }
  }

  private static boolean isPeerUnused(
      final EthPeer ethPeer, final Collection<EthPeer> usedEthPeers) {
    return !usedEthPeers.contains(ethPeer);
  }

  private static boolean isPeerHeightHighEnough(final EthPeer ethPeer, final long requiredHeight) {
    return ethPeer.chainState().getEstimatedHeight() >= requiredHeight;
  }

  private static boolean isPeerProtocolSuitable(final EthPeer ethPeer, final String protocol) {
    return ethPeer.getProtocolName().equals(protocol);
  }
}
