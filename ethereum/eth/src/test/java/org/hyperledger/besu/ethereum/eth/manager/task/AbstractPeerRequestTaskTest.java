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
package org.hyperledger.besu.ethereum.eth.manager.task;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestBuilder;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.PendingPeerRequest;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.messages.EthProtocolMessages;
import org.hyperledger.besu.ethereum.eth.sync.ChainHeadTracker;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AbstractPeerRequestTaskTest {

  private EthProtocolManager ethProtocolManager;
  private EthContext ethContext;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @BeforeEach
  public void setup() throws Exception {
    ethProtocolManager = EthProtocolManagerTestBuilder.builder().build();
    ethContext = ethProtocolManager.ethContext();
    final ChainHeadTracker chainHeadTracker = mock(ChainHeadTracker.class);
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(chainHeadTracker.getBestHeaderFromPeer(any()))
        .thenReturn(CompletableFuture.completedFuture(blockHeader));
    ethContext.getEthPeers().setChainHeadTracker(chainHeadTracker);
  }

  @Test
  public void taskShouldTimeoutWhenRequestStuckInPendingQueue() throws Exception {
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final EthPeer ethPeer = peer.getEthPeer();

    // Consume all peer capacity so the request will be queued in pendingRequests
    while (ethPeer.hasAvailableRequestCapacity()) {
      ethPeer.getNodeData(singletonList(Hash.ZERO));
    }
    assertThat(ethPeer.hasAvailableRequestCapacity()).isFalse();

    // Create a task with the busy peer assigned and a short timeout
    final TestPeerRequestTask task = new TestPeerRequestTask(ethContext, metricsSystem);
    task.setTimeout(Duration.ofMillis(100));
    task.assignPeer(ethPeer);

    // Run the task - request will be stuck in pendingRequests since peer is busy
    final CompletableFuture<AbstractPeerTask.PeerTaskResult<String>> result = task.run();

    // The timeout should fire and complete the task exceptionally, even though the request
    // was never actually sent to the peer. Expire any pending timeouts tracked by the
    // deterministic scheduler.
    EthProtocolManagerTestUtil.expirePendingTimeouts(ethProtocolManager);

    // The timeout is registered immediately in executeTask(), so it fires even though
    // the request was never dispatched. The task completes exceptionally with TimeoutException.
    assertThat(result.isDone()).isTrue();
    assertThat(result.isCompletedExceptionally()).isTrue();
  }

  @Test
  public void taskShouldTimeoutWhenRequestSentButNoPeerResponse() throws Exception {
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final EthPeer ethPeer = peer.getEthPeer();

    // Peer has capacity - request will be sent immediately
    assertThat(ethPeer.hasAvailableRequestCapacity()).isTrue();

    final TestPeerRequestTask task = new TestPeerRequestTask(ethContext, metricsSystem);
    task.setTimeout(Duration.ofMillis(100));
    task.assignPeer(ethPeer);

    final CompletableFuture<AbstractPeerTask.PeerTaskResult<String>> result = task.run();

    // Request was sent, so failAfterTimeout was called. Expire the timeout.
    EthProtocolManagerTestUtil.expirePendingTimeouts(ethProtocolManager);

    // This should pass even with the current code - timeout fires because the request was sent
    assertThat(result.isDone()).isTrue();
    assertThat(result.isCompletedExceptionally()).isTrue();
  }

  @Test
  public void taskShouldTimeoutEvenAfterRunningPendingFuturesWhenPeerBusy() throws Exception {
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final EthPeer ethPeer = peer.getEthPeer();

    // Consume all capacity
    while (ethPeer.hasAvailableRequestCapacity()) {
      ethPeer.getNodeData(singletonList(Hash.ZERO));
    }

    final TestPeerRequestTask task = new TestPeerRequestTask(ethContext, metricsSystem);
    task.setTimeout(Duration.ofMillis(100));
    task.assignPeer(ethPeer);

    final CompletableFuture<AbstractPeerTask.PeerTaskResult<String>> result = task.run();

    // Expire all registered timeouts
    EthProtocolManagerTestUtil.expirePendingTimeouts(ethProtocolManager);

    // Run any pending futures
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);

    // The timeout fires even after running pending futures — the fix holds regardless
    // of whether the futures executor is also drained.
    assertThat(result.isDone()).isTrue();
    assertThat(result.isCompletedExceptionally()).isTrue();
  }

  @Test
  public void expiringTimeoutsCompletesTaskWhenRequestStuckInPendingQueue() throws Exception {
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final EthPeer ethPeer = peer.getEthPeer();

    // Consume all capacity
    while (ethPeer.hasAvailableRequestCapacity()) {
      ethPeer.getNodeData(singletonList(Hash.ZERO));
    }

    final TestPeerRequestTask task = new TestPeerRequestTask(ethContext, metricsSystem);
    task.setTimeout(Duration.ofMillis(100));
    task.assignPeer(ethPeer);

    final CompletableFuture<AbstractPeerTask.PeerTaskResult<String>> result = task.run();

    // Expire all pending timeouts - timeout is registered immediately, so this completes the task
    EthProtocolManagerTestUtil.expirePendingTimeouts(ethProtocolManager);

    assertThat(result.isDone()).isTrue();
    assertThat(result.isCompletedExceptionally()).isTrue();
  }

  /**
   * Concrete test implementation of AbstractPeerRequestTask that sends a simple request. The
   * response processing is irrelevant for these timeout tests - we're testing what happens when the
   * request is never sent at all.
   */
  private static class TestPeerRequestTask extends AbstractPeerRequestTask<String> {

    protected TestPeerRequestTask(final EthContext ethContext, final MetricsSystem metricsSystem) {
      super(ethContext, EthProtocol.NAME, EthProtocolMessages.GET_BLOCK_BODIES, metricsSystem);
    }

    @Override
    protected PendingPeerRequest sendRequest() {
      // Use a real PendingPeerRequest through the standard path.
      // This hits EthPeers.executePeerRequest which will queue the request
      // in pendingRequests if the assigned peer has no capacity.
      return sendRequestToPeer(peer -> peer.getNodeData(singletonList(Hash.ZERO)), 0);
    }

    @Override
    protected Optional<String> processResponse(
        final boolean streamClosed, final MessageData message, final EthPeer peer) {
      if (streamClosed) {
        return Optional.of("closed");
      }
      return Optional.empty();
    }
  }
}
