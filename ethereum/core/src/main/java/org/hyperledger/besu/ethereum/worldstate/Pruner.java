/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Pruner {
  private static final Logger LOG = LogManager.getLogger();

  private final MarkSweepPruner pruningStrategy;
  private final Blockchain blockchain;
  private final ExecutorService executorService;
  private final long blocksRetained;
  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
  private volatile long markBlockNumber = 0;
  private volatile BlockHeader markedBlockHeader;
  private long blockConfirmations;

  public Pruner(
      final MarkSweepPruner pruningStrategy,
      final Blockchain blockchain,
      final ExecutorService executorService,
      final PruningConfiguration pruningConfiguration) {
    this.pruningStrategy = pruningStrategy;
    this.executorService = executorService;
    this.blockchain = blockchain;
    this.blocksRetained = pruningConfiguration.getBlocksRetained();
    this.blockConfirmations = pruningConfiguration.getBlockConfirmations();
    if (blockConfirmations < 0 || blocksRetained < 0) {
      throw new IllegalArgumentException(
          String.format(
              "blockConfirmations and blocksRetained must be non-negative. blockConfirmations=%d, blocksRetained=%d",
              blockConfirmations, blocksRetained));
    }
  }

  public void start() {
    blockchain.observeBlockAdded((event, blockchain) -> handleNewBlock(event));
  }

  public void stop() throws InterruptedException {
    pruningStrategy.cleanup();
    executorService.awaitTermination(10, TimeUnit.SECONDS);
  }

  private void handleNewBlock(final BlockAddedEvent event) {
    if (!event.isNewCanonicalHead()) {
      return;
    }

    final long blockNumber = event.getBlock().getHeader().getNumber();
    if (state.compareAndSet(State.IDLE, State.MARK_BLOCK_CONFIRMATIONS_AWAITING)) {
      pruningStrategy.prepare();
      markBlockNumber = blockNumber;
    } else if (blockNumber >= markBlockNumber + blockConfirmations
        && state.compareAndSet(State.MARK_BLOCK_CONFIRMATIONS_AWAITING, State.MARKING)) {
      markedBlockHeader = blockchain.getBlockHeader(markBlockNumber).get();
      mark(markedBlockHeader);
    } else if (blockNumber >= markBlockNumber + blocksRetained
        && blockchain.blockIsOnCanonicalChain(markedBlockHeader.getHash())
        && state.compareAndSet(State.MARKING_COMPLETE, State.SWEEPING)) {
      sweep();
    }
  }

  private void mark(final BlockHeader header) {
    markBlockNumber = header.getNumber();
    final Hash stateRoot = header.getStateRoot();
    LOG.info(
        "Begin marking used nodes for pruning. Block number: {} State root: {}",
        markBlockNumber,
        stateRoot);
    execute(
        () -> {
          pruningStrategy.mark(stateRoot);
          state.compareAndSet(State.MARKING, State.MARKING_COMPLETE);
        });
  }

  private void sweep() {
    LOG.info("Begin sweeping unused nodes for pruning. Retention period: {}", blocksRetained);
    execute(
        () -> {
          pruningStrategy.sweepBefore(markBlockNumber);
          state.compareAndSet(State.SWEEPING, State.IDLE);
        });
  }

  private void execute(final Runnable action) {
    try {
      executorService.execute(action);
    } catch (final Throwable t) {
      LOG.error("Pruning failed", t);
      state.set(State.IDLE);
    }
  }

  private enum State {
    IDLE,
    MARK_BLOCK_CONFIRMATIONS_AWAITING,
    MARKING,
    MARKING_COMPLETE,
    SWEEPING;
  }
}
