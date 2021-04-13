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
package org.hyperledger.besu.ethereum.mainnet;

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.ethereum.chain.PoWObserver;
import org.hyperledger.besu.util.Subscribers;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;

public class PoWSolver {

  private static final Logger LOG = getLogger();

  public static class PoWSolverJob {

    private final PoWSolverInputs inputs;
    private final CompletableFuture<PoWSolution> nonceFuture;

    PoWSolverJob(final PoWSolverInputs inputs, final CompletableFuture<PoWSolution> nonceFuture) {
      this.inputs = inputs;
      this.nonceFuture = nonceFuture;
    }

    public static PoWSolverJob createFromInputs(final PoWSolverInputs inputs) {
      return new PoWSolverJob(inputs, new CompletableFuture<>());
    }

    PoWSolverInputs getInputs() {
      return inputs;
    }

    public boolean isDone() {
      return nonceFuture.isDone();
    }

    void solvedWith(final PoWSolution solution) {
      nonceFuture.complete(solution);
    }

    public void cancel() {
      nonceFuture.cancel(false);
    }

    public void failed(final Throwable ex) {
      nonceFuture.completeExceptionally(ex);
    }

    PoWSolution getSolution() throws InterruptedException, ExecutionException {
      return nonceFuture.get();
    }
  }

  private final long NO_MINING_CONDUCTED = -1;

  private final Iterable<Long> nonceGenerator;
  private final PoWHasher poWHasher;
  private volatile long hashesPerSecond = NO_MINING_CONDUCTED;
  private final Boolean stratumMiningEnabled;
  private final Subscribers<PoWObserver> ethHashObservers;
  private final EpochCalculator epochCalculator;
  private volatile Optional<PoWSolverJob> currentJob = Optional.empty();

  public PoWSolver(
      final Iterable<Long> nonceGenerator,
      final PoWHasher poWHasher,
      final Boolean stratumMiningEnabled,
      final Subscribers<PoWObserver> ethHashObservers,
      final EpochCalculator epochCalculator) {
    this.nonceGenerator = nonceGenerator;
    this.poWHasher = poWHasher;
    this.stratumMiningEnabled = stratumMiningEnabled;
    this.ethHashObservers = ethHashObservers;
    ethHashObservers.forEach(observer -> observer.setSubmitWorkCallback(this::submitSolution));
    this.epochCalculator = epochCalculator;
  }

  public PoWSolution solveFor(final PoWSolverJob job)
      throws InterruptedException, ExecutionException {
    currentJob = Optional.of(job);
    if (stratumMiningEnabled) {
      ethHashObservers.forEach(observer -> observer.newJob(job.inputs));
    } else {
      findValidNonce();
    }
    return currentJob.get().getSolution();
  }

  private void findValidNonce() {
    final Stopwatch operationTimer = Stopwatch.createStarted();
    final PoWSolverJob job = currentJob.get();
    long hashesExecuted = 0;
    for (final Long n : nonceGenerator) {

      if (job.isDone()) {
        return;
      }

      final Optional<PoWSolution> solution = testNonce(job.getInputs(), n);
      solution.ifPresent(job::solvedWith);

      hashesExecuted++;
      final double operationDurationSeconds = operationTimer.elapsed(TimeUnit.NANOSECONDS) / 1e9;
      hashesPerSecond = (long) (hashesExecuted / operationDurationSeconds);
    }
    job.failed(new IllegalStateException("No valid nonce found."));
  }

  private Optional<PoWSolution> testNonce(final PoWSolverInputs inputs, final long nonce) {
    return Optional.ofNullable(
            poWHasher.hash(nonce, inputs.getBlockNumber(), epochCalculator, inputs.getPrePowHash()))
        .filter(sol -> UInt256.fromBytes(sol.getSolution()).compareTo(inputs.getTarget()) <= 0);
  }

  public void cancel() {
    currentJob.ifPresent(PoWSolverJob::cancel);
  }

  public Optional<PoWSolverInputs> getWorkDefinition() {
    return currentJob.flatMap(job -> Optional.of(job.getInputs()));
  }

  public Optional<Long> hashesPerSecond() {
    if (hashesPerSecond == NO_MINING_CONDUCTED) {
      return Optional.empty();
    }
    return Optional.of(hashesPerSecond);
  }

  public boolean submitSolution(final PoWSolution solution) {
    final Optional<PoWSolverJob> jobSnapshot = currentJob;
    if (jobSnapshot.isEmpty()) {
      LOG.debug("No current job, rejecting miner work");
      return false;
    }

    final PoWSolverJob job = jobSnapshot.get();
    final PoWSolverInputs inputs = job.getInputs();
    if (!inputs.getPrePowHash().equals(solution.getPowHash())) {
      LOG.debug("Miner's solution does not match current job");
      return false;
    }
    final Optional<PoWSolution> calculatedSolution = testNonce(inputs, solution.getNonce());

    if (calculatedSolution.isPresent()) {
      LOG.debug("Accepting a solution from a miner");
      currentJob.get().solvedWith(calculatedSolution.get());
      return true;
    }
    LOG.debug("Rejecting a solution from a miner");
    return false;
  }

  public Iterable<Long> getNonceGenerator() {
    return nonceGenerator;
  }
}
