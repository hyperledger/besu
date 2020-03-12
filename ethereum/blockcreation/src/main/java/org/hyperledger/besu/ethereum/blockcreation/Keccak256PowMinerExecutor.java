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
package org.hyperledger.besu.ethereum.blockcreation;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Keccak256PowObserver;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.mainnet.Keccak256PowHasher;
import org.hyperledger.besu.ethereum.mainnet.Keccak256PowSolver;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.util.Subscribers;

import java.util.Optional;
import java.util.function.Function;

public class Keccak256PowMinerExecutor
    extends AbstractMinerExecutor<Void, Keccak256PowBlockMiner, Keccak256PowObserver> {

  private volatile Optional<Address> coinbase;
  private boolean stratumMiningEnabled;

  public Keccak256PowMinerExecutor(
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
  }

  @Override
  public Optional<Keccak256PowBlockMiner> startAsyncMining(
      final Subscribers<MinedBlockObserver> observers,
      final Subscribers<Keccak256PowObserver> ethHashObservers,
      final BlockHeader parentHeader) {
    if (!coinbase.isPresent()) {
      throw new CoinbaseNotSetException("Unable to start mining without a coinbase.");
    }
    return super.startAsyncMining(observers, ethHashObservers, parentHeader);
  }

  @Override
  public Keccak256PowBlockMiner createMiner(
      final Subscribers<MinedBlockObserver> observers,
      final Subscribers<Keccak256PowObserver> ethHashObservers,
      final BlockHeader parentHeader) {
    final Keccak256PowSolver solver =
        new Keccak256PowSolver(
            new RandomNonceGenerator(),
            new Keccak256PowHasher.Hasher(),
            stratumMiningEnabled,
            ethHashObservers);
    final Function<BlockHeader, Keccak256PowBlockCreator> blockCreator =
        (header) ->
            new Keccak256PowBlockCreator(
                coinbase.get(),
                parent -> extraData,
                pendingTransactions,
                protocolContext,
                protocolSchedule,
                gasLimitCalculator,
                solver,
                minTransactionGasPrice,
                parentHeader);

    return new Keccak256PowBlockMiner(
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
