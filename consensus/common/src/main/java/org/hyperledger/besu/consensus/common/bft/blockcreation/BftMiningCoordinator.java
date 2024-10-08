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
package org.hyperledger.besu.consensus.common.bft.blockcreation;

import org.hyperledger.besu.consensus.common.bft.BftEventQueue;
import org.hyperledger.besu.consensus.common.bft.BftExecutors;
import org.hyperledger.besu.consensus.common.bft.BftProcessor;
import org.hyperledger.besu.consensus.common.bft.events.NewChainHead;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftEventHandler;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Bft mining coordinator. */
public class BftMiningCoordinator implements MiningCoordinator, BlockAddedObserver {

  private enum State {
    /** Idle state. */
    IDLE,
    /** Running state. */
    RUNNING,
    /** Stopped state. */
    STOPPED,
    /** Paused state. */
    PAUSED,
  }

  private static final Logger LOG = LoggerFactory.getLogger(BftMiningCoordinator.class);

  private final BftEventHandler eventHandler;
  private final BftProcessor bftProcessor;
  private final BftBlockCreatorFactory<?> blockCreatorFactory;

  /** The Blockchain. */
  protected final Blockchain blockchain;

  private final BftEventQueue eventQueue;
  private final BftExecutors bftExecutors;

  private long blockAddedObserverId;
  private final AtomicReference<State> state = new AtomicReference<>(State.PAUSED);

  /**
   * Instantiates a new Bft mining coordinator.
   *
   * @param bftExecutors the bft executors
   * @param eventHandler the event handler
   * @param bftProcessor the bft processor
   * @param blockCreatorFactory the block creator factory
   * @param blockchain the blockchain
   * @param eventQueue the event queue
   */
  public BftMiningCoordinator(
      final BftExecutors bftExecutors,
      final BftEventHandler eventHandler,
      final BftProcessor bftProcessor,
      final BftBlockCreatorFactory<?> blockCreatorFactory,
      final Blockchain blockchain,
      final BftEventQueue eventQueue) {
    this.bftExecutors = bftExecutors;
    this.eventHandler = eventHandler;
    this.bftProcessor = bftProcessor;
    this.blockCreatorFactory = blockCreatorFactory;
    this.eventQueue = eventQueue;

    this.blockchain = blockchain;
  }

  @Override
  public void start() {
    if (state.compareAndSet(State.IDLE, State.RUNNING)
        || state.compareAndSet(State.STOPPED, State.RUNNING)) {
      bftProcessor.start();
      bftExecutors.start();
      blockAddedObserverId = blockchain.observeBlockAdded(this);
      eventHandler.start();
      bftExecutors.executeBftProcessor(bftProcessor);
    }
  }

  @Override
  public void stop() {
    if (state.compareAndSet(State.RUNNING, State.STOPPED)) {
      blockchain.removeObserver(blockAddedObserverId);
      bftProcessor.stop();
      // Make sure the processor has stopped before shutting down the executors
      try {
        bftProcessor.awaitStop();
      } catch (final InterruptedException e) {
        LOG.debug("Interrupted while waiting for BftProcessor to stop.", e);
        Thread.currentThread().interrupt();
      }
      bftExecutors.stop();
    }
  }

  @Override
  public void awaitStop() throws InterruptedException {
    bftExecutors.awaitStop();
  }

  @Override
  public boolean enable() {
    // Return true if we're already running or idle, or successfully switch to idle
    if (state.get() == State.RUNNING
        || state.get() == State.IDLE
        || state.compareAndSet(State.PAUSED, State.IDLE)) {
      return true;
    }
    return false;
  }

  @Override
  public boolean disable() {
    if (state.get() == State.PAUSED
        || state.compareAndSet(State.IDLE, State.PAUSED)
        || state.compareAndSet(State.RUNNING, State.PAUSED)) {
      return true;
    }
    return false;
  }

  @Override
  public boolean isMining() {
    return state.get() == State.RUNNING;
  }

  @Override
  public Wei getMinTransactionGasPrice() {
    return blockCreatorFactory.getMinTransactionGasPrice();
  }

  @Override
  public Wei getMinPriorityFeePerGas() {
    return blockCreatorFactory.getMinPriorityFeePerGas();
  }

  @Override
  public Optional<Address> getCoinbase() {
    return Optional.of(blockCreatorFactory.getLocalAddress());
  }

  @Override
  public Optional<Block> createBlock(
      final BlockHeader parentHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers) {
    // One-off block creation has not been implemented
    return Optional.empty();
  }

  @Override
  public Optional<Block> createBlock(final BlockHeader parentHeader, final long timestamp) {
    // One-off block creation has not been implemented
    return Optional.empty();
  }

  @Override
  public void changeTargetGasLimit(final Long targetGasLimit) {
    blockCreatorFactory.changeTargetGasLimit(targetGasLimit);
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    if (event.isNewCanonicalHead()) {
      LOG.trace("New canonical head detected");
      eventQueue.add(new NewChainHead(event.getBlock().getHeader()));
    }
  }

  @Override
  public void removeObserver() {
    blockchain.removeObserver(blockAddedObserverId);
  }
}
