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

import org.hyperledger.besu.config.BftFork;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.config.QbftFork;
import org.hyperledger.besu.consensus.common.BftValidatorOverrides;
import org.hyperledger.besu.consensus.common.EpochManager;
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
import org.hyperledger.besu.consensus.qbft.QbftBlockHeaderValidationRulesetFactory;
import org.hyperledger.besu.consensus.qbft.QbftContext;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.QbftGossip;
import org.hyperledger.besu.consensus.qbft.QbftProtocolSchedule;
import org.hyperledger.besu.consensus.qbft.blockcreation.QbftBlockCreatorFactory;
import org.hyperledger.besu.consensus.qbft.jsonrpc.QbftJsonRpcMethods;
import org.hyperledger.besu.consensus.qbft.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.protocol.Istanbul100SubProtocol;
import org.hyperledger.besu.consensus.qbft.statemachine.QbftBlockHeightManagerFactory;
import org.hyperledger.besu.consensus.qbft.statemachine.QbftController;
import org.hyperledger.besu.consensus.qbft.statemachine.QbftRoundFactory;
import org.hyperledger.besu.consensus.qbft.validation.MessageValidatorFactory;
import org.hyperledger.besu.consensus.qbft.validator.ForkingValidatorProvider;
import org.hyperledger.besu.consensus.qbft.validator.TransactionValidatorProvider;
import org.hyperledger.besu.consensus.qbft.validator.ValidatorContractController;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.consensus.qbft.validator.ValidatorSelectorConfig;
import org.hyperledger.besu.consensus.qbft.validator.ValidatorSelectorForksSchedule;
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
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.Subscribers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Suppliers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class QbftBesuControllerBuilder extends BftBesuControllerBuilder {

  private static final Logger LOG = LogManager.getLogger();
  private BftEventQueue bftEventQueue;
  private QbftConfigOptions qbftConfig;
  private ValidatorSelectorForksSchedule qbftForksSchedule;
  private ValidatorPeers peers;

  @Override
  protected Supplier<BftExtraDataCodec> bftExtraDataCodec() {
    return Suppliers.memoize(
        () -> {
          if (pkiBlockCreationConfiguration.isPresent()) {
            return new PkiQbftExtraDataCodec();
          } else {
            return new QbftExtraDataCodec();
          }
        });
  }

  @Override
  protected void prepForBuild() {
    qbftConfig = genesisConfig.getConfigOptions(genesisConfigOverrides).getQbftConfigOptions();
    bftEventQueue = new BftEventQueue(qbftConfig.getMessageQueueLimit());
    qbftForksSchedule = createQbftForksSchedule(genesisConfig.getConfigOptions());
  }

  @Override
  protected JsonRpcMethods createAdditionalJsonRpcMethodFactory(
      final ProtocolContext protocolContext) {
    return new QbftJsonRpcMethods(protocolContext);
  }

  @Override
  protected SubProtocolConfiguration createSubProtocolConfiguration(
      final EthProtocolManager ethProtocolManager) {
    return new SubProtocolConfiguration()
        .withSubProtocol(EthProtocol.get(), ethProtocolManager)
        .withSubProtocol(
            Istanbul100SubProtocol.get(),
            new BftProtocolManager(
                bftEventQueue,
                peers,
                Istanbul100SubProtocol.ISTANBUL_100,
                Istanbul100SubProtocol.get().getName()));
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
    final BftExecutors bftExecutors = BftExecutors.create(metricsSystem);

    final Address localAddress = Util.publicKeyToAddress(nodeKey.getPublicKey());
    final BftBlockCreatorFactory blockCreatorFactory =
        new QbftBlockCreatorFactory(
            transactionPool.getPendingTransactions(),
            protocolContext,
            protocolSchedule,
            miningParameters,
            localAddress,
            qbftConfig.getMiningBeneficiary().map(Address::fromHexString).orElse(localAddress),
            bftExtraDataCodec().get(),
            qbftForksSchedule);

    final ValidatorProvider validatorProvider =
        protocolContext.getConsensusState(BftContext.class).getValidatorProvider();

    final ProposerSelector proposerSelector =
        new ProposerSelector(blockchain, bftBlockInterface().get(), true, validatorProvider);

    // NOTE: peers should not be used for accessing the network as it does not enforce the
    // "only send once" filter applied by the UniqueMessageMulticaster.
    peers = new ValidatorPeers(validatorProvider, Istanbul100SubProtocol.NAME);

    final UniqueMessageMulticaster uniqueMessageMulticaster =
        new UniqueMessageMulticaster(peers, qbftConfig.getGossipedHistoryLimit());

    final QbftGossip gossiper = new QbftGossip(uniqueMessageMulticaster, bftExtraDataCodec().get());

    final BftFinalState finalState =
        new BftFinalState(
            validatorProvider,
            nodeKey,
            Util.publicKeyToAddress(nodeKey.getPublicKey()),
            proposerSelector,
            uniqueMessageMulticaster,
            new RoundTimer(bftEventQueue, qbftConfig.getRequestTimeoutSeconds(), bftExecutors),
            new BlockTimer(bftEventQueue, qbftConfig.getBlockPeriodSeconds(), bftExecutors, clock),
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
            qbftConfig.getFutureMessagesMaxDistance(),
            qbftConfig.getFutureMessagesLimit(),
            blockchain.getChainHeadBlockNumber());
    final MessageTracker duplicateMessageTracker =
        new MessageTracker(qbftConfig.getDuplicateMessageLimit());

    final MessageFactory messageFactory = new MessageFactory(nodeKey);

    final BftEventHandler qbftController =
        new QbftController(
            blockchain,
            finalState,
            new QbftBlockHeightManagerFactory(
                finalState,
                new QbftRoundFactory(
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
            new EthSynchronizerUpdater(ethProtocolManager.ethContext().getEthPeers()),
            bftExtraDataCodec().get());

    final EventMultiplexer eventMultiplexer = new EventMultiplexer(qbftController);
    final BftProcessor bftProcessor = new BftProcessor(bftEventQueue, eventMultiplexer);

    final MiningCoordinator miningCoordinator =
        new BftMiningCoordinator(
            bftExecutors,
            qbftController,
            bftProcessor,
            blockCreatorFactory,
            blockchain,
            bftEventQueue);
    miningCoordinator.enable();

    return miningCoordinator;
  }

  @Override
  protected PluginServiceFactory createAdditionalPluginServices(
      final Blockchain blockchain, final ProtocolContext protocolContext) {
    final ValidatorProvider validatorProvider =
        protocolContext.getConsensusState(BftContext.class).getValidatorProvider();
    return new BftQueryPluginServiceFactory(
        blockchain, bftExtraDataCodec().get(), validatorProvider, nodeKey, "qbft");
  }

  @Override
  protected ProtocolSchedule createProtocolSchedule() {
    final QbftBlockHeaderValidationRulesetFactory qbftBlockHeaderValidationRulesetFactory =
        new QbftBlockHeaderValidationRulesetFactory();

    return QbftProtocolSchedule.create(
        genesisConfig.getConfigOptions(genesisConfigOverrides),
        privacyParameters,
        isRevertReasonEnabled,
        qbftBlockHeaderValidationRulesetFactory::blockHeaderValidator,
        bftExtraDataCodec().get());
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
    final EpochManager epochManager = new EpochManager(qbftConfig.getEpochLength());

    final BftValidatorOverrides validatorOverrides =
        convertBftForks(genesisConfig.getConfigOptions().getTransitions().getQbftForks());
    final TransactionSimulator transactionSimulator =
        new TransactionSimulator(blockchain, worldStateArchive, protocolSchedule);

    final BlockValidatorProvider blockValidatorProvider =
        BlockValidatorProvider.forkingValidatorProvider(
            blockchain, epochManager, bftBlockInterface().get(), validatorOverrides);
    final TransactionValidatorProvider transactionValidatorProvider =
        new TransactionValidatorProvider(
            blockchain, new ValidatorContractController(transactionSimulator, qbftForksSchedule));
    final ValidatorProvider validatorProvider =
        new ForkingValidatorProvider(
            blockchain, qbftForksSchedule, blockValidatorProvider, transactionValidatorProvider);

    return new QbftContext(
        validatorProvider, epochManager, bftBlockInterface().get(), pkiBlockCreationConfiguration);
  }

  private ValidatorSelectorForksSchedule createQbftForksSchedule(
      final GenesisConfigOptions configOptions) {
    final ValidatorSelectorConfig initialFork =
        ValidatorSelectorConfig.fromQbftConfig(configOptions.getQbftConfigOptions());
    final List<ValidatorSelectorConfig> validatorSelectionForks =
        convertToValidatorSelectionConfig(
            genesisConfig.getConfigOptions().getTransitions().getQbftForks());
    return new ValidatorSelectorForksSchedule(initialFork, validatorSelectionForks);
  }

  private BftValidatorOverrides convertBftForks(final List<QbftFork> bftForks) {
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

  private List<ValidatorSelectorConfig> convertToValidatorSelectionConfig(
      final List<QbftFork> qbftForks) {
    return qbftForks.stream()
        .map(ValidatorSelectorConfig::fromQbftFork)
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
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
