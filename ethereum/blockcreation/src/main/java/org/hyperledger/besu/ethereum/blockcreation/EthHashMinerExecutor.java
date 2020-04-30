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
import org.hyperledger.besu.ethereum.chain.EthHashObserver;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.mainnet.EthHashSolver;
import org.hyperledger.besu.ethereum.mainnet.EthHasher;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.util.Subscribers;

import java.util.Optional;
import java.util.function.Function;

public class EthHashMinerExecutor extends AbstractMinerExecutor<Void, EthHashBlockMiner> {

  private volatile Optional<Address> coinbase;
  private boolean stratumMiningEnabled;
  private final Iterable<Long> nonceGenerator;

  public EthHashMinerExecutor(
      final ProtocolContext<Void> protocolContext,
      final ProtocolSchedule<Void> protocolSchedule,
      final PendingTransactions pendingTransactions,
      final MiningParameters miningParams,
      final AbstractBlockScheduler blockScheduler,
      final Function<Long, Long> gasLimitCalculator) {
    super(
        protocolContext,
        protocolSchedule,
        pendingTransactions,
        miningParams,
        blockScheduler,
        gasLimitCalculator);
    this.coinbase = miningParams.getCoinbase();
    this.nonceGenerator = miningParams.getNonceGenerator().orElse(new RandomNonceGenerator());
  }

  @Override
  public Optional<EthHashBlockMiner> startAsyncMining(
      final Subscribers<MinedBlockObserver> observers,
      final Subscribers<EthHashObserver> ethHashObservers,
      final BlockHeader parentHeader) {
    if (coinbase.isEmpty()) {
      throw new CoinbaseNotSetException("Unable to start mining without a coinbase.");
    }
    return super.startAsyncMining(observers, ethHashObservers, parentHeader);
  }

  @Override
  public EthHashBlockMiner createMiner(
      final Subscribers<MinedBlockObserver> observers,
      final Subscribers<EthHashObserver> ethHashObservers,
      final BlockHeader parentHeader) {
    final EthHashSolver solver =
        new EthHashSolver(
            nonceGenerator, new EthHasher.Light(), stratumMiningEnabled, ethHashObservers);
    final Function<BlockHeader, EthHashBlockCreator> blockCreator =
        (header) ->
            new EthHashBlockCreator(
                coinbase.get(),
                parent -> extraData,
                pendingTransactions,
                protocolContext,
                protocolSchedule,
                gasLimitCalculator,
                solver,
                minTransactionGasPrice,
                minBlockOccupancyRatio,
                parentHeader);

    return new EthHashBlockMiner(
        blockCreator, protocolSchedule, protocolContext, observers, blockScheduler, parentHeader);
  }

  public void setCoinbase(final Address coinbase) {
    if (coinbase == null) {
      throw new IllegalArgumentException("Coinbase cannot be unset.");
    } else {
      this.coinbase = Optional.of(Address.wrap(coinbase.copy()));
    }
  }

  void setStratumMiningEnabled(final boolean stratumMiningEnabled) {
    this.stratumMiningEnabled = stratumMiningEnabled;
  }

  @Override
  public Optional<Address> getCoinbase() {
    return coinbase;
  }
}
