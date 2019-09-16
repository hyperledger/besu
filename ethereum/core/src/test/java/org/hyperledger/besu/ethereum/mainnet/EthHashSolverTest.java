/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import org.junit.Test;

public class EthHashSolverTest {

  @Test
  public void emptyHashRateAndWorkDefinitionIsReportedPriorToSolverStarting() {
    final List<Long> noncesToTry = Arrays.asList(1L, 1L, 1L, 1L, 1L, 1L, 0L);
    final EthHashSolver solver = new EthHashSolver(noncesToTry, new EthHasher.Light());

    assertThat(solver.hashesPerSecond()).isEqualTo(Optional.empty());
    assertThat(solver.getWorkDefinition()).isEqualTo(Optional.empty());
  }

  @Test
  public void hashRateIsProducedSuccessfully() throws InterruptedException, ExecutionException {
    final List<Long> noncesToTry = Arrays.asList(1L, 1L, 1L, 1L, 1L, 1L, 0L);

    final EthHasher hasher = mock(EthHasher.class);
    doAnswer(
            invocation -> {
              final Object[] args = invocation.getArguments();
              final byte[] headerHash = ((byte[]) args[0]);
              final long nonce = ((long) args[1]);
              headerHash[32] = (byte) (nonce & 0xFF);
              return null;
            })
        .when(hasher)
        .hash(any(), anyLong(), anyLong(), any());

    final EthHashSolver solver = new EthHashSolver(noncesToTry, hasher);

    final Stopwatch operationTimer = Stopwatch.createStarted();
    final EthHashSolverInputs inputs = new EthHashSolverInputs(UInt256.ONE, new byte[0], 5);
    solver.solveFor(EthHashSolver.EthHashSolverJob.createFromInputs(inputs));
    final double runtimeSeconds = operationTimer.elapsed(TimeUnit.NANOSECONDS) / 1e9;
    final long worstCaseHashesPerSecond = (long) (noncesToTry.size() / runtimeSeconds);

    final Optional<Long> hashesPerSecond = solver.hashesPerSecond();
    assertThat(hashesPerSecond.isPresent()).isTrue();
    assertThat(hashesPerSecond.get()).isGreaterThanOrEqualTo(worstCaseHashesPerSecond);

    assertThat(solver.getWorkDefinition().isPresent()).isTrue();
    assertThat(solver.getWorkDefinition().equals(Optional.of(inputs))).isTrue();
  }

  @Test
  public void ifInvokedTwiceProducesCorrectAnswerForSecondInvocation()
      throws InterruptedException, ExecutionException {

    final EthHashSolverInputs firstInputs =
        new EthHashSolverInputs(
            UInt256.fromHexString(
                "0x0083126e978d4fdf3b645a1cac083126e978d4fdf3b645a1cac083126e978d4f"),
            new byte[] {
              15, -114, -104, 87, -95, -36, -17, 120, 52, 1, 124, 61, -6, -66, 78, -27, -57, 118,
              -18, -64, -103, -91, -74, -121, 42, 91, -14, -98, 101, 86, -43, -51
            },
            468);

    final EthHashSolution expectedFirstOutput =
        new EthHashSolution(
            -6506032554016940193L,
            Hash.fromHexString(
                "0xc5e3c33c86d64d0641dd3c86e8ce4628fe0aac0ef7b4c087c5fcaa45d5046d90"),
            firstInputs.getPrePowHash());

    final EthHashSolverInputs secondInputs =
        new EthHashSolverInputs(
            UInt256.fromHexString(
                "0x0083126e978d4fdf3b645a1cac083126e978d4fdf3b645a1cac083126e978d4f"),
            new byte[] {
              -62, 121, -81, -31, 55, -38, -68, 102, -32, 95, -94, -83, -3, -48, -122, -68, 14,
              -125, -83, 84, -55, -23, -123, -57, -34, 25, -89, 23, 64, -9, -114, -3,
            },
            1);

    final EthHashSolution expectedSecondOutput =
        new EthHashSolution(
            8855952212886464488L,
            Hash.fromHexString(
                "0x2adb0f375dd2d528689cb9e00473c3c9692737109d547130feafbefb2c6c5244"),
            firstInputs.getPrePowHash());

    // Nonces need to have a 0L inserted, as it is a "wasted" nonce in the solver.
    final EthHashSolver solver =
        new EthHashSolver(
            Lists.newArrayList(expectedFirstOutput.getNonce(), 0L, expectedSecondOutput.getNonce()),
            new EthHasher.Light());

    EthHashSolution soln =
        solver.solveFor(EthHashSolver.EthHashSolverJob.createFromInputs(firstInputs));
    assertThat(soln.getMixHash()).isEqualTo(expectedFirstOutput.getMixHash());

    soln = solver.solveFor(EthHashSolver.EthHashSolverJob.createFromInputs(secondInputs));
    assertThat(soln.getMixHash()).isEqualTo(expectedSecondOutput.getMixHash());
  }
}
