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

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.config.BftFork;
import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.config.QbftFork;
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
import org.hyperledger.besu.consensus.qbft.QbftContext;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.QbftForksSchedulesFactory;
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
import org.hyperledger.besu.consensus.qbft.validator.ValidatorModeTransitionLogger;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QbftBesuControllerBuilder extends BftBesuControllerBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(QbftBesuControllerBuilder.class);
  private BftEventQueue bftEventQueue;
  private QbftConfigOptions qbftConfig;
  private ForksSchedule<QbftConfigOptions> qbftForksSchedule;
  private ValidatorPeers peers;
  private TransactionValidatorProvider transactionValidatorProvider;

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
    qbftConfig = configOptionsSupplier.get().getQbftConfigOptions();
    bftEventQueue = new BftEventQueue(qbftConfig.getMessageQueueLimit());
    qbftForksSchedule = QbftForksSchedulesFactory.create(configOptionsSupplier.get());
  }

  @Override
  protected JsonRpcMethods createAdditionalJsonRpcMethodFactory(
      final ProtocolContext protocolContext) {

    return new QbftJsonRpcMethods(
        protocolContext, createReadOnlyValidatorProvider(protocolContext.getBlockchain()));
  }

  private ValidatorProvider createReadOnlyValidatorProvider(final Blockchain blockchain) {
    checkNotNull(
        transactionValidatorProvider, "transactionValidatorProvider should have been initialised");
    final EpochManager epochManager = new EpochManager(qbftConfig.getEpochLength());
    // Must create our own voteTallyCache as using this would pollute the main voteTallyCache
    final BlockValidatorProvider readOnlyBlockValidatorProvider =
        BlockValidatorProvider.nonForkingValidatorProvider(
            blockchain, epochManager, bftBlockInterface().get());
    return new ForkingValidatorProvider(
        blockchain,
        qbftForksSchedule,
        readOnlyBlockValidatorProvider,
        transactionValidatorProvider);
  }

  @Override
  protected SubProtocolConfiguration createSubProtocolConfiguration(
      final EthProtocolManager ethProtocolManager,
      final Optional<SnapProtocolManager> maybeSnapProtocolManager) {
    final SubProtocolConfiguration subProtocolConfiguration =
        new SubProtocolConfiguration()
            .withSubProtocol(EthProtocol.get(), ethProtocolManager)
            .withSubProtocol(
                Istanbul100SubProtocol.get(),
                new BftProtocolManager(
                    bftEventQueue,
                    peers,
                    Istanbul100SubProtocol.ISTANBUL_100,
                    Istanbul100SubProtocol.get().getName()));
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
        BftExecutors.create(metricsSystem, BftExecutors.ConsensusType.QBFT);

    final Address localAddress = Util.publicKeyToAddress(nodeKey.getPublicKey());
    final BftBlockCreatorFactory<?> blockCreatorFactory =
        new QbftBlockCreatorFactory(
            transactionPool.getPendingTransactions(),
            protocolContext,
            protocolSchedule,
            qbftForksSchedule,
            miningParameters,
            localAddress,
            bftExtraDataCodec().get());

    final ValidatorProvider validatorProvider =
        protocolContext.getConsensusContext(BftContext.class).getValidatorProvider();

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
            new BlockTimer(bftEventQueue, qbftForksSchedule, bftExecutors, clock),
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
                messageFactory,
                new ValidatorModeTransitionLogger(qbftForksSchedule)),
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
        protocolContext.getConsensusContext(BftContext.class).getValidatorProvider();
    return new BftQueryPluginServiceFactory(
        blockchain, bftExtraDataCodec().get(), validatorProvider, nodeKey, "qbft");
  }

  @Override
  protected ProtocolSchedule createProtocolSchedule() {
    return QbftProtocolSchedule.create(
        configOptionsSupplier.get(),
        qbftForksSchedule,
        privacyParameters,
        isRevertReasonEnabled,
        bftExtraDataCodec().get(),
        evmConfiguration);
  }

  @Override
  protected void validateContext(final ProtocolContext context) {
    final BlockHeader genesisBlockHeader = context.getBlockchain().getGenesisBlock().getHeader();

    if (usingValidatorContractModeButSignersExistIn(genesisBlockHeader)) {
      LOG.warn(
          "Using validator contract mode but genesis block contains signers - the genesis block signers will not be used.");
    }

    if (usingValidatorBlockHeaderModeButNoSignersIn(genesisBlockHeader)) {
      LOG.warn("Genesis block contains no signers - chain will not progress.");
    }
  }

  private boolean usingValidatorContractModeButSignersExistIn(
      final BlockHeader genesisBlockHeader) {
    return isValidatorContractMode() && signersExistIn(genesisBlockHeader);
  }

  private boolean usingValidatorBlockHeaderModeButNoSignersIn(
      final BlockHeader genesisBlockHeader) {
    return !isValidatorContractMode() && !signersExistIn(genesisBlockHeader);
  }

  private boolean isValidatorContractMode() {
    return configOptionsSupplier.get().getQbftConfigOptions().isValidatorContractMode();
  }

  private boolean signersExistIn(final BlockHeader genesisBlockHeader) {
    return bftBlockInterface().get().validatorsInBlock(genesisBlockHeader).size() > 0;
  }

  @Override
  protected BftContext createConsensusContext(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {
    final EpochManager epochManager = new EpochManager(qbftConfig.getEpochLength());

    final BftValidatorOverrides validatorOverrides =
        convertBftForks(configOptionsSupplier.get().getTransitions().getQbftForks());
    final BlockValidatorProvider blockValidatorProvider =
        BlockValidatorProvider.forkingValidatorProvider(
            blockchain, epochManager, bftBlockInterface().get(), validatorOverrides);

    final TransactionSimulator transactionSimulator =
        new TransactionSimulator(blockchain, worldStateArchive, protocolSchedule);
    transactionValidatorProvider =
        new TransactionValidatorProvider(
            blockchain, new ValidatorContractController(transactionSimulator), qbftForksSchedule);

    final ValidatorProvider validatorProvider =
        new ForkingValidatorProvider(
            blockchain, qbftForksSchedule, blockValidatorProvider, transactionValidatorProvider);

    return new QbftContext(
        validatorProvider, epochManager, bftBlockInterface().get(), pkiBlockCreationConfiguration);
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
