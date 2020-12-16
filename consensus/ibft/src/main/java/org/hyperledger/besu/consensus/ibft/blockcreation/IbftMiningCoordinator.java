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
package org.hyperledger.besu.consensus.ibft.blockcreation;

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.consensus.common.bft.BftEventQueue;
import org.hyperledger.besu.consensus.common.bft.BftExecutors;
import org.hyperledger.besu.consensus.common.bft.BftProcessor;
import org.hyperledger.besu.consensus.common.bft.events.NewChainHead;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftEventHandler;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class IbftMiningCoordinator implements MiningCoordinator, BlockAddedObserver {

  private enum State {
    IDLE,
    RUNNING,
    STOPPED
  }

  private static final Logger LOG = getLogger();

  private final BftEventHandler eventHandler;
  private final BftProcessor bftProcessor;
  private final IbftBlockCreatorFactory blockCreatorFactory;
  protected final Blockchain blockchain;
  private final BftEventQueue eventQueue;
  private final BftExecutors bftExecutors;

  private long blockAddedObserverId;
  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

  public IbftMiningCoordinator(
      final BftExecutors bftExecutors,
      final BftEventHandler eventHandler,
      final BftProcessor bftProcessor,
      final IbftBlockCreatorFactory blockCreatorFactory,
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
    if (state.compareAndSet(State.IDLE, State.RUNNING)) {
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
        LOG.debug("Interrupted while waiting for IbftProcessor to stop.", e);
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
    return true;
  }

  @Override
  public boolean disable() {
    return false;
  }

  @Override
  public boolean isMining() {
    return true;
  }

  @Override
  public Wei getMinTransactionGasPrice() {
    return blockCreatorFactory.getMinTransactionGasPrice();
  }

  @Override
  public void setExtraData(final Bytes extraData) {
    blockCreatorFactory.setExtraData(extraData);
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
}
