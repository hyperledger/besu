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

import org.hyperledger.besu.config.CliqueConfigOptions;
import org.hyperledger.besu.consensus.clique.CliqueBlockInterface;
import org.hyperledger.besu.consensus.clique.CliqueContext;
import org.hyperledger.besu.consensus.clique.CliqueMiningTracker;
import org.hyperledger.besu.consensus.clique.CliqueProtocolSchedule;
import org.hyperledger.besu.consensus.clique.blockcreation.CliqueBlockScheduler;
import org.hyperledger.besu.consensus.clique.blockcreation.CliqueMinerExecutor;
import org.hyperledger.besu.consensus.clique.blockcreation.CliqueMiningCoordinator;
import org.hyperledger.besu.consensus.clique.jsonrpc.CliqueJsonRpcMethods;
import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.validator.blockbased.BlockValidatorProvider;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CliqueBesuControllerBuilder extends BesuControllerBuilder {

  private static final Logger LOG = LogManager.getLogger();

  private Address localAddress;
  private EpochManager epochManager;
  private long secondsBetweenBlocks;
  private final BlockInterface blockInterface = new CliqueBlockInterface();

  @Override
  protected void prepForBuild() {
    localAddress = Util.publicKeyToAddress(nodeKey.getPublicKey());
    final CliqueConfigOptions cliqueConfig =
        genesisConfig.getConfigOptions(genesisConfigOverrides).getCliqueConfigOptions();
    final long blocksPerEpoch = cliqueConfig.getEpochLength();
    secondsBetweenBlocks = cliqueConfig.getBlockPeriodSeconds();

    epochManager = new EpochManager(blocksPerEpoch);
  }

  @Override
  protected JsonRpcMethods createAdditionalJsonRpcMethodFactory(
      final ProtocolContext protocolContext) {
    return new CliqueJsonRpcMethods(protocolContext);
  }

  @Override
  protected MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionPool transactionPool,
      final MiningParameters miningParameters,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager) {
    final CliqueMinerExecutor miningExecutor =
        new CliqueMinerExecutor(
            protocolContext,
            protocolSchedule,
            transactionPool.getPendingTransactions(),
            nodeKey,
            miningParameters,
            new CliqueBlockScheduler(
                clock,
                protocolContext.getConsensusState(CliqueContext.class).getValidatorProvider(),
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
    return miningCoordinator;
  }

  @Override
  protected ProtocolSchedule createProtocolSchedule() {
    return CliqueProtocolSchedule.create(
        genesisConfig.getConfigOptions(genesisConfigOverrides),
        nodeKey,
        privacyParameters,
        isRevertReasonEnabled);
  }

  @Override
  protected void validateContext(final ProtocolContext context) {
    final BlockHeader genesisBlockHeader = context.getBlockchain().getGenesisBlock().getHeader();

    if (blockInterface.validatorsInBlock(genesisBlockHeader).isEmpty()) {
      LOG.warn("Genesis block contains no signers - chain will not progress.");
    }
  }

  @Override
  protected PluginServiceFactory createAdditionalPluginServices(
      final Blockchain blockchain, final ProtocolContext protocolContext) {
    return new CliqueQueryPluginServiceFactory(blockchain, nodeKey);
  }

  @Override
  protected CliqueContext createConsensusContext(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {
    return new CliqueContext(
        BlockValidatorProvider.nonForkingValidatorProvider(
            blockchain, epochManager, blockInterface),
        epochManager,
        blockInterface);
  }
}
