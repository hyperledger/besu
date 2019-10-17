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

import org.hyperledger.besu.consensus.ibft.IbftEventQueue;
import org.hyperledger.besu.consensus.ibft.IbftExecutors;
import org.hyperledger.besu.consensus.ibft.IbftProcessor;
import org.hyperledger.besu.consensus.ibft.ibftevent.NewChainHead;
import org.hyperledger.besu.consensus.ibft.statemachine.IbftController;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.Logger;

public class IbftMiningCoordinator implements MiningCoordinator, BlockAddedObserver {

  private static final Logger LOG = getLogger();

  private final IbftController controller;
  private final IbftProcessor ibftProcessor;
  private final IbftBlockCreatorFactory blockCreatorFactory;
  protected final Blockchain blockchain;
  private final IbftEventQueue eventQueue;
  private final IbftExecutors ibftExecutors;

  private AtomicBoolean started = new AtomicBoolean(false);
  private AtomicBoolean stopped = new AtomicBoolean(false);

  public IbftMiningCoordinator(
      final IbftExecutors ibftExecutors,
      final IbftController controller,
      final IbftProcessor ibftProcessor,
      final IbftBlockCreatorFactory blockCreatorFactory,
      final Blockchain blockchain,
      final IbftEventQueue eventQueue) {
    this.ibftExecutors = ibftExecutors;
    this.controller = controller;
    this.ibftProcessor = ibftProcessor;
    this.blockCreatorFactory = blockCreatorFactory;
    this.eventQueue = eventQueue;

    this.blockchain = blockchain;
    this.blockchain.observeBlockAdded(this);
  }

  @Override
  public void start() {
    if (started.compareAndSet(false, true)) {
      ibftExecutors.start();
      controller.start();
      ibftExecutors.executeIbftProcessor(ibftProcessor);
    }
  }

  @Override
  public void stop() {
    if (started.get() && stopped.compareAndSet(false, true)) {
      ibftProcessor.stop();
      controller.stop();
      ibftExecutors.stop();
    }
  }

  @Override
  public void awaitStop() throws InterruptedException {
    ibftExecutors.awaitStop();
  }

  @Override
  public boolean enable() {
    return false;
  }

  @Override
  public boolean disable() {
    return false;
  }

  @Override
  public boolean isRunning() {
    return true;
  }

  @Override
  public Wei getMinTransactionGasPrice() {
    return blockCreatorFactory.getMinTransactionGasPrice();
  }

  @Override
  public void setExtraData(final BytesValue extraData) {
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
  public void onBlockAdded(final BlockAddedEvent event, final Blockchain blockchain) {
    if (event.isNewCanonicalHead()) {
      LOG.trace("New canonical head detected");
      eventQueue.add(new NewChainHead(event.getBlock().getHeader()));
    }
  }
}
