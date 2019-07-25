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
package tech.pegasys.pantheon.ethereum.blockcreation;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

public abstract class AbstractMinerExecutor<
    C, M extends BlockMiner<C, ? extends AbstractBlockCreator<C>>> {

  protected final ProtocolContext<C> protocolContext;
  protected final ExecutorService executorService;
  protected final ProtocolSchedule<C> protocolSchedule;
  protected final PendingTransactions pendingTransactions;
  protected final AbstractBlockScheduler blockScheduler;

  protected volatile BytesValue extraData;
  protected volatile Wei minTransactionGasPrice;

  public AbstractMinerExecutor(
      final ProtocolContext<C> protocolContext,
      final ExecutorService executorService,
      final ProtocolSchedule<C> protocolSchedule,
      final PendingTransactions pendingTransactions,
      final MiningParameters miningParams,
      final AbstractBlockScheduler blockScheduler) {
    this.protocolContext = protocolContext;
    this.executorService = executorService;
    this.protocolSchedule = protocolSchedule;
    this.pendingTransactions = pendingTransactions;
    this.extraData = miningParams.getExtraData();
    this.minTransactionGasPrice = miningParams.getMinTransactionGasPrice();
    this.blockScheduler = blockScheduler;
  }

  public abstract M startAsyncMining(
      final Subscribers<MinedBlockObserver> observers, final BlockHeader parentHeader);

  public void setExtraData(final BytesValue extraData) {
    this.extraData = extraData.copy();
  }

  public void setMinTransactionGasPrice(final Wei minTransactionGasPrice) {
    this.minTransactionGasPrice = minTransactionGasPrice.copy();
  }

  public Wei getMinTransactionGasPrice() {
    return minTransactionGasPrice;
  }

  public abstract Optional<Address> getCoinbase();
}
