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

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.BftFork;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.common.BftValidatorOverrides;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.bft.BftEventQueue;
import org.hyperledger.besu.consensus.common.bft.BftExecutors;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftProcessor;
import org.hyperledger.besu.consensus.common.bft.BlockTimer;
import org.hyperledger.besu.consensus.common.bft.EthSynchronizerUpdater;
import org.hyperledger.besu.consensus.common.bft.EventMultiplexer;
import org.hyperledger.besu.consensus.common.bft.MessageTracker;
import org.hyperledger.besu.consensus.common.bft.RoundTimer;
import org.hyperledger.besu.consensus.common.bft.UniqueMessageMulticaster;
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftBlockCreatorFactory;
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftMiningCoordinator;
import org.hyperledger.besu.consensus.common.bft.blockcreation.ProposerSelector;
import org.hyperledger.besu.consensus.common.bft.network.ValidatorPeers;
import org.hyperledger.besu.consensus.common.bft.protocol.BftProtocolManager;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftEventHandler;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftFinalState;
import org.hyperledger.besu.consensus.common.bft.statemachine.FutureMessageBuffer;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.blockbased.BlockValidatorProvider;
import org.hyperledger.besu.consensus.ibft.IbftExtraDataCodec;
import org.hyperledger.besu.consensus.ibft.IbftForksSchedulesFactory;
import org.hyperledger.besu.consensus.ibft.IbftGossip;
import org.hyperledger.besu.consensus.ibft.IbftProtocolSchedule;
import org.hyperledger.besu.consensus.ibft.jsonrpc.IbftJsonRpcMethods;
import org.hyperledger.besu.consensus.ibft.payload.MessageFactory;
import org.hyperledger.besu.consensus.ibft.protocol.IbftSubProtocol;
import org.hyperledger.besu.consensus.ibft.statemachine.IbftBlockHeightManagerFactory;
import org.hyperledger.besu.consensus.ibft.statemachine.IbftController;
import org.hyperledger.besu.consensus.ibft.statemachine.IbftRoundFactory;
import org.hyperledger.besu.consensus.ibft.validation.MessageValidatorFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.SnapProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.Subscribers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IbftBesuControllerBuilder extends BftBesuControllerBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(IbftBesuControllerBuilder.class);
  private BftEventQueue bftEventQueue;
  private BftConfigOptions bftConfig;
  private ForksSchedule<BftConfigOptions> forksSchedule;
  private ValidatorPeers peers;

  @Override
  protected Supplier<BftExtraDataCodec> bftExtraDataCodec() {
    return Suppliers.memoize(IbftExtraDataCodec::new);
  }

  @Override
  protected void prepForBuild() {
    bftConfig = configOptionsSupplier.get().getBftConfigOptions();
    bftEventQueue = new BftEventQueue(bftConfig.getMessageQueueLimit());
    forksSchedule = IbftForksSchedulesFactory.create(configOptionsSupplier.get());
  }

  @Override
  protected JsonRpcMethods createAdditionalJsonRpcMethodFactory(
      final ProtocolContext protocolContext) {
    return new IbftJsonRpcMethods(protocolContext);
  }

  @Override
  protected SubProtocolConfiguration createSubProtocolConfiguration(
      final EthProtocolManager ethProtocolManager,
      final Optional<SnapProtocolManager> maybeSnapProtocolManager) {
    final SubProtocolConfiguration subProtocolConfiguration =
        new SubProtocolConfiguration()
            .withSubProtocol(EthProtocol.get(), ethProtocolManager)
            .withSubProtocol(
                IbftSubProtocol.get(),
                new BftProtocolManager(
                    bftEventQueue, peers, IbftSubProtocol.IBFV1, IbftSubProtocol.get().getName()));
    maybeSnapProtocolManager.ifPresent(
        snapProtocolManager -> {
          subProtocolConfiguration.withSubProtocol(SnapProtocol.get(), snapProtocolManager);
        });
    return subProtocolConfiguration;
  }

  @Override
  protected MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionPool transactionPool,
      final MiningParameters miningParameters,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager) {
    final MutableBlockchain blockchain = protocolContext.getBlockchain();
    final BftExecutors bftExecutors =
        BftExecutors.create(metricsSystem, BftExecutors.ConsensusType.IBFT);

    final Address localAddress = Util.publicKeyToAddress(nodeKey.getPublicKey());
    final BftBlockCreatorFactory<?> blockCreatorFactory =
        new BftBlockCreatorFactory<>(
            transactionPool.getPendingTransactions(),
            protocolContext,
            protocolSchedule,
            forksSchedule,
            miningParameters,
            localAddress,
            bftExtraDataCodec().get());

    final ValidatorProvider validatorProvider =
        protocolContext.getConsensusContext(BftContext.class).getValidatorProvider();

    final ProposerSelector proposerSelector =
        new ProposerSelector(blockchain, bftBlockInterface().get(), true, validatorProvider);

    // NOTE: peers should not be used for accessing the network as it does not enforce the
    // "only send once" filter applied by the UniqueMessageMulticaster.
    peers = new ValidatorPeers(validatorProvider, IbftSubProtocol.NAME);

    final UniqueMessageMulticaster uniqueMessageMulticaster =
        new UniqueMessageMulticaster(peers, bftConfig.getGossipedHistoryLimit());

    final IbftGossip gossiper = new IbftGossip(uniqueMessageMulticaster);

    final BftFinalState finalState =
        new BftFinalState(
            validatorProvider,
            nodeKey,
            Util.publicKeyToAddress(nodeKey.getPublicKey()),
            proposerSelector,
            uniqueMessageMulticaster,
            new RoundTimer(bftEventQueue, bftConfig.getRequestTimeoutSeconds(), bftExecutors),
            new BlockTimer(bftEventQueue, forksSchedule, bftExecutors, clock),
            blockCreatorFactory,
            clock);

    final MessageValidatorFactory messageValidatorFactory =
        new MessageValidatorFactory(
            proposerSelector, protocolSchedule, protocolContext, bftExtraDataCodec().get());

    final Subscribers<MinedBlockObserver> minedBlockObservers = Subscribers.create();
    minedBlockObservers.subscribe(ethProtocolManager);
    minedBlockObservers.subscribe(blockLogger(transactionPool, localAddress));

    final FutureMessageBuffer futureMessageBuffer =
        new FutureMessageBuffer(
            bftConfig.getFutureMessagesMaxDistance(),
            bftConfig.getFutureMessagesLimit(),
            blockchain.getChainHeadBlockNumber());
    final MessageTracker duplicateMessageTracker =
        new MessageTracker(bftConfig.getDuplicateMessageLimit());

    final MessageFactory messageFactory = new MessageFactory(nodeKey);

    final BftEventHandler ibftController =
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
                    messageValidatorFactory,
                    messageFactory,
                    bftExtraDataCodec().get()),
                messageValidatorFactory,
                messageFactory),
            gossiper,
            duplicateMessageTracker,
            futureMessageBuffer,
            new EthSynchronizerUpdater(ethProtocolManager.ethContext().getEthPeers()));

    final EventMultiplexer eventMultiplexer = new EventMultiplexer(ibftController);
    final BftProcessor bftProcessor = new BftProcessor(bftEventQueue, eventMultiplexer);

    final MiningCoordinator ibftMiningCoordinator =
        new BftMiningCoordinator(
            bftExecutors,
            ibftController,
            bftProcessor,
            blockCreatorFactory,
            blockchain,
            bftEventQueue);
    ibftMiningCoordinator.enable();

    return ibftMiningCoordinator;
  }

  @Override
  protected PluginServiceFactory createAdditionalPluginServices(
      final Blockchain blockchain, final ProtocolContext protocolContext) {
    final ValidatorProvider validatorProvider =
        protocolContext.getConsensusContext(BftContext.class).getValidatorProvider();
    return new IbftQueryPluginServiceFactory(
        blockchain, bftBlockInterface().get(), validatorProvider, nodeKey);
  }

  @Override
  protected ProtocolSchedule createProtocolSchedule() {
    return IbftProtocolSchedule.create(
        configOptionsSupplier.get(),
        forksSchedule,
        privacyParameters,
        isRevertReasonEnabled,
        bftExtraDataCodec().get(),
        evmConfiguration);
  }

  @Override
  protected void validateContext(final ProtocolContext context) {
    final BlockHeader genesisBlockHeader = context.getBlockchain().getGenesisBlock().getHeader();

    if (bftBlockInterface().get().validatorsInBlock(genesisBlockHeader).isEmpty()) {
      LOG.warn("Genesis block contains no signers - chain will not progress.");
    }
  }

  @Override
  protected BftContext createConsensusContext(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {
    final GenesisConfigOptions configOptions = configOptionsSupplier.get();
    final BftConfigOptions ibftConfig = configOptions.getBftConfigOptions();
    final EpochManager epochManager = new EpochManager(ibftConfig.getEpochLength());

    final BftValidatorOverrides validatorOverrides =
        convertIbftForks(configOptions.getTransitions().getIbftForks());

    return new BftContext(
        BlockValidatorProvider.forkingValidatorProvider(
            blockchain, epochManager, bftBlockInterface().get(), validatorOverrides),
        epochManager,
        bftBlockInterface().get());
  }

  private BftValidatorOverrides convertIbftForks(final List<BftFork> bftForks) {
    final Map<Long, List<Address>> result = new HashMap<>();

    for (final BftFork fork : bftForks) {
      fork.getValidators()
          .ifPresent(
              validators ->
                  result.put(
                      fork.getForkBlock(),
                      validators.stream()
                          .map(Address::fromHexString)
                          .collect(Collectors.toList())));
    }

    return new BftValidatorOverrides(result);
  }

  private static MinedBlockObserver blockLogger(
      final TransactionPool transactionPool, final Address localAddress) {
    return block ->
        LOG.info(
            String.format(
                "%s #%,d / %d tx / %d pending / %,d (%01.1f%%) gas / (%s)",
                block.getHeader().getCoinbase().equals(localAddress) ? "Produced" : "Imported",
                block.getHeader().getNumber(),
                block.getBody().getTransactions().size(),
                transactionPool.getPendingTransactions().size(),
                block.getHeader().getGasUsed(),
                (block.getHeader().getGasUsed() * 100.0) / block.getHeader().getGasLimit(),
                block.getHash().toHexString()));
  }
}
