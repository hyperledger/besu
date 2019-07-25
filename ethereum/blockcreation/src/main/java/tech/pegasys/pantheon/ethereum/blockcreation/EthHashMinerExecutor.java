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
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.mainnet.EthHashSolver;
import tech.pegasys.pantheon.ethereum.mainnet.EthHasher;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.util.Subscribers;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class EthHashMinerExecutor extends AbstractMinerExecutor<Void, EthHashBlockMiner> {

  private volatile Optional<Address> coinbase;

  public EthHashMinerExecutor(
      final ProtocolContext<Void> protocolContext,
      final ExecutorService executorService,
      final ProtocolSchedule<Void> protocolSchedule,
      final PendingTransactions pendingTransactions,
      final MiningParameters miningParams,
      final AbstractBlockScheduler blockScheduler) {
    super(
        protocolContext,
        executorService,
        protocolSchedule,
        pendingTransactions,
        miningParams,
        blockScheduler);
    this.coinbase = miningParams.getCoinbase();
  }

  @Override
  public EthHashBlockMiner startAsyncMining(
      final Subscribers<MinedBlockObserver> observers, final BlockHeader parentHeader) {
    if (!coinbase.isPresent()) {
      throw new CoinbaseNotSetException("Unable to start mining without a coinbase.");
    } else {
      final EthHashSolver solver =
          new EthHashSolver(new RandomNonceGenerator(), new EthHasher.Light());
      final EthHashBlockCreator blockCreator =
          new EthHashBlockCreator(
              coinbase.get(),
              parent -> extraData,
              pendingTransactions,
              protocolContext,
              protocolSchedule,
              (gasLimit) -> gasLimit,
              solver,
              minTransactionGasPrice,
              parentHeader);

      final EthHashBlockMiner currentRunningMiner =
          new EthHashBlockMiner(
              blockCreator,
              protocolSchedule,
              protocolContext,
              observers,
              blockScheduler,
              parentHeader);
      executorService.execute(currentRunningMiner);
      return currentRunningMiner;
    }
  }

  public void setCoinbase(final Address coinbase) {
    if (coinbase == null) {
      throw new IllegalArgumentException("Coinbase cannot be unset.");
    } else {
      this.coinbase = Optional.of(coinbase.copy());
    }
  }

  @Override
  public Optional<Address> getCoinbase() {
    return coinbase;
  }
}
