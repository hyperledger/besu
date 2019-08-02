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
package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;

public class EthHashSolver {

  public static class EthHashSolverJob {

    private final EthHashSolverInputs inputs;
    private final CompletableFuture<EthHashSolution> nonceFuture;

    EthHashSolverJob(
        final EthHashSolverInputs inputs, final CompletableFuture<EthHashSolution> nonceFuture) {
      this.inputs = inputs;
      this.nonceFuture = nonceFuture;
    }

    public static EthHashSolverJob createFromInputs(final EthHashSolverInputs inputs) {
      return new EthHashSolverJob(inputs, new CompletableFuture<>());
    }

    EthHashSolverInputs getInputs() {
      return inputs;
    }

    public boolean isDone() {
      return nonceFuture.isDone();
    }

    void solvedWith(final EthHashSolution solution) {
      nonceFuture.complete(solution);
    }

    public void cancel() {
      nonceFuture.cancel(false);
    }

    public void failed(final Throwable ex) {
      nonceFuture.completeExceptionally(ex);
    }

    EthHashSolution getSolution() throws InterruptedException, ExecutionException {
      return nonceFuture.get();
    }
  }

  private final long NO_MINING_CONDUCTED = -1;

  private final Iterable<Long> nonceGenerator;
  private final EthHasher ethHasher;
  private volatile long hashesPerSecond = NO_MINING_CONDUCTED;

  private volatile Optional<EthHashSolverJob> currentJob = Optional.empty();

  public EthHashSolver(final Iterable<Long> nonceGenerator, final EthHasher ethHasher) {
    this.nonceGenerator = nonceGenerator;
    this.ethHasher = ethHasher;
  }

  public EthHashSolution solveFor(final EthHashSolverJob job)
      throws InterruptedException, ExecutionException {
    currentJob = Optional.of(job);
    findValidNonce();
    return currentJob.get().getSolution();
  }

  private void findValidNonce() {
    final Stopwatch operationTimer = Stopwatch.createStarted();
    final EthHashSolverJob job = currentJob.get();
    long hashesExecuted = 0;
    final byte[] hashBuffer = new byte[64];
    for (final Long n : nonceGenerator) {

      if (job.isDone()) {
        return;
      }

      final Optional<EthHashSolution> solution = testNonce(job.getInputs(), n, hashBuffer);
      solution.ifPresent(job::solvedWith);

      hashesExecuted++;
      final double operationDurationSeconds = operationTimer.elapsed(TimeUnit.NANOSECONDS) / 1e9;
      hashesPerSecond = (long) (hashesExecuted / operationDurationSeconds);
    }
    job.failed(new IllegalStateException("No valid nonce found."));
  }

  private Optional<EthHashSolution> testNonce(
      final EthHashSolverInputs inputs, final long nonce, final byte[] hashBuffer) {
    ethHasher.hash(hashBuffer, nonce, inputs.getBlockNumber(), inputs.getPrePowHash());
    final UInt256 x = UInt256.wrap(Bytes32.wrap(hashBuffer, 32));
    if (x.compareTo(inputs.getTarget()) <= 0) {
      final Hash mixedHash =
          Hash.wrap(Bytes32.leftPad(BytesValue.wrap(hashBuffer).slice(0, Bytes32.SIZE)));
      return Optional.of(new EthHashSolution(nonce, mixedHash, inputs.getPrePowHash()));
    }
    return Optional.empty();
  }

  public void cancel() {
    currentJob.ifPresent(EthHashSolverJob::cancel);
  }

  public Optional<EthHashSolverInputs> getWorkDefinition() {
    return currentJob.flatMap(job -> Optional.of(job.getInputs()));
  }

  public Optional<Long> hashesPerSecond() {
    if (hashesPerSecond == NO_MINING_CONDUCTED) {
      return Optional.empty();
    }
    return Optional.of(hashesPerSecond);
  }

  public boolean submitSolution(final EthHashSolution solution) {
    final Optional<EthHashSolverJob> jobSnapshot = currentJob;
    if (jobSnapshot.isEmpty()) {
      return false;
    }

    final EthHashSolverJob job = jobSnapshot.get();
    final EthHashSolverInputs inputs = job.getInputs();
    if (!Arrays.equals(inputs.getPrePowHash(), solution.getPowHash())) {
      return false;
    }
    final byte[] hashBuffer = new byte[64];
    final Optional<EthHashSolution> calculatedSolution =
        testNonce(inputs, solution.getNonce(), hashBuffer);

    if (calculatedSolution.isPresent()) {
      currentJob.get().solvedWith(solution);
      return true;
    }
    return false;
  }

  public Iterable<Long> getNonceGenerator() {
    return nonceGenerator;
  }
}
