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
package org.hyperledger.besu.ethereum.worldstate;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Pruner {

  private static final Logger LOG = LogManager.getLogger();

  private final MarkSweepPruner pruningStrategy;
  private final Blockchain blockchain;
  private Long blockAddedObserverId;
  private final long blocksRetained;
  private final AtomicReference<PruningPhase> pruningPhase =
      new AtomicReference<>(PruningPhase.IDLE);
  private volatile long markBlockNumber = 0;
  private volatile BlockHeader markedBlockHeader;
  private final long blockConfirmations;

  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
  private final Supplier<ExecutorService> executorServiceSupplier;
  private ExecutorService executorService;

  @VisibleForTesting
  Pruner(
      final MarkSweepPruner pruningStrategy,
      final Blockchain blockchain,
      final PrunerConfiguration prunerConfiguration,
      final Supplier<ExecutorService> executorServiceSupplier) {
    this.pruningStrategy = pruningStrategy;
    this.blockchain = blockchain;
    this.executorServiceSupplier = executorServiceSupplier;
    this.blocksRetained = prunerConfiguration.getBlocksRetained();
    this.blockConfirmations = prunerConfiguration.getBlockConfirmations();
    checkArgument(
        blockConfirmations >= 0 && blockConfirmations < blocksRetained,
        "blockConfirmations and blocksRetained must be non-negative. blockConfirmations must be less than blockRetained.");
  }

  public Pruner(
      final MarkSweepPruner pruningStrategy,
      final Blockchain blockchain,
      final PrunerConfiguration prunerConfiguration) {
    this(pruningStrategy, blockchain, prunerConfiguration, getDefaultExecutorSupplier());
  }

  private static Supplier<ExecutorService> getDefaultExecutorSupplier() {
    return () ->
        Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setPriority(Thread.MIN_PRIORITY)
                .setNameFormat("StatePruning-%d")
                .build());
  }

  public void start() {

    if (state.compareAndSet(State.IDLE, State.RUNNING)) {
      LOG.info("Starting Pruner.");
      executorService = executorServiceSupplier.get();
      pruningStrategy.prepare();
      blockAddedObserverId = blockchain.observeBlockAdded(this::handleNewBlock);
    }
  }

  public void stop() {
    if (state.compareAndSet(State.RUNNING, State.STOPPED)) {
      LOG.info("Stopping Pruner.");
      pruningStrategy.cleanup();
      blockchain.removeObserver(blockAddedObserverId);
      executorService.shutdownNow();
    }
  }

  public void awaitStop() throws InterruptedException {
    if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
      LOG.error("Failed to shutdown Pruner executor service.");
    }
  }

  private void handleNewBlock(final BlockAddedEvent event) {
    if (!event.isNewCanonicalHead()) {
      return;
    }

    final long blockNumber = event.getBlock().getHeader().getNumber();
    if (pruningPhase.compareAndSet(
        PruningPhase.IDLE, PruningPhase.MARK_BLOCK_CONFIRMATIONS_AWAITING)) {
      markBlockNumber = blockNumber;
    } else if (blockNumber >= markBlockNumber + blockConfirmations
        && pruningPhase.compareAndSet(
            PruningPhase.MARK_BLOCK_CONFIRMATIONS_AWAITING, PruningPhase.MARKING)) {
      markedBlockHeader = blockchain.getBlockHeader(markBlockNumber).get();
      mark(markedBlockHeader);
    } else if (blockNumber >= markBlockNumber + blocksRetained
        && blockchain.blockIsOnCanonicalChain(markedBlockHeader.getHash())
        && pruningPhase.compareAndSet(PruningPhase.MARKING_COMPLETE, PruningPhase.SWEEPING)) {
      sweep();
    }
  }

  private void mark(final BlockHeader header) {
    final Hash stateRoot = header.getStateRoot();
    LOG.debug(
        "Begin marking used nodes for pruning. Block number: {} State root: {}",
        markBlockNumber,
        stateRoot);
    execute(
        () -> {
          pruningStrategy.mark(stateRoot);
          pruningPhase.compareAndSet(PruningPhase.MARKING, PruningPhase.MARKING_COMPLETE);
        });
  }

  private void sweep() {
    LOG.debug(
        "Begin sweeping unused nodes for pruning. Keeping full state for blocks {} to {}",
        markBlockNumber,
        markBlockNumber + blocksRetained);
    execute(
        () -> {
          pruningStrategy.sweepBefore(markBlockNumber);
          pruningPhase.compareAndSet(PruningPhase.SWEEPING, PruningPhase.IDLE);
        });
  }

  private void execute(final Runnable action) {
    try {
      executorService.execute(action);
    } catch (final Throwable t) {
      LOG.error("Pruning failed", t);
      pruningStrategy.cleanup();
      pruningPhase.set(PruningPhase.IDLE);
    }
  }

  @VisibleForTesting
  PruningPhase getPruningPhase() {
    return pruningPhase.get();
  }

  enum PruningPhase {
    IDLE,
    MARK_BLOCK_CONFIRMATIONS_AWAITING,
    MARKING,
    MARKING_COMPLETE,
    SWEEPING;
  }

  private enum State {
    IDLE,
    RUNNING,
    STOPPED
  }
}
