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

import tech.pegasys.pantheon.config.CliqueConfigOptions;
import tech.pegasys.pantheon.consensus.clique.CliqueBlockInterface;
import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.CliqueMiningTracker;
import tech.pegasys.pantheon.consensus.clique.CliqueProtocolSchedule;
import tech.pegasys.pantheon.consensus.clique.blockcreation.CliqueBlockScheduler;
import tech.pegasys.pantheon.consensus.clique.blockcreation.CliqueMinerExecutor;
import tech.pegasys.pantheon.consensus.clique.blockcreation.CliqueMiningCoordinator;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.CliqueJsonRpcMethodsFactory;
import tech.pegasys.pantheon.consensus.common.BlockInterface;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTallyCache;
import tech.pegasys.pantheon.consensus.common.VoteTallyUpdater;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethodFactory;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CliquePantheonControllerBuilder extends PantheonControllerBuilder<CliqueContext> {
  private static final Logger LOG = LogManager.getLogger();

  private Address localAddress;
  private EpochManager epochManager;
  private long secondsBetweenBlocks;
  private final BlockInterface blockInterface = new CliqueBlockInterface();

  @Override
  protected void prepForBuild() {
    localAddress = Util.publicKeyToAddress(nodeKeys.getPublicKey());
    final CliqueConfigOptions cliqueConfig =
        genesisConfig.getConfigOptions(genesisConfigOverrides).getCliqueConfigOptions();
    final long blocksPerEpoch = cliqueConfig.getEpochLength();
    secondsBetweenBlocks = cliqueConfig.getBlockPeriodSeconds();

    epochManager = new EpochManager(blocksPerEpoch);
  }

  @Override
  protected JsonRpcMethodFactory createAdditionalJsonRpcMethodFactory(
      final ProtocolContext<CliqueContext> protocolContext) {
    return new CliqueJsonRpcMethodsFactory(protocolContext);
  }

  @Override
  protected MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule<CliqueContext> protocolSchedule,
      final ProtocolContext<CliqueContext> protocolContext,
      final TransactionPool transactionPool,
      final MiningParameters miningParameters,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager) {
    final ExecutorService minerThreadPool = Executors.newCachedThreadPool();
    final CliqueMinerExecutor miningExecutor =
        new CliqueMinerExecutor(
            protocolContext,
            minerThreadPool,
            protocolSchedule,
            transactionPool.getPendingTransactions(),
            nodeKeys,
            miningParameters,
            new CliqueBlockScheduler(
                clock,
                protocolContext.getConsensusState().getVoteTallyCache(),
                localAddress,
                secondsBetweenBlocks),
            epochManager);
    final CliqueMiningCoordinator miningCoordinator =
        new CliqueMiningCoordinator(
            protocolContext.getBlockchain(),
            miningExecutor,
            syncState,
            new CliqueMiningTracker(localAddress, protocolContext));
    miningCoordinator.addMinedBlockObserver(ethProtocolManager);

    // Clique mining is implicitly enabled.
    miningCoordinator.enable();
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
  protected ProtocolSchedule<CliqueContext> createProtocolSchedule() {
    return CliqueProtocolSchedule.create(
        genesisConfig.getConfigOptions(genesisConfigOverrides),
        nodeKeys,
        privacyParameters,
        isRevertReasonEnabled);
  }

  @Override
  protected void validateContext(final ProtocolContext<CliqueContext> context) {
    final BlockHeader genesisBlockHeader = context.getBlockchain().getGenesisBlock().getHeader();

    if (blockInterface.validatorsInBlock(genesisBlockHeader).isEmpty()) {
      LOG.warn("Genesis block contains no signers - chain will not progress.");
    }
  }

  @Override
  protected CliqueContext createConsensusContext(
      final Blockchain blockchain, final WorldStateArchive worldStateArchive) {
    return new CliqueContext(
        new VoteTallyCache(
            blockchain,
            new VoteTallyUpdater(epochManager, blockInterface),
            epochManager,
            blockInterface),
        new VoteProposer(),
        epochManager);
  }
}
