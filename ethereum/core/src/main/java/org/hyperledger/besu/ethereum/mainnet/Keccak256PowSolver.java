/*
 * Copyright 2020 Whiteblock Inc.
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

import org.hyperledger.besu.ethereum.chain.Keccak256PowObserver;
import org.hyperledger.besu.util.Subscribers;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class Keccak256PowSolver {

  private static final Logger LOG = getLogger();

  public static class Keccak256PowSolverJob {

    private final EthHashSolverInputs inputs;
    private final CompletableFuture<PowSolution> nonceFuture;

    Keccak256PowSolverJob(
        final EthHashSolverInputs inputs, final CompletableFuture<PowSolution> nonceFuture) {
      this.inputs = inputs;
      this.nonceFuture = nonceFuture;
    }

    public static Keccak256PowSolverJob createFromInputs(final EthHashSolverInputs inputs) {
      return new Keccak256PowSolverJob(inputs, new CompletableFuture<>());
    }

    EthHashSolverInputs getInputs() {
      return inputs;
    }

    public boolean isDone() {
      return nonceFuture.isDone();
    }

    void solvedWith(final PowSolution solution) {
      nonceFuture.complete(solution);
    }

    public void cancel() {
      nonceFuture.cancel(false);
    }

    public void failed(final Throwable ex) {
      nonceFuture.completeExceptionally(ex);
    }

    PowSolution getSolution() throws InterruptedException, ExecutionException {
      return nonceFuture.get();
    }
  }

  private final long NO_MINING_CONDUCTED = -1;

  private final Iterable<Long> nonceGenerator;
  private final Keccak256PowHasher keccak256PowHasher;
  private volatile long hashesPerSecond = NO_MINING_CONDUCTED;
  private final Boolean stratumMiningEnabled;
  private final Subscribers<Keccak256PowObserver> ethHashObservers;
  private volatile Optional<Keccak256PowSolverJob> currentJob = Optional.empty();

  public Keccak256PowSolver(
      final Iterable<Long> nonceGenerator,
      final Keccak256PowHasher keccak256PowHasher,
      final Boolean stratumMiningEnabled,
      final Subscribers<Keccak256PowObserver> ethHashObservers) {
    this.nonceGenerator = nonceGenerator;
    this.keccak256PowHasher = keccak256PowHasher;
    this.stratumMiningEnabled = stratumMiningEnabled;
    this.ethHashObservers = ethHashObservers;
    ethHashObservers.forEach(observer -> observer.setSubmitWorkCallback(this::submitSolution));
  }

  public PowSolution solveFor(final Keccak256PowSolverJob job)
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
    final Keccak256PowSolverJob job = currentJob.get();
    long hashesExecuted = 0;
    final byte[] hashBuffer = new byte[64];
    for (final Long n : nonceGenerator) {

      if (job.isDone()) {
        return;
      }

      final Optional<PowSolution> solution = testNonce(job.getInputs(), n, hashBuffer);
      solution.ifPresent(job::solvedWith);

      hashesExecuted++;
      final double operationDurationSeconds = operationTimer.elapsed(TimeUnit.NANOSECONDS) / 1e9;
      hashesPerSecond = (long) (hashesExecuted / operationDurationSeconds);
    }
    job.failed(new IllegalStateException("No valid nonce found."));
  }

  private Optional<PowSolution> testNonce(
      final EthHashSolverInputs inputs, final long nonce, final byte[] hashBuffer) {
    keccak256PowHasher.hash(hashBuffer, nonce, inputs.getBlockNumber(), inputs.getPrePowHash());
    final UInt256 x = UInt256.fromBytes(Bytes32.wrap(hashBuffer, 32));
    if (x.compareTo(inputs.getTarget()) <= 0) {
      return Optional.of(new PowSolution(nonce, inputs.getPrePowHash()));
    }
    return Optional.empty();
  }

  public void cancel() {
    currentJob.ifPresent(Keccak256PowSolverJob::cancel);
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

  public boolean submitSolution(final PowSolution solution) {
    final Optional<Keccak256PowSolverJob> jobSnapshot = currentJob;
    if (jobSnapshot.isEmpty()) {
      LOG.debug("No current job, rejecting miner work");
      return false;
    }

    final Keccak256PowSolverJob job = jobSnapshot.get();
    final EthHashSolverInputs inputs = job.getInputs();
    if (!Arrays.equals(inputs.getPrePowHash(), solution.getPowHash())) {
      LOG.debug("Miner's solution does not match current job");
      return false;
    }
    final byte[] hashBuffer = new byte[64];
    final Optional<PowSolution> calculatedSolution =
        testNonce(inputs, solution.getNonce(), hashBuffer);

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
