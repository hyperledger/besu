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
package tech.pegasys.pantheon.consensus.ibft.support;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;

import tech.pegasys.pantheon.config.StubGenesisConfigOptions;
import tech.pegasys.pantheon.consensus.common.BlockInterface;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTallyCache;
import tech.pegasys.pantheon.consensus.common.VoteTallyUpdater;
import tech.pegasys.pantheon.consensus.ibft.BlockTimer;
import tech.pegasys.pantheon.consensus.ibft.EventMultiplexer;
import tech.pegasys.pantheon.consensus.ibft.Gossiper;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockHeaderFunctions;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockInterface;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.IbftEventQueue;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.IbftGossip;
import tech.pegasys.pantheon.consensus.ibft.IbftHelpers;
import tech.pegasys.pantheon.consensus.ibft.IbftProtocolSchedule;
import tech.pegasys.pantheon.consensus.ibft.MessageTracker;
import tech.pegasys.pantheon.consensus.ibft.RoundTimer;
import tech.pegasys.pantheon.consensus.ibft.SynchronizerUpdater;
import tech.pegasys.pantheon.consensus.ibft.UniqueMessageMulticaster;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.IbftBlockCreatorFactory;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.ProposerSelector;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.statemachine.FutureMessageBuffer;
import tech.pegasys.pantheon.consensus.ibft.statemachine.IbftBlockHeightManagerFactory;
import tech.pegasys.pantheon.consensus.ibft.statemachine.IbftController;
import tech.pegasys.pantheon.consensus.ibft.statemachine.IbftFinalState;
import tech.pegasys.pantheon.consensus.ibft.statemachine.IbftRoundFactory;
import tech.pegasys.pantheon.consensus.ibft.validation.MessageValidatorFactory;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPoolConfiguration;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;

public class TestContextBuilder {

  private static MetricsSystem metricsSystem = new NoOpMetricsSystem();

  private static class ControllerAndState {

    private IbftController controller;
    private IbftFinalState finalState;
    private EventMultiplexer eventMultiplexer;

    public ControllerAndState(
        final IbftController controller,
        final IbftFinalState finalState,
        final EventMultiplexer eventMultiplexer) {
      this.controller = controller;
      this.finalState = finalState;
      this.eventMultiplexer = eventMultiplexer;
    }

    public IbftController getController() {
      return controller;
    }

    public IbftFinalState getFinalState() {
      return finalState;
    }

    public EventMultiplexer getEventMultiplexer() {
      return eventMultiplexer;
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

  private Clock clock = Clock.fixed(Instant.MIN, ZoneId.of("UTC"));
  private IbftEventQueue ibftEventQueue = new IbftEventQueue(MESSAGE_QUEUE_LIMIT);
  private int validatorCount = 4;
  private int indexOfFirstLocallyProposedBlock = 0; // Meaning first block is from remote peer.
  private boolean useGossip = false;

  public TestContextBuilder clock(final Clock clock) {
    this.clock = clock;
    return this;
  }

  public TestContextBuilder ibftEventQueue(final IbftEventQueue ibftEventQueue) {
    this.ibftEventQueue = ibftEventQueue;
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

  public TestContextBuilder useGossip(final boolean useGossip) {
    this.useGossip = useGossip;
    return this;
  }

  public TestContext build() {
    final NetworkLayout networkNodes =
        NetworkLayout.createNetworkLayout(validatorCount, indexOfFirstLocallyProposedBlock);

    final Block genesisBlock = createGenesisBlock(networkNodes.getValidatorAddresses());
    final MutableBlockchain blockChain =
        createInMemoryBlockchain(genesisBlock, IbftBlockHeaderFunctions.forOnChainBlock());

    final KeyPair nodeKeys = networkNodes.getLocalNode().getNodeKeyPair();

    // Use a stubbed version of the multicaster, to prevent creating PeerConnections etc.
    final StubValidatorMulticaster multicaster = new StubValidatorMulticaster();
    final UniqueMessageMulticaster uniqueMulticaster =
        new UniqueMessageMulticaster(multicaster, GOSSIPED_HISTORY_LIMIT);

    final Gossiper gossiper = useGossip ? new IbftGossip(uniqueMulticaster) : mock(Gossiper.class);

    final StubbedSynchronizerUpdater synchronizerUpdater = new StubbedSynchronizerUpdater();

    final ControllerAndState controllerAndState =
        createControllerAndFinalState(
            blockChain,
            multicaster,
            nodeKeys,
            clock,
            ibftEventQueue,
            gossiper,
            synchronizerUpdater);

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
                            new MessageFactory(nodeParams.getNodeKeyPair()),
                            controllerAndState.getEventMultiplexer()),
                    (u, v) -> {
                      throw new IllegalStateException(String.format("Duplicate key %s", u));
                    },
                    LinkedHashMap::new));

    multicaster.addNetworkPeers(remotePeers.values());
    synchronizerUpdater.addNetworkPeers(remotePeers.values());

    return new TestContext(
        remotePeers,
        blockChain,
        controllerAndState.getController(),
        controllerAndState.getFinalState(),
        controllerAndState.getEventMultiplexer());
  }

  private static Block createGenesisBlock(final Set<Address> validators) {
    final Address coinbase = Iterables.get(validators, 0);
    final BlockHeaderTestFixture headerTestFixture = new BlockHeaderTestFixture();
    final IbftExtraData extraData =
        new IbftExtraData(
            BytesValue.wrap(new byte[32]),
            Collections.emptyList(),
            Optional.empty(),
            0,
            validators);
    headerTestFixture.extraData(extraData.encode());
    headerTestFixture.mixHash(IbftHelpers.EXPECTED_MIX_HASH);
    headerTestFixture.difficulty(UInt256.ONE);
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

  private static ControllerAndState createControllerAndFinalState(
      final MutableBlockchain blockChain,
      final StubValidatorMulticaster multicaster,
      final KeyPair nodeKeys,
      final Clock clock,
      final IbftEventQueue ibftEventQueue,
      final Gossiper gossiper,
      final SynchronizerUpdater synchronizerUpdater) {

    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();

    final MiningParameters miningParams =
        new MiningParameters(
            AddressHelpers.ofValue(1),
            Wei.ZERO,
            BytesValue.wrap("Ibft Int tests".getBytes(UTF_8)),
            true);

    final StubGenesisConfigOptions genesisConfigOptions = new StubGenesisConfigOptions();
    genesisConfigOptions.byzantiumBlock(0);

    final ProtocolSchedule<IbftContext> protocolSchedule =
        IbftProtocolSchedule.create(genesisConfigOptions);

    /////////////////////////////////////////////////////////////////////////////////////
    // From here down is BASICALLY taken from IbftPantheonController
    final EpochManager epochManager = new EpochManager(EPOCH_LENGTH);

    final BlockInterface blockInterface = new IbftBlockInterface();

    final VoteTallyCache voteTallyCache =
        new VoteTallyCache(
            blockChain,
            new VoteTallyUpdater(epochManager, blockInterface),
            epochManager,
            new IbftBlockInterface());

    final VoteProposer voteProposer = new VoteProposer();

    final ProtocolContext<IbftContext> protocolContext =
        new ProtocolContext<>(
            blockChain, worldStateArchive, new IbftContext(voteTallyCache, voteProposer));

    final PendingTransactions pendingTransactions =
        new PendingTransactions(
            TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS, 1, clock, metricsSystem);

    final IbftBlockCreatorFactory blockCreatorFactory =
        new IbftBlockCreatorFactory(
            (gasLimit) -> gasLimit,
            pendingTransactions, // changed from IbftPantheonController
            protocolContext,
            protocolSchedule,
            miningParams,
            Util.publicKeyToAddress(nodeKeys.getPublicKey()));

    final ProposerSelector proposerSelector =
        new ProposerSelector(blockChain, blockInterface, true);

    final IbftFinalState finalState =
        new IbftFinalState(
            protocolContext.getConsensusState().getVoteTallyCache(),
            nodeKeys,
            Util.publicKeyToAddress(nodeKeys.getPublicKey()),
            proposerSelector,
            multicaster,
            new RoundTimer(
                ibftEventQueue, ROUND_TIMER_SEC * 1000, Executors.newScheduledThreadPool(1)),
            new BlockTimer(
                ibftEventQueue,
                BLOCK_TIMER_SEC * 1000,
                Executors.newScheduledThreadPool(1),
                TestClock.fixed()),
            blockCreatorFactory,
            new MessageFactory(nodeKeys),
            clock);

    final MessageValidatorFactory messageValidatorFactory =
        new MessageValidatorFactory(proposerSelector, protocolSchedule, protocolContext);

    final Subscribers<MinedBlockObserver> minedBlockObservers = Subscribers.create();

    final MessageTracker duplicateMessageTracker = new MessageTracker(DUPLICATE_MESSAGE_LIMIT);
    final FutureMessageBuffer futureMessageBuffer =
        new FutureMessageBuffer(
            FUTURE_MESSAGES_MAX_DISTANCE,
            FUTURE_MESSAGES_LIMIT,
            blockChain.getChainHeadBlockNumber());

    final IbftController ibftController =
        new IbftController(
            blockChain,
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
            synchronizerUpdater);

    final EventMultiplexer eventMultiplexer = new EventMultiplexer(ibftController);
    //////////////////////////// END IBFT PantheonController ////////////////////////////

    return new ControllerAndState(ibftController, finalState, eventMultiplexer);
  }
}
