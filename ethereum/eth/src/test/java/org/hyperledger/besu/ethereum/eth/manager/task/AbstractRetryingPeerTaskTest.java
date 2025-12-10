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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeerImmutableAttributes;
import org.hyperledger.besu.ethereum.eth.manager.PeerReputation;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.MaxRetriesReachedException;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AbstractRetryingPeerTaskTest {

  @Mock EthContext ethContext;
  MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @Test
  public void shouldSuccessAtFirstTryIfNoTaskFailures()
      throws InterruptedException, ExecutionException {
    final int maxRetries = 2;
    TaskThatFailsSometimes task = new TaskThatFailsSometimes(0, maxRetries);
    CompletableFuture<Boolean> result = task.run();
    assertThat(result.get()).isTrue();
    assertThat(task.executions).isEqualTo(1);
  }

  @Test
  public void shouldSuccessIfTaskFailOnlyOnce() throws InterruptedException, ExecutionException {
    final int maxRetries = 2;
    TaskThatFailsSometimes task = new TaskThatFailsSometimes(1, maxRetries);
    CompletableFuture<Boolean> result = task.run();
    assertThat(result.get()).isTrue();
    assertThat(task.executions).isEqualTo(2);
  }

  @Test
  public void shouldFailAfterMaxRetriesExecutions() throws InterruptedException {
    final int maxRetries = 2;
    TaskThatFailsSometimes task = new TaskThatFailsSometimes(maxRetries, maxRetries);
    CompletableFuture<Boolean> result = task.run();
    assertThat(result.isCompletedExceptionally()).isTrue();
    assertThat(task.executions).isEqualTo(maxRetries);
    try {
      result.get();
    } catch (ExecutionException ee) {
      assertThat(ee).hasCauseExactlyInstanceOf(MaxRetriesReachedException.class);
      return;
    }
    failBecauseExceptionWasNotThrown(MaxRetriesReachedException.class);
  }

  @Test
  public void shouldAssignSuitablePeer() {
    final int maxRetries = 2;
    TaskThatAcceptsAllPeers task = new TaskThatAcceptsAllPeers(0, maxRetries);
    EthPeer mockPeer = createMockPeerWithHeight(100L);

    boolean assigned = task.assignPeer(mockPeer);

    assertThat(assigned).isTrue();
    assertThat(task.getAssignedPeer()).isPresent();
    assertThat(task.getAssignedPeer().get()).isEqualTo(mockPeer);
  }

  @Test
  public void shouldRejectUnsuitablePeer() {
    final int maxRetries = 2;
    TaskWithPeerFilter task = new TaskWithPeerFilter(0, maxRetries, 100);

    EthPeer unsuitablePeer = createMockPeerWithHeight(50L);

    boolean assigned = task.assignPeer(unsuitablePeer);

    assertThat(assigned).isFalse();
    assertThat(task.getAssignedPeer()).isEmpty();
  }

  @Test
  public void shouldAcceptSuitablePeerBasedOnCustomFilter() {
    final int maxRetries = 2;
    TaskWithPeerFilter task = new TaskWithPeerFilter(0, maxRetries, 100);

    EthPeer suitablePeer = createMockPeerWithHeight(150L);

    boolean assigned = task.assignPeer(suitablePeer);

    assertThat(assigned).isTrue();
    assertThat(task.getAssignedPeer()).isPresent();
    assertThat(task.getAssignedPeer().get()).isEqualTo(suitablePeer);
  }

  private EthPeer createMockPeerWithHeight(final long estimatedHeight) {
    EthPeer peer = mock(EthPeer.class);
    ChainState chainState = mock(ChainState.class);
    PeerReputation reputation = mock(PeerReputation.class);
    PeerConnection connection = mock(PeerConnection.class);

    when(peer.chainState()).thenReturn(chainState);
    when(chainState.hasEstimatedHeight()).thenReturn(true);
    when(chainState.getEstimatedHeight()).thenReturn(estimatedHeight);
    when(chainState.getEstimatedTotalDifficulty())
        .thenReturn(org.hyperledger.besu.ethereum.core.Difficulty.ZERO);
    when(peer.getReputation()).thenReturn(reputation);
    when(reputation.getScore()).thenReturn(0);
    when(peer.outstandingRequests()).thenReturn(0);
    when(peer.getLastRequestTimestamp()).thenReturn(0L);
    when(peer.isDisconnected()).thenReturn(false);
    when(peer.isFullyValidated()).thenReturn(true);
    when(peer.isServingSnap()).thenReturn(false);
    when(peer.hasAvailableRequestCapacity()).thenReturn(true);
    when(peer.getConnection()).thenReturn(connection);
    when(connection.inboundInitiated()).thenReturn(false);
    return peer;
  }

  @Test
  public void shouldFailExactlyAtMaxRetries() throws InterruptedException {
    final int maxRetries = 3;
    // Task that fails exactly maxRetries times (boundary condition)
    TaskThatFailsSometimes task = new TaskThatFailsSometimes(maxRetries, maxRetries);
    CompletableFuture<Boolean> result = task.run();

    assertThat(result.isCompletedExceptionally()).isTrue();
    // Should execute exactly maxRetries times (not maxRetries + 1)
    assertThat(task.executions).isEqualTo(maxRetries);
    try {
      result.get();
    } catch (ExecutionException ee) {
      assertThat(ee).hasCauseExactlyInstanceOf(MaxRetriesReachedException.class);
      return;
    }
    failBecauseExceptionWasNotThrown(MaxRetriesReachedException.class);
  }

  @Test
  public void shouldSucceedOnLastRetry() throws InterruptedException, ExecutionException {
    final int maxRetries = 3;
    // Task that fails maxRetries - 1 times, succeeds on last attempt
    TaskThatFailsSometimes task = new TaskThatFailsSometimes(maxRetries - 1, maxRetries);
    CompletableFuture<Boolean> result = task.run();

    assertThat(result.get()).isTrue();
    assertThat(task.executions).isEqualTo(maxRetries);
  }

  @Test
  public void shouldResetRetryCounterOnPartialSuccess()
      throws InterruptedException, ExecutionException {
    final int maxRetries = 2;
    TaskWithPartialSuccess task = new TaskWithPartialSuccess(maxRetries);
    CompletableFuture<String> result = task.run();

    assertThat(result.get()).isEqualTo("success");
    // Should execute multiple times: fail, partial, fail, partial, success
    assertThat(task.executions).isGreaterThan(2);
  }

  @Test
  public void shouldGetAssignedPeerWhenNotAssigned() {
    final int maxRetries = 2;
    TaskThatFailsSometimes task = new TaskThatFailsSometimes(0, maxRetries);

    assertThat(task.getAssignedPeer()).isEmpty();
  }

  @Test
  public void shouldProvideAccessToProtectedGetters() {
    final int maxRetries = 2;
    TaskWithProtectedGetters task = new TaskWithProtectedGetters(0, maxRetries);

    assertThat(task.accessEthContext()).isEqualTo(ethContext);
    assertThat(task.accessMetricsSystem()).isEqualTo(metricsSystem);
  }

  private class TaskThatFailsSometimes extends AbstractRetryingPeerTask<Boolean> {
    final int initialFailures;
    int executions = 0;
    int failures = 0;

    protected TaskThatFailsSometimes(final int initialFailures, final int maxRetries) {
      super(ethContext, maxRetries, Objects::isNull, metricsSystem);
      this.initialFailures = initialFailures;
    }

    @Override
    protected CompletableFuture<Boolean> executePeerTask(final Optional<EthPeer> assignedPeer) {
      executions++;
      if (failures < initialFailures) {
        failures++;
        return CompletableFuture.completedFuture(null);
      } else {
        result.complete(Boolean.TRUE);
        return CompletableFuture.completedFuture(Boolean.TRUE);
      }
    }
  }

  private class TaskThatAcceptsAllPeers extends AbstractRetryingPeerTask<Boolean> {
    final int initialFailures;
    int failures = 0;

    protected TaskThatAcceptsAllPeers(final int initialFailures, final int maxRetries) {
      super(ethContext, maxRetries, Objects::isNull, metricsSystem);
      this.initialFailures = initialFailures;
    }

    @Override
    protected CompletableFuture<Boolean> executePeerTask(final Optional<EthPeer> assignedPeer) {
      if (failures < initialFailures) {
        failures++;
        return CompletableFuture.completedFuture(null);
      } else {
        result.complete(Boolean.TRUE);
        return CompletableFuture.completedFuture(Boolean.TRUE);
      }
    }
  }

  private class TaskWithPeerFilter extends AbstractRetryingPeerTask<Boolean> {
    final int initialFailures;
    final long minChainHeight;
    int failures = 0;

    protected TaskWithPeerFilter(
        final int initialFailures, final int maxRetries, final long minChainHeight) {
      super(ethContext, maxRetries, Objects::isNull, metricsSystem);
      this.initialFailures = initialFailures;
      this.minChainHeight = minChainHeight;
    }

    @Override
    protected boolean isSuitablePeer(final EthPeerImmutableAttributes peer) {
      return peer.hasEstimatedChainHeight() && peer.estimatedChainHeight() >= minChainHeight;
    }

    @Override
    protected CompletableFuture<Boolean> executePeerTask(final Optional<EthPeer> assignedPeer) {
      if (failures < initialFailures) {
        failures++;
        return CompletableFuture.completedFuture(null);
      } else {
        result.complete(Boolean.TRUE);
        return CompletableFuture.completedFuture(Boolean.TRUE);
      }
    }
  }

  private class TaskWithPartialSuccess extends AbstractRetryingPeerTask<String> {
    @SuppressWarnings("UnusedVariable")
    int executions = 0;

    int cycle = 0;

    protected TaskWithPartialSuccess(final int maxRetries) {
      super(ethContext, maxRetries, Objects::isNull, metricsSystem);
    }

    @Override
    protected CompletableFuture<String> executePeerTask(final Optional<EthPeer> assignedPeer) {
      executions++;
      cycle++;

      // Pattern: fail, partial success, fail, partial success, full success
      if (cycle % 5 == 0) {
        result.complete("success");
        return CompletableFuture.completedFuture("success");
      } else if (cycle % 2 == 0) {
        // Partial success (non-null but not complete)
        return CompletableFuture.completedFuture("partial");
      } else {
        // Null response (triggers retry)
        return CompletableFuture.completedFuture(null);
      }
    }
  }

  private class TaskWithProtectedGetters extends AbstractRetryingPeerTask<Boolean> {
    final int initialFailures;
    int failures = 0;

    protected TaskWithProtectedGetters(final int initialFailures, final int maxRetries) {
      super(ethContext, maxRetries, Objects::isNull, metricsSystem);
      this.initialFailures = initialFailures;
    }

    public EthContext accessEthContext() {
      return getEthContext();
    }

    public MetricsSystem accessMetricsSystem() {
      return getMetricsSystem();
    }

    @Override
    protected CompletableFuture<Boolean> executePeerTask(final Optional<EthPeer> assignedPeer) {
      if (failures < initialFailures) {
        failures++;
        return CompletableFuture.completedFuture(null);
      } else {
        result.complete(Boolean.TRUE);
        return CompletableFuture.completedFuture(Boolean.TRUE);
      }
    }
  }
}
