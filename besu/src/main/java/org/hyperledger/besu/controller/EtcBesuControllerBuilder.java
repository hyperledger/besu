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
package org.hyperledger.besu.controller;

import org.hyperledger.besu.config.EtchashConfigOptions;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.DefaultBlockScheduler;
import org.hyperledger.besu.ethereum.blockcreation.EtcHashMinerExecutor;
import org.hyperledger.besu.ethereum.blockcreation.EthHashMiningCoordinator;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

public class EtcBesuControllerBuilder extends MainnetBesuControllerBuilder {
  private long activationBlock;

  @Override
  protected void prepForBuild() {
    final EtchashConfigOptions etchashConfig =
        genesisConfig.getConfigOptions(genesisConfigOverrides).getEtchashConfigOptions();
    activationBlock = etchashConfig.getEpochLengthActivationBlock().getAsLong();
  }

  @Override
  protected MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionPool transactionPool,
      final MiningParameters miningParameters,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager) {
    final EtcHashMinerExecutor executor =
        new EtcHashMinerExecutor(
            protocolContext,
            protocolSchedule,
            transactionPool.getPendingTransactions(),
            miningParameters,
            new DefaultBlockScheduler(
                MainnetBlockHeaderValidator.MINIMUM_SECONDS_SINCE_PARENT,
                MainnetBlockHeaderValidator.TIMESTAMP_TOLERANCE_S,
                clock),
            gasLimitCalculator,
            activationBlock);

    final EthHashMiningCoordinator miningCoordinator =
        new EthHashMiningCoordinator(
            protocolContext.getBlockchain(),
            executor,
            syncState,
            miningParameters.getRemoteSealersLimit(),
            miningParameters.getRemoteSealersTimeToLive());
    miningCoordinator.addMinedBlockObserver(ethProtocolManager);
    miningCoordinator.setStratumMiningEnabled(miningParameters.isStratumMiningEnabled());
    if (miningParameters.isMiningEnabled()) {
      miningCoordinator.enable();
    }

    return miningCoordinator;
  }
}
