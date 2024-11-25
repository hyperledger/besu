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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.chain.PoWObserver;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.PoWSolver;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.util.Subscribers;

import java.util.Optional;
import java.util.function.Function;

public class PoWMinerExecutor extends AbstractMinerExecutor<PoWBlockMiner> {

  protected boolean stratumMiningEnabled;
  protected final EpochCalculator epochCalculator;

  public PoWMinerExecutor(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final TransactionPool transactionPool,
      final MiningConfiguration miningParams,
      final AbstractBlockScheduler blockScheduler,
      final EpochCalculator epochCalculator,
      final EthScheduler ethScheduler) {
    super(
        protocolContext,
        protocolSchedule,
        transactionPool,
        miningParams,
        blockScheduler,
        ethScheduler);
    if (miningParams.getNonceGenerator().isEmpty()) {
      miningParams.setNonceGenerator(new RandomNonceGenerator());
    }
    this.epochCalculator = epochCalculator;
  }

  @Override
  public Optional<PoWBlockMiner> startAsyncMining(
      final Subscribers<MinedBlockObserver> observers,
      final Subscribers<PoWObserver> ethHashObservers,
      final BlockHeader parentHeader) {
    if (miningConfiguration.getCoinbase().isEmpty()) {
      throw new CoinbaseNotSetException("Unable to start mining without a coinbase.");
    }
    return super.startAsyncMining(observers, ethHashObservers, parentHeader);
  }

  @Override
  public PoWBlockMiner createMiner(
      final Subscribers<MinedBlockObserver> observers,
      final Subscribers<PoWObserver> ethHashObservers,
      final BlockHeader parentHeader) {
    // We don't need to consider the timestamp when getting the protocol schedule for the next block
    // as timestamps are not used for defining forks when using POW
    final ProtocolSpec nextBlockProtocolSpec =
        protocolSchedule.getForNextBlockHeader(parentHeader, 0);
    final PoWSolver solver =
        new PoWSolver(
            miningConfiguration,
            nextBlockProtocolSpec.getPoWHasher().get(),
            stratumMiningEnabled,
            ethHashObservers,
            epochCalculator);
    final Function<BlockHeader, PoWBlockCreator> blockCreator =
        (header) ->
            new PoWBlockCreator(
                miningConfiguration,
                parent -> miningConfiguration.getExtraData(),
                transactionPool,
                protocolContext,
                protocolSchedule,
                solver,
                ethScheduler);

    return new PoWBlockMiner(
        blockCreator, protocolSchedule, protocolContext, observers, blockScheduler, parentHeader);
  }

  public void setCoinbase(final Address coinbase) {
    if (coinbase == null) {
      throw new IllegalArgumentException("Coinbase cannot be unset.");
    } else {
      miningConfiguration.setCoinbase(Address.wrap(coinbase.copy()));
    }
  }

  void setStratumMiningEnabled(final boolean stratumMiningEnabled) {
    this.stratumMiningEnabled = stratumMiningEnabled;
  }

  @Override
  public Optional<Address> getCoinbase() {
    return miningConfiguration.getCoinbase();
  }

  public EpochCalculator getEpochCalculator() {
    return epochCalculator;
  }
}
