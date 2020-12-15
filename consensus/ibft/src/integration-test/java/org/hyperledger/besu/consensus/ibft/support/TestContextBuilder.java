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
package org.hyperledger.besu.consensus.ibft.support;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.besu.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.consensus.common.VoteTallyUpdater;
import org.hyperledger.besu.consensus.common.bft.BftEventQueue;
import org.hyperledger.besu.consensus.common.bft.BftExecutors;
import org.hyperledger.besu.consensus.common.bft.BlockTimer;
import org.hyperledger.besu.consensus.common.bft.EventMultiplexer;
import org.hyperledger.besu.consensus.common.bft.Gossiper;
import org.hyperledger.besu.consensus.common.bft.MessageTracker;
import org.hyperledger.besu.consensus.common.bft.RoundTimer;
import org.hyperledger.besu.consensus.common.bft.SynchronizerUpdater;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftEventHandler;
import org.hyperledger.besu.consensus.ibft.IbftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.ibft.IbftBlockInterface;
import org.hyperledger.besu.consensus.ibft.IbftContext;
import org.hyperledger.besu.consensus.ibft.IbftExtraData;
import org.hyperledger.besu.consensus.ibft.IbftGossip;
import org.hyperledger.besu.consensus.ibft.IbftHelpers;
import org.hyperledger.besu.consensus.ibft.IbftProtocolSchedule;
import org.hyperledger.besu.consensus.ibft.UniqueMessageMulticaster;
import org.hyperledger.besu.consensus.ibft.blockcreation.IbftBlockCreatorFactory;
import org.hyperledger.besu.consensus.ibft.blockcreation.ProposerSelector;
import org.hyperledger.besu.consensus.ibft.payload.MessageFactory;
import org.hyperledger.besu.consensus.ibft.statemachine.FutureMessageBuffer;
import org.hyperledger.besu.consensus.ibft.statemachine.IbftBlockHeightManagerFactory;
import org.hyperledger.besu.consensus.ibft.statemachine.IbftController;
import org.hyperledger.besu.consensus.ibft.statemachine.IbftFinalState;
import org.hyperledger.besu.consensus.ibft.statemachine.IbftRoundFactory;
import org.hyperledger.besu.consensus.ibft.validation.MessageValidatorFactory;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.TestClock;
import org.hyperledger.besu.util.Subscribers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import org.apache.tuweni.bytes.Bytes;

public class TestContextBuilder {

  private static final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  private static class ControllerAndState {

    private final BftExecutors bftExecutors;
    private final BftEventHandler eventHandler;
    private final IbftFinalState finalState;
    private final EventMultiplexer eventMultiplexer;

    public ControllerAndState(
        final BftExecutors bftExecutors,
        final BftEventHandler eventHandler,
        final IbftFinalState finalState,
        final EventMultiplexer eventMultiplexer) {
      this.bftExecutors = bftExecutors;
      this.eventHandler = eventHandler;
      this.finalState = finalState;
      this.eventMultiplexer = eventMultiplexer;
    }

    public BftExecutors getIbftExecutors() {
      return bftExecutors;
    }

    public BftEventHandler getEventHandler() {
      return eventHandler;
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
  private BftEventQueue bftEventQueue = new BftEventQueue(MESSAGE_QUEUE_LIMIT);
  private int validatorCount = 4;
  private int indexOfFirstLocallyProposedBlock = 0; // Meaning first block is from remote peer.
  private boolean useGossip = false;

  public TestContextBuilder clock(final Clock clock) {
    this.clock = clock;
    return this;
  }

  public TestContextBuilder ibftEventQueue(final BftEventQueue bftEventQueue) {
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
            networkNodes.getLocalNode().getNodeKey(),
            clock,
            bftEventQueue,
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
                            new MessageFactory(nodeParams.getNodeKey()),
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
        controllerAndState.getIbftExecutors(),
        controllerAndState.getEventHandler(),
        controllerAndState.getFinalState(),
        controllerAndState.getEventMultiplexer());
  }

  public TestContext buildAndStart() {
    TestContext testContext = build();
    testContext.start();
    return testContext;
  }

  private static Block createGenesisBlock(final Set<Address> validators) {
    final Address coinbase = Iterables.get(validators, 0);
    final BlockHeaderTestFixture headerTestFixture = new BlockHeaderTestFixture();
    final IbftExtraData extraData =
        new IbftExtraData(
            Bytes.wrap(new byte[32]), Collections.emptyList(), Optional.empty(), 0, validators);
    headerTestFixture.extraData(extraData.encode());
    headerTestFixture.mixHash(IbftHelpers.EXPECTED_MIX_HASH);
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

  private static ControllerAndState createControllerAndFinalState(
      final MutableBlockchain blockChain,
      final StubValidatorMulticaster multicaster,
      final NodeKey nodeKey,
      final Clock clock,
      final BftEventQueue bftEventQueue,
      final Gossiper gossiper,
      final SynchronizerUpdater synchronizerUpdater) {

    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();

    final MiningParameters miningParams =
        new MiningParameters(
            AddressHelpers.ofValue(1),
            Wei.ZERO,
            Bytes.wrap("Ibft Int tests".getBytes(UTF_8)),
            true);

    final StubGenesisConfigOptions genesisConfigOptions = new StubGenesisConfigOptions();
    genesisConfigOptions.byzantiumBlock(0);

    final ProtocolSchedule protocolSchedule = IbftProtocolSchedule.create(genesisConfigOptions);

    /////////////////////////////////////////////////////////////////////////////////////
    // From here down is BASICALLY taken from IbftBesuController
    final EpochManager epochManager = new EpochManager(EPOCH_LENGTH);

    final BlockInterface blockInterface = new IbftBlockInterface();

    final VoteTallyCache voteTallyCache =
        new VoteTallyCache(
            blockChain,
            new VoteTallyUpdater(epochManager, blockInterface),
            epochManager,
            new IbftBlockInterface());

    final VoteProposer voteProposer = new VoteProposer();

    final ProtocolContext protocolContext =
        new ProtocolContext(
            blockChain,
            worldStateArchive,
            new IbftContext(voteTallyCache, voteProposer, epochManager, blockInterface));

    final PendingTransactions pendingTransactions =
        new PendingTransactions(
            TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
            1,
            1,
            clock,
            metricsSystem,
            blockChain::getChainHeadHeader,
            TransactionPoolConfiguration.DEFAULT_PRICE_BUMP);

    final Address localAddress = Util.publicKeyToAddress(nodeKey.getPublicKey());
    final IbftBlockCreatorFactory blockCreatorFactory =
        new IbftBlockCreatorFactory(
            (gasLimit) -> gasLimit,
            pendingTransactions, // changed from IbftBesuController
            protocolContext,
            protocolSchedule,
            miningParams,
            localAddress,
            localAddress);

    final ProposerSelector proposerSelector =
        new ProposerSelector(blockChain, blockInterface, true, voteTallyCache);

    final BftExecutors bftExecutors = BftExecutors.create(new NoOpMetricsSystem());
    final IbftFinalState finalState =
        new IbftFinalState(
            protocolContext.getConsensusState(IbftContext.class).getVoteTallyCache(),
            nodeKey,
            Util.publicKeyToAddress(nodeKey.getPublicKey()),
            proposerSelector,
            multicaster,
            new RoundTimer(bftEventQueue, ROUND_TIMER_SEC * 1000, bftExecutors),
            new BlockTimer(bftEventQueue, BLOCK_TIMER_SEC * 1000, bftExecutors, TestClock.fixed()),
            blockCreatorFactory,
            new MessageFactory(nodeKey),
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

    final BftEventHandler ibftController =
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
    //////////////////////////// END IBFT BesuController ////////////////////////////

    return new ControllerAndState(bftExecutors, ibftController, finalState, eventMultiplexer);
  }
}
