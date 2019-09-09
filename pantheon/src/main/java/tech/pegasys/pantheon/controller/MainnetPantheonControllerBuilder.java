/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.controller;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.DefaultBlockScheduler;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMinerExecutor;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHeaderValidator;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MainnetPantheonControllerBuilder extends PantheonControllerBuilder<Void> {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  protected MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule<Void> protocolSchedule,
      final ProtocolContext<Void> protocolContext,
      final TransactionPool transactionPool,
      final MiningParameters miningParameters,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager) {
    final ExecutorService minerThreadPool = Executors.newCachedThreadPool();
    final EthHashMinerExecutor executor =
        new EthHashMinerExecutor(
            protocolContext,
            minerThreadPool,
            protocolSchedule,
            transactionPool.getPendingTransactions(),
            miningParameters,
            new DefaultBlockScheduler(
                MainnetBlockHeaderValidator.MINIMUM_SECONDS_SINCE_PARENT,
                MainnetBlockHeaderValidator.TIMESTAMP_TOLERANCE_S,
                clock));

    final EthHashMiningCoordinator miningCoordinator =
        new EthHashMiningCoordinator(protocolContext.getBlockchain(), executor, syncState);
    miningCoordinator.addMinedBlockObserver(ethProtocolManager);
    if (miningParameters.isMiningEnabled()) {
      miningCoordinator.enable();
    }
    addShutdownAction(
        () -> {
          miningCoordinator.disable();
          minerThreadPool.shutdownNow();
          try {
            minerThreadPool.awaitTermination(5, TimeUnit.SECONDS);
          } catch (final InterruptedException e) {
            LOG.error("Failed to shutdown miner executor");
          }
        });
    return miningCoordinator;
  }

  @Override
  protected Void createConsensusContext(
      final Blockchain blockchain, final WorldStateArchive worldStateArchive) {
    return null;
  }

  @Override
  protected ProtocolSchedule<Void> createProtocolSchedule() {
    return MainnetProtocolSchedule.fromConfig(
        genesisConfig.getConfigOptions(genesisConfigOverrides),
        privacyParameters,
        isRevertReasonEnabled);
  }
}
