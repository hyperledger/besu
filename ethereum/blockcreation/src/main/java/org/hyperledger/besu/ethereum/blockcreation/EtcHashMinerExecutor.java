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
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.mainnet.EthHashSolver;
import org.hyperledger.besu.ethereum.mainnet.EthHasher;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.util.Subscribers;

import java.util.function.Function;

public class EtcHashMinerExecutor extends EthHashMinerExecutor {
  private long activationBlock;

  public EtcHashMinerExecutor(
      ProtocolContext protocolContext,
      ProtocolSchedule protocolSchedule,
      PendingTransactions pendingTransactions,
      MiningParameters miningParams,
      AbstractBlockScheduler blockScheduler,
      Function<Long, Long> gasLimitCalculator,
      long activationBlock) {
    super(
        protocolContext,
        protocolSchedule,
        pendingTransactions,
        miningParams,
        blockScheduler,
        gasLimitCalculator);
    this.activationBlock = activationBlock;
  }

  @Override
  public EthHashBlockMiner createMiner(
      Subscribers<MinedBlockObserver> observers,
      Subscribers<EthHashObserver> ethHashObservers,
      BlockHeader parentHeader) {
    final EthHasher hasher = new EthHasher.EtcHasher(activationBlock);
    final EthHashSolver solver =
        new EthHashSolver(nonceGenerator, hasher, stratumMiningEnabled, ethHashObservers);
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
}
