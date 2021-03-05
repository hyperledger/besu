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
package org.hyperledger.besu.ethereum.blockcreation;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.chain.PoWObserver;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.util.Subscribers;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public abstract class AbstractMinerExecutor<M extends BlockMiner<? extends AbstractBlockCreator>> {

  private static final Logger LOG = LogManager.getLogger();

  private final ExecutorService executorService = Executors.newCachedThreadPool();
  protected final ProtocolContext protocolContext;
  protected final ProtocolSchedule protocolSchedule;
  protected final PendingTransactions pendingTransactions;
  protected final AbstractBlockScheduler blockScheduler;
  protected final GasLimitCalculator gasLimitCalculator;

  protected volatile Bytes extraData;
  protected volatile Wei minTransactionGasPrice;
  protected volatile Double minBlockOccupancyRatio;

  private final AtomicBoolean stopped = new AtomicBoolean(false);

  protected AbstractMinerExecutor(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final PendingTransactions pendingTransactions,
      final MiningParameters miningParams,
      final AbstractBlockScheduler blockScheduler,
      final GasLimitCalculator gasLimitCalculator) {
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.pendingTransactions = pendingTransactions;
    this.extraData = miningParams.getExtraData();
    this.minTransactionGasPrice = miningParams.getMinTransactionGasPrice();
    this.blockScheduler = blockScheduler;
    this.gasLimitCalculator = gasLimitCalculator;
    this.minBlockOccupancyRatio = miningParams.getMinBlockOccupancyRatio();
  }

  public Optional<M> startAsyncMining(
      final Subscribers<MinedBlockObserver> observers,
      final Subscribers<PoWObserver> ethHashObservers,
      final BlockHeader parentHeader) {
    try {
      final M currentRunningMiner = createMiner(observers, ethHashObservers, parentHeader);
      executorService.execute(currentRunningMiner);
      return Optional.of(currentRunningMiner);
    } catch (RejectedExecutionException e) {
      LOG.warn("Unable to start mining.", e);
      return Optional.empty();
    }
  }

  public void shutDown() {
    if (stopped.compareAndSet(false, true)) {
      executorService.shutdownNow();
    }
  }

  public void awaitShutdown() throws InterruptedException {
    if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
      LOG.error("Failed to shutdown {}.", this.getClass().getSimpleName());
    }
  }

  public abstract M createMiner(
      final Subscribers<MinedBlockObserver> subscribers,
      final Subscribers<PoWObserver> ethHashObservers,
      final BlockHeader parentHeader);

  public void setExtraData(final Bytes extraData) {
    this.extraData = extraData.copy();
  }

  public void setMinTransactionGasPrice(final Wei minTransactionGasPrice) {
    this.minTransactionGasPrice = minTransactionGasPrice;
  }

  public Wei getMinTransactionGasPrice() {
    return minTransactionGasPrice;
  }

  public abstract Optional<Address> getCoinbase();

  public void changeTargetGasLimit(final Long targetGasLimit) {
    gasLimitCalculator.changeTargetGasLimit(targetGasLimit);
  }
}
