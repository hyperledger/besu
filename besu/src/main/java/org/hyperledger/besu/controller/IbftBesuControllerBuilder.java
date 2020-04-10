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

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.IbftConfigOptions;
import org.hyperledger.besu.config.IbftFork;
import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.ForkingVoteTallyCache;
import org.hyperledger.besu.consensus.common.IbftValidatorOverrides;
import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.consensus.common.VoteTallyUpdater;
import org.hyperledger.besu.consensus.ibft.BlockTimer;
import org.hyperledger.besu.consensus.ibft.EthSynchronizerUpdater;
import org.hyperledger.besu.consensus.ibft.EventMultiplexer;
import org.hyperledger.besu.consensus.ibft.IbftBlockInterface;
import org.hyperledger.besu.consensus.ibft.IbftContext;
import org.hyperledger.besu.consensus.ibft.IbftEventQueue;
import org.hyperledger.besu.consensus.ibft.IbftExecutors;
import org.hyperledger.besu.consensus.ibft.IbftGossip;
import org.hyperledger.besu.consensus.ibft.IbftProcessor;
import org.hyperledger.besu.consensus.ibft.IbftProtocolSchedule;
import org.hyperledger.besu.consensus.ibft.MessageTracker;
import org.hyperledger.besu.consensus.ibft.RoundTimer;
import org.hyperledger.besu.consensus.ibft.UniqueMessageMulticaster;
import org.hyperledger.besu.consensus.ibft.blockcreation.IbftBlockCreatorFactory;
import org.hyperledger.besu.consensus.ibft.blockcreation.IbftMiningCoordinator;
import org.hyperledger.besu.consensus.ibft.blockcreation.ProposerSelector;
import org.hyperledger.besu.consensus.ibft.jsonrpc.IbftJsonRpcMethods;
import org.hyperledger.besu.consensus.ibft.network.ValidatorPeers;
import org.hyperledger.besu.consensus.ibft.payload.MessageFactory;
import org.hyperledger.besu.consensus.ibft.protocol.IbftProtocolManager;
import org.hyperledger.besu.consensus.ibft.protocol.IbftSubProtocol;
import org.hyperledger.besu.consensus.ibft.statemachine.FutureMessageBuffer;
import org.hyperledger.besu.consensus.ibft.statemachine.IbftBlockHeightManagerFactory;
import org.hyperledger.besu.consensus.ibft.statemachine.IbftController;
import org.hyperledger.besu.consensus.ibft.statemachine.IbftFinalState;
import org.hyperledger.besu.consensus.ibft.statemachine.IbftRoundFactory;
import org.hyperledger.besu.consensus.ibft.validation.MessageValidatorFactory;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.Subscribers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IbftBesuControllerBuilder extends BesuControllerBuilder<IbftContext> {

  private static final Logger LOG = LogManager.getLogger();
  private IbftEventQueue ibftEventQueue;
  private IbftConfigOptions ibftConfig;
  private ValidatorPeers peers;
  private final BlockInterface blockInterface = new IbftBlockInterface();

  @Override
  protected void prepForBuild() {
    ibftConfig = genesisConfig.getConfigOptions(genesisConfigOverrides).getIbft2ConfigOptions();
    ibftEventQueue = new IbftEventQueue(ibftConfig.getMessageQueueLimit());
  }

  @Override
  protected JsonRpcMethods createAdditionalJsonRpcMethodFactory(
      final ProtocolContext<IbftContext> protocolContext) {
    return new IbftJsonRpcMethods(protocolContext);
  }

  @Override
  protected SubProtocolConfiguration createSubProtocolConfiguration(
      final EthProtocolManager ethProtocolManager) {
    return new SubProtocolConfiguration()
        .withSubProtocol(EthProtocol.get(), ethProtocolManager)
        .withSubProtocol(IbftSubProtocol.get(), new IbftProtocolManager(ibftEventQueue, peers));
  }

  @Override
  protected MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule<IbftContext> protocolSchedule,
      final ProtocolContext<IbftContext> protocolContext,
      final TransactionPool transactionPool,
      final MiningParameters miningParameters,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager) {
    final MutableBlockchain blockchain = protocolContext.getBlockchain();
    final IbftExecutors ibftExecutors = IbftExecutors.create(metricsSystem);

    final IbftBlockCreatorFactory blockCreatorFactory =
        new IbftBlockCreatorFactory(
            gasLimitCalculator,
            transactionPool.getPendingTransactions(),
            protocolContext,
            protocolSchedule,
            miningParameters,
            Util.publicKeyToAddress(nodeKey.getPublicKey()));

    // NOTE: peers should not be used for accessing the network as it does not enforce the
    // "only send once" filter applied by the UniqueMessageMulticaster.
    final VoteTallyCache voteTallyCache = protocolContext.getConsensusState().getVoteTallyCache();

    final ProposerSelector proposerSelector =
        new ProposerSelector(blockchain, blockInterface, true, voteTallyCache);

    peers = new ValidatorPeers(voteTallyCache);

    final UniqueMessageMulticaster uniqueMessageMulticaster =
        new UniqueMessageMulticaster(peers, ibftConfig.getGossipedHistoryLimit());

    final IbftGossip gossiper = new IbftGossip(uniqueMessageMulticaster);

    final IbftFinalState finalState =
        new IbftFinalState(
            voteTallyCache,
            nodeKey,
            Util.publicKeyToAddress(nodeKey.getPublicKey()),
            proposerSelector,
            uniqueMessageMulticaster,
            new RoundTimer(ibftEventQueue, ibftConfig.getRequestTimeoutSeconds(), ibftExecutors),
            new BlockTimer(
                ibftEventQueue, ibftConfig.getBlockPeriodSeconds(), ibftExecutors, clock),
            blockCreatorFactory,
            new MessageFactory(nodeKey),
            clock);

    final MessageValidatorFactory messageValidatorFactory =
        new MessageValidatorFactory(proposerSelector, protocolSchedule, protocolContext);

    final Subscribers<MinedBlockObserver> minedBlockObservers = Subscribers.create();
    minedBlockObservers.subscribe(ethProtocolManager);

    final FutureMessageBuffer futureMessageBuffer =
        new FutureMessageBuffer(
            ibftConfig.getFutureMessagesMaxDistance(),
            ibftConfig.getFutureMessagesLimit(),
            blockchain.getChainHeadBlockNumber());
    final MessageTracker duplicateMessageTracker =
        new MessageTracker(ibftConfig.getDuplicateMessageLimit());

    final IbftController ibftController =
        new IbftController(
            blockchain,
            finalState,
            new IbftBlockHeightManagerFactory(
                finalState,
                new IbftRoundFactory(
                    finalState,
                    protocolContext,
                    protocolSchedule,
                    minedBlockObservers,
                    messageValidatorFactory),
                messageValidatorFactory),
            gossiper,
            duplicateMessageTracker,
            futureMessageBuffer,
            new EthSynchronizerUpdater(ethProtocolManager.ethContext().getEthPeers()));

    final EventMultiplexer eventMultiplexer = new EventMultiplexer(ibftController);
    final IbftProcessor ibftProcessor = new IbftProcessor(ibftEventQueue, eventMultiplexer);

    final MiningCoordinator ibftMiningCoordinator =
        new IbftMiningCoordinator(
            ibftExecutors,
            ibftController,
            ibftProcessor,
            blockCreatorFactory,
            blockchain,
            ibftEventQueue);
    ibftMiningCoordinator.enable();

    return ibftMiningCoordinator;
  }

  @Override
  protected PluginServiceFactory createAdditionalPluginServices(final Blockchain blockchain) {
    return new IbftQueryPluginServiceFactory(blockchain, nodeKey);
  }

  @Override
  protected ProtocolSchedule<IbftContext> createProtocolSchedule() {
    return IbftProtocolSchedule.create(
        genesisConfig.getConfigOptions(genesisConfigOverrides),
        privacyParameters,
        isRevertReasonEnabled);
  }

  @Override
  protected void validateContext(final ProtocolContext<IbftContext> context) {
    final BlockHeader genesisBlockHeader = context.getBlockchain().getGenesisBlock().getHeader();

    if (blockInterface.validatorsInBlock(genesisBlockHeader).isEmpty()) {
      LOG.warn("Genesis block contains no signers - chain will not progress.");
    }
  }

  @Override
  protected IbftContext createConsensusContext(
      final Blockchain blockchain, final WorldStateArchive worldStateArchive) {
    final GenesisConfigOptions configOptions =
        genesisConfig.getConfigOptions(genesisConfigOverrides);
    final IbftConfigOptions ibftConfig = configOptions.getIbft2ConfigOptions();
    final EpochManager epochManager = new EpochManager(ibftConfig.getEpochLength());

    final Map<Long, List<Address>> ibftValidatorForkMap =
        convertIbftForks(configOptions.getTransitions().getIbftForks());

    return new IbftContext(
        new ForkingVoteTallyCache(
            blockchain,
            new VoteTallyUpdater(epochManager, new IbftBlockInterface()),
            epochManager,
            new IbftBlockInterface(),
            new IbftValidatorOverrides(ibftValidatorForkMap)),
        new VoteProposer(),
        epochManager,
        blockInterface);
  }

  private Map<Long, List<Address>> convertIbftForks(final List<IbftFork> ibftForks) {
    final Map<Long, List<Address>> result = new HashMap<>();

    for (final IbftFork fork : ibftForks) {
      fork.getValidators()
          .map(
              validators ->
                  result.put(
                      fork.getForkBlock(),
                      validators.stream()
                          .map(Address::fromHexString)
                          .collect(Collectors.toList())));
    }

    return result;
  }
}
