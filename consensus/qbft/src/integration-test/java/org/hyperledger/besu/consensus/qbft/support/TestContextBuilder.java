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
package org.hyperledger.besu.consensus.qbft.support;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.config.BftFork;
import org.hyperledger.besu.config.JsonQbftConfigOptions;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.config.QbftFork;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.consensus.common.BftValidatorOverrides;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftBlockInterface;
import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.bft.BftEventQueue;
import org.hyperledger.besu.consensus.common.bft.BftExecutors;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.BlockTimer;
import org.hyperledger.besu.consensus.common.bft.EventMultiplexer;
import org.hyperledger.besu.consensus.common.bft.Gossiper;
import org.hyperledger.besu.consensus.common.bft.MessageTracker;
import org.hyperledger.besu.consensus.common.bft.RoundTimer;
import org.hyperledger.besu.consensus.common.bft.SynchronizerUpdater;
import org.hyperledger.besu.consensus.common.bft.UniqueMessageMulticaster;
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftBlockCreatorFactory;
import org.hyperledger.besu.consensus.common.bft.blockcreation.ProposerSelector;
import org.hyperledger.besu.consensus.common.bft.inttest.DefaultValidatorPeer;
import org.hyperledger.besu.consensus.common.bft.inttest.NetworkLayout;
import org.hyperledger.besu.consensus.common.bft.inttest.NodeParams;
import org.hyperledger.besu.consensus.common.bft.inttest.StubValidatorMulticaster;
import org.hyperledger.besu.consensus.common.bft.inttest.StubbedSynchronizerUpdater;
import org.hyperledger.besu.consensus.common.bft.inttest.TestTransitions;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftEventHandler;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftFinalState;
import org.hyperledger.besu.consensus.common.bft.statemachine.FutureMessageBuffer;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.blockbased.BlockValidatorProvider;
import org.hyperledger.besu.consensus.qbft.MutableQbftConfigOptions;
import org.hyperledger.besu.consensus.qbft.QbftContext;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.QbftForksSchedulesFactory;
import org.hyperledger.besu.consensus.qbft.QbftGossip;
import org.hyperledger.besu.consensus.qbft.QbftProtocolSchedule;
import org.hyperledger.besu.consensus.qbft.blockcreation.QbftBlockCreatorFactory;
import org.hyperledger.besu.consensus.qbft.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.statemachine.QbftBlockHeightManagerFactory;
import org.hyperledger.besu.consensus.qbft.statemachine.QbftController;
import org.hyperledger.besu.consensus.qbft.statemachine.QbftRoundFactory;
import org.hyperledger.besu.consensus.qbft.validation.MessageValidatorFactory;
import org.hyperledger.besu.consensus.qbft.validator.ForkingValidatorProvider;
import org.hyperledger.besu.consensus.qbft.validator.TransactionValidatorProvider;
import org.hyperledger.besu.consensus.qbft.validator.ValidatorContractController;
import org.hyperledger.besu.consensus.qbft.validator.ValidatorModeTransitionLogger;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.worldstate.DefaultWorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.TestClock;
import org.hyperledger.besu.util.Subscribers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import org.apache.tuweni.bytes.Bytes;

public class TestContextBuilder {

  private static final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private boolean useValidatorContract;

  private static class ControllerAndState {

    private final BftExecutors bftExecutors;
    private final BftEventHandler eventHandler;
    private final BftFinalState finalState;
    private final EventMultiplexer eventMultiplexer;
    private final MessageFactory messageFactory;
    private final ValidatorProvider validatorProvider;

    public ControllerAndState(
        final BftExecutors bftExecutors,
        final BftEventHandler eventHandler,
        final BftFinalState finalState,
        final EventMultiplexer eventMultiplexer,
        final MessageFactory messageFactory,
        final ValidatorProvider validatorProvider) {
      this.bftExecutors = bftExecutors;
      this.eventHandler = eventHandler;
      this.finalState = finalState;
      this.eventMultiplexer = eventMultiplexer;
      this.messageFactory = messageFactory;
      this.validatorProvider = validatorProvider;
    }

    public BftExecutors getBftExecutors() {
      return bftExecutors;
    }

    public BftEventHandler getEventHandler() {
      return eventHandler;
    }

    public BftFinalState getFinalState() {
      return finalState;
    }

    public EventMultiplexer getEventMultiplexer() {
      return eventMultiplexer;
    }

    public MessageFactory getMessageFactory() {
      return messageFactory;
    }

    public ValidatorProvider getValidatorProvider() {
      return validatorProvider;
    }
  }

  public static final int EPOCH_LENGTH = 10_000;
  public static final int BLOCK_TIMER_SEC = 3;
  public static final int ROUND_TIMER_SEC = 12;
  public static final int MESSAGE_QUEUE_LIMIT = 1000;
  public static final int GOSSIPED_HISTORY_LIMIT = 100;
  public static final int DUPLICATE_MESSAGE_LIMIT = 100;
  public static final int FUTURE_MESSAGES_MAX_DISTANCE = 10;
  public static final int FUTURE_MESSAGES_LIMIT = 1000;
  public static final Address VALIDATOR_CONTRACT_ADDRESS =
      Address.fromHexString("0x0000000000000000000000000000000000008888");
  private static final BftExtraDataCodec BFT_EXTRA_DATA_ENCODER = new QbftExtraDataCodec();

  private Clock clock = Clock.fixed(Instant.MIN, ZoneId.of("UTC"));
  private BftEventQueue bftEventQueue = new BftEventQueue(MESSAGE_QUEUE_LIMIT);
  private int validatorCount = 4;
  private int indexOfFirstLocallyProposedBlock = 0; // Meaning first block is from remote peer.
  private boolean useGossip = false;
  private Optional<String> genesisFile = Optional.empty();
  private List<NodeParams> nodeParams = Collections.emptyList();
  private List<QbftFork> qbftForks = Collections.emptyList();

  public TestContextBuilder clock(final Clock clock) {
    this.clock = clock;
    return this;
  }

  public TestContextBuilder eventQueue(final BftEventQueue bftEventQueue) {
    this.bftEventQueue = bftEventQueue;
    return this;
  }

  public TestContextBuilder validatorCount(final int validatorCount) {
    this.validatorCount = validatorCount;
    return this;
  }

  public TestContextBuilder indexOfFirstLocallyProposedBlock(
      final int indexOfFirstLocallyProposedBlock) {
    this.indexOfFirstLocallyProposedBlock = indexOfFirstLocallyProposedBlock;
    return this;
  }

  public TestContextBuilder nodeParams(final List<NodeParams> nodeParams) {
    this.nodeParams = nodeParams;
    return this;
  }

  public TestContextBuilder useGossip(final boolean useGossip) {
    this.useGossip = useGossip;
    return this;
  }

  public TestContextBuilder genesisFile(final String genesisFile) {
    this.genesisFile = Optional.of(genesisFile);
    return this;
  }

  public TestContextBuilder useValidatorContract(final boolean useValidatorContract) {
    this.useValidatorContract = useValidatorContract;
    return this;
  }

  public TestContextBuilder qbftForks(final List<QbftFork> qbftForks) {
    this.qbftForks = qbftForks;
    return this;
  }

  public TestContext build() {
    final NetworkLayout networkNodes;
    if (nodeParams.isEmpty()) {
      networkNodes =
          NetworkLayout.createNetworkLayout(validatorCount, indexOfFirstLocallyProposedBlock);
    } else {
      final TreeMap<Address, NodeParams> addressKeyMap = new TreeMap<>();
      for (NodeParams params : nodeParams) {
        addressKeyMap.put(params.getAddress(), params);
      }
      final NodeParams localNode =
          Iterables.get(addressKeyMap.values(), indexOfFirstLocallyProposedBlock);
      networkNodes = new NetworkLayout(localNode, addressKeyMap);
    }

    final MutableBlockchain blockChain;
    final DefaultWorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();

    if (genesisFile.isPresent()) {
      try {
        final GenesisState genesisState = createGenesisBlock(genesisFile.get());
        blockChain =
            createInMemoryBlockchain(
                genesisState.getBlock(),
                BftBlockHeaderFunctions.forOnchainBlock(BFT_EXTRA_DATA_ENCODER));
        genesisState.writeStateTo(worldStateArchive.getMutable());
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    } else {
      final Block genesisBlock = createGenesisBlock(networkNodes.getValidatorAddresses());
      blockChain =
          createInMemoryBlockchain(
              genesisBlock, BftBlockHeaderFunctions.forOnchainBlock(BFT_EXTRA_DATA_ENCODER));
    }

    // Use a stubbed version of the multicaster, to prevent creating PeerConnections etc.
    final StubValidatorMulticaster multicaster = new StubValidatorMulticaster();
    final UniqueMessageMulticaster uniqueMulticaster =
        new UniqueMessageMulticaster(multicaster, GOSSIPED_HISTORY_LIMIT);

    final Gossiper gossiper =
        useGossip
            ? new QbftGossip(uniqueMulticaster, BFT_EXTRA_DATA_ENCODER)
            : mock(Gossiper.class);

    final StubbedSynchronizerUpdater synchronizerUpdater = new StubbedSynchronizerUpdater();

    final ControllerAndState controllerAndState =
        createControllerAndFinalState(
            blockChain,
            worldStateArchive,
            multicaster,
            networkNodes.getLocalNode().getNodeKey(),
            clock,
            bftEventQueue,
            gossiper,
            synchronizerUpdater,
            useValidatorContract,
            qbftForks);

    // Add each networkNode to the Multicaster (such that each can receive msgs from local node).
    // NOTE: the remotePeers needs to be ordered based on Address (as this is used to determine
    // the proposer order which must be managed in test).
    final Map<Address, ValidatorPeer> remotePeers =
        networkNodes.getRemotePeers().stream()
            .collect(
                Collectors.toMap(
                    NodeParams::getAddress,
                    nodeParams ->
                        new ValidatorPeer(
                            nodeParams,
                            new MessageFactory(nodeParams.getNodeKey()),
                            controllerAndState.getEventMultiplexer()),
                    (u, v) -> {
                      throw new IllegalStateException(String.format("Duplicate key %s", u));
                    },
                    LinkedHashMap::new));

    final List<DefaultValidatorPeer> peerCollection = new ArrayList<>(remotePeers.values());
    multicaster.addNetworkPeers(peerCollection);
    synchronizerUpdater.addNetworkPeers(peerCollection);

    return new TestContext(
        remotePeers,
        blockChain,
        controllerAndState.getBftExecutors(),
        controllerAndState.getEventHandler(),
        controllerAndState.getFinalState(),
        controllerAndState.getEventMultiplexer(),
        controllerAndState.getMessageFactory(),
        controllerAndState.getValidatorProvider(),
        BFT_EXTRA_DATA_ENCODER);
  }

  public TestContext buildAndStart() {
    TestContext testContext = build();
    testContext.start();
    return testContext;
  }

  private static Block createGenesisBlock(final Set<Address> validators) {
    final Address coinbase = Iterables.get(validators, 0);
    final BlockHeaderTestFixture headerTestFixture = new BlockHeaderTestFixture();
    final BftExtraData extraData =
        new BftExtraData(
            Bytes.wrap(new byte[32]), Collections.emptyList(), Optional.empty(), 0, validators);
    headerTestFixture.extraData(BFT_EXTRA_DATA_ENCODER.encode(extraData));
    headerTestFixture.mixHash(BftHelpers.EXPECTED_MIX_HASH);
    headerTestFixture.difficulty(Difficulty.ONE);
    headerTestFixture.ommersHash(Hash.EMPTY_LIST_HASH);
    headerTestFixture.nonce(0);
    headerTestFixture.timestamp(0);
    headerTestFixture.parentHash(Hash.ZERO);
    headerTestFixture.gasLimit(5000);
    headerTestFixture.coinbase(coinbase);

    final BlockHeader genesisHeader = headerTestFixture.buildHeader();
    return new Block(
        genesisHeader, new BlockBody(Collections.emptyList(), Collections.emptyList()));
  }

  private GenesisState createGenesisBlock(final String genesisFile) throws IOException {
    final String json = Files.readString(Path.of(genesisFile));
    return GenesisState.fromJson(json, ProtocolScheduleFixture.MAINNET);
  }

  private static ControllerAndState createControllerAndFinalState(
      final MutableBlockchain blockChain,
      final WorldStateArchive worldStateArchive,
      final StubValidatorMulticaster multicaster,
      final NodeKey nodeKey,
      final Clock clock,
      final BftEventQueue bftEventQueue,
      final Gossiper gossiper,
      final SynchronizerUpdater synchronizerUpdater,
      final boolean useValidatorContract,
      final List<QbftFork> qbftForks) {

    final MiningParameters miningParams =
        new MiningParameters.Builder()
            .coinbase(AddressHelpers.ofValue(1))
            .minTransactionGasPrice(Wei.ZERO)
            .extraData(Bytes.wrap("Qbft Int tests".getBytes(UTF_8)))
            .miningEnabled(true)
            .build();

    final StubGenesisConfigOptions genesisConfigOptions = new StubGenesisConfigOptions();
    final Map<String, Object> qbftConfigValues =
        useValidatorContract
            ? Map.of(
                JsonQbftConfigOptions.VALIDATOR_CONTRACT_ADDRESS,
                VALIDATOR_CONTRACT_ADDRESS.toHexString())
            : Collections.emptyMap();
    final QbftConfigOptions qbftConfigOptions = createGenesisConfig(useValidatorContract);

    genesisConfigOptions.byzantiumBlock(0);
    genesisConfigOptions.qbftConfigOptions(
        new JsonQbftConfigOptions(JsonUtil.objectNodeFromMap(qbftConfigValues)));
    genesisConfigOptions.transitions(TestTransitions.createQbftTestTransitions(qbftForks));
    genesisConfigOptions.qbftConfigOptions(qbftConfigOptions);

    final EpochManager epochManager = new EpochManager(EPOCH_LENGTH);

    final BftBlockInterface blockInterface = new BftBlockInterface(BFT_EXTRA_DATA_ENCODER);

    final ForksSchedule<QbftConfigOptions> forksSchedule =
        QbftForksSchedulesFactory.create(genesisConfigOptions);

    final ProtocolSchedule protocolSchedule =
        QbftProtocolSchedule.create(
            genesisConfigOptions, forksSchedule, BFT_EXTRA_DATA_ENCODER, EvmConfiguration.DEFAULT);

    final BftValidatorOverrides validatorOverrides = convertBftForks(qbftForks);
    final TransactionSimulator transactionSimulator =
        new TransactionSimulator(blockChain, worldStateArchive, protocolSchedule);

    final BlockValidatorProvider blockValidatorProvider =
        BlockValidatorProvider.forkingValidatorProvider(
            blockChain, epochManager, blockInterface, validatorOverrides);
    final TransactionValidatorProvider transactionValidatorProvider =
        new TransactionValidatorProvider(
            blockChain, new ValidatorContractController(transactionSimulator), forksSchedule);
    final ValidatorProvider validatorProvider =
        new ForkingValidatorProvider(
            blockChain, forksSchedule, blockValidatorProvider, transactionValidatorProvider);

    final ProtocolContext protocolContext =
        new ProtocolContext(
            blockChain,
            worldStateArchive,
            new QbftContext(validatorProvider, epochManager, blockInterface, Optional.empty()));

    final GasPricePendingTransactionsSorter pendingTransactions =
        new GasPricePendingTransactionsSorter(
            TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
            1,
            clock,
            metricsSystem,
            blockChain::getChainHeadHeader,
            TransactionPoolConfiguration.DEFAULT_PRICE_BUMP);

    final Address localAddress = Util.publicKeyToAddress(nodeKey.getPublicKey());
    final BftBlockCreatorFactory<?> blockCreatorFactory =
        new QbftBlockCreatorFactory(
            pendingTransactions, // changed from QbftBesuController
            protocolContext,
            protocolSchedule,
            forksSchedule,
            miningParams,
            localAddress,
            BFT_EXTRA_DATA_ENCODER);

    final ProposerSelector proposerSelector =
        new ProposerSelector(blockChain, blockInterface, true, validatorProvider);

    final BftExecutors bftExecutors =
        BftExecutors.create(new NoOpMetricsSystem(), BftExecutors.ConsensusType.QBFT);
    final BftFinalState finalState =
        new BftFinalState(
            protocolContext.getConsensusContext(BftContext.class).getValidatorProvider(),
            nodeKey,
            Util.publicKeyToAddress(nodeKey.getPublicKey()),
            proposerSelector,
            multicaster,
            new RoundTimer(bftEventQueue, ROUND_TIMER_SEC, bftExecutors),
            new BlockTimer(bftEventQueue, forksSchedule, bftExecutors, TestClock.fixed()),
            blockCreatorFactory,
            clock);

    final MessageFactory messageFactory = new MessageFactory(nodeKey);

    final MessageValidatorFactory messageValidatorFactory =
        new MessageValidatorFactory(
            proposerSelector, protocolSchedule, protocolContext, BFT_EXTRA_DATA_ENCODER);

    final Subscribers<MinedBlockObserver> minedBlockObservers = Subscribers.create();

    final MessageTracker duplicateMessageTracker = new MessageTracker(DUPLICATE_MESSAGE_LIMIT);
    final FutureMessageBuffer futureMessageBuffer =
        new FutureMessageBuffer(
            FUTURE_MESSAGES_MAX_DISTANCE,
            FUTURE_MESSAGES_LIMIT,
            blockChain.getChainHeadBlockNumber());

    final BftEventHandler qbftController =
        new QbftController(
            blockChain,
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
                    BFT_EXTRA_DATA_ENCODER),
                messageValidatorFactory,
                messageFactory,
                new ValidatorModeTransitionLogger(forksSchedule)),
            gossiper,
            duplicateMessageTracker,
            futureMessageBuffer,
            synchronizerUpdater,
            BFT_EXTRA_DATA_ENCODER);

    final EventMultiplexer eventMultiplexer = new EventMultiplexer(qbftController);
    //////////////////////////// END QBFT BesuController ////////////////////////////

    return new ControllerAndState(
        bftExecutors,
        qbftController,
        finalState,
        eventMultiplexer,
        messageFactory,
        validatorProvider);
  }

  private static QbftConfigOptions createGenesisConfig(final boolean useValidatorContract) {
    final MutableQbftConfigOptions qbftConfigOptions =
        new MutableQbftConfigOptions(JsonQbftConfigOptions.DEFAULT);
    qbftConfigOptions.setBlockPeriodSeconds(BLOCK_TIMER_SEC);
    if (useValidatorContract) {
      qbftConfigOptions.setValidatorContractAddress(
          Optional.of(VALIDATOR_CONTRACT_ADDRESS.toHexString()));
    }
    return qbftConfigOptions;
  }

  private static BftValidatorOverrides convertBftForks(final List<QbftFork> bftForks) {
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
}
