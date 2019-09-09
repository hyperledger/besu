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

import static tech.pegasys.pantheon.ethereum.eth.manager.MonitoredExecutors.newScheduledThreadPool;

import tech.pegasys.pantheon.config.IbftConfigOptions;
import tech.pegasys.pantheon.consensus.common.BlockInterface;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTallyCache;
import tech.pegasys.pantheon.consensus.common.VoteTallyUpdater;
import tech.pegasys.pantheon.consensus.ibft.BlockTimer;
import tech.pegasys.pantheon.consensus.ibft.EthSynchronizerUpdater;
import tech.pegasys.pantheon.consensus.ibft.EventMultiplexer;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockInterface;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.IbftEventQueue;
import tech.pegasys.pantheon.consensus.ibft.IbftGossip;
import tech.pegasys.pantheon.consensus.ibft.IbftProcessor;
import tech.pegasys.pantheon.consensus.ibft.IbftProtocolSchedule;
import tech.pegasys.pantheon.consensus.ibft.MessageTracker;
import tech.pegasys.pantheon.consensus.ibft.RoundTimer;
import tech.pegasys.pantheon.consensus.ibft.UniqueMessageMulticaster;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.IbftBlockCreatorFactory;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.IbftMiningCoordinator;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.ProposerSelector;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.IbftJsonRpcMethodsFactory;
import tech.pegasys.pantheon.consensus.ibft.network.ValidatorPeers;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.protocol.IbftProtocolManager;
import tech.pegasys.pantheon.consensus.ibft.protocol.IbftSubProtocol;
import tech.pegasys.pantheon.consensus.ibft.statemachine.FutureMessageBuffer;
import tech.pegasys.pantheon.consensus.ibft.statemachine.IbftBlockHeightManagerFactory;
import tech.pegasys.pantheon.consensus.ibft.statemachine.IbftController;
import tech.pegasys.pantheon.consensus.ibft.statemachine.IbftFinalState;
import tech.pegasys.pantheon.consensus.ibft.statemachine.IbftRoundFactory;
import tech.pegasys.pantheon.consensus.ibft.validation.MessageValidatorFactory;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethodFactory;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.util.Subscribers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IbftPantheonControllerBuilder extends PantheonControllerBuilder<IbftContext> {
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
  protected JsonRpcMethodFactory createAdditionalJsonRpcMethodFactory(
      final ProtocolContext<IbftContext> protocolContext) {
    return new IbftJsonRpcMethodsFactory(protocolContext);
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

    final IbftBlockCreatorFactory blockCreatorFactory =
        new IbftBlockCreatorFactory(
            (gasLimit) -> gasLimit,
            transactionPool.getPendingTransactions(),
            protocolContext,
            protocolSchedule,
            miningParameters,
            Util.publicKeyToAddress(nodeKeys.getPublicKey()));

    final ProposerSelector proposerSelector =
        new ProposerSelector(blockchain, blockInterface, true);

    // NOTE: peers should not be used for accessing the network as it does not enforce the
    // "only send once" filter applied by the UniqueMessageMulticaster.
    final VoteTallyCache voteTallyCache = protocolContext.getConsensusState().getVoteTallyCache();
    peers = new ValidatorPeers(voteTallyCache);

    final UniqueMessageMulticaster uniqueMessageMulticaster =
        new UniqueMessageMulticaster(peers, ibftConfig.getGossipedHistoryLimit());

    final IbftGossip gossiper = new IbftGossip(uniqueMessageMulticaster);

    final ScheduledExecutorService timerExecutor =
        newScheduledThreadPool("IbftTimerExecutor", 1, metricsSystem);

    final IbftFinalState finalState =
        new IbftFinalState(
            voteTallyCache,
            nodeKeys,
            Util.publicKeyToAddress(nodeKeys.getPublicKey()),
            proposerSelector,
            uniqueMessageMulticaster,
            new RoundTimer(ibftEventQueue, ibftConfig.getRequestTimeoutSeconds(), timerExecutor),
            new BlockTimer(
                ibftEventQueue, ibftConfig.getBlockPeriodSeconds(), timerExecutor, clock),
            blockCreatorFactory,
            new MessageFactory(nodeKeys),
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
    final ExecutorService processorExecutor = Executors.newSingleThreadExecutor();
    processorExecutor.execute(ibftProcessor);

    final MiningCoordinator ibftMiningCoordinator =
        new IbftMiningCoordinator(ibftProcessor, blockCreatorFactory, blockchain, ibftEventQueue);
    ibftMiningCoordinator.enable();
    addShutdownAction(
        () -> {
          ibftProcessor.stop();
          ibftMiningCoordinator.disable();
          processorExecutor.shutdownNow();
          try {
            processorExecutor.awaitTermination(5, TimeUnit.SECONDS);
          } catch (final InterruptedException e) {
            LOG.error("Failed to shutdown ibft processor executor");
          }
          timerExecutor.shutdownNow();
          try {
            timerExecutor.awaitTermination(5, TimeUnit.SECONDS);
          } catch (final InterruptedException e) {
            LOG.error("Failed to shutdown timer executor");
          }
        });
    return ibftMiningCoordinator;
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
    final IbftConfigOptions ibftConfig =
        genesisConfig.getConfigOptions(genesisConfigOverrides).getIbft2ConfigOptions();
    final EpochManager epochManager = new EpochManager(ibftConfig.getEpochLength());
    return new IbftContext(
        new VoteTallyCache(
            blockchain,
            new VoteTallyUpdater(epochManager, new IbftBlockInterface()),
            epochManager,
            new IbftBlockInterface()),
        new VoteProposer());
  }
}
