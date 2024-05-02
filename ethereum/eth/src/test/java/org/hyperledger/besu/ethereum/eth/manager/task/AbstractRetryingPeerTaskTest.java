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

import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.MaxRetriesReachedException;
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
}
