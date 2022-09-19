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
package org.hyperledger.besu.ethereum.eth.manager;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapProtocolManager;
import org.hyperledger.besu.ethereum.eth.messages.BlockBodiesMessage;
import org.hyperledger.besu.ethereum.eth.messages.BlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.EthPV63;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.eth.messages.NodeDataMessage;
import org.hyperledger.besu.ethereum.eth.messages.PooledTransactionsMessage;
import org.hyperledger.besu.ethereum.eth.messages.ReceiptsMessage;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.DefaultMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;

public class RespondingEthPeer {
  private static final BlockDataGenerator gen = new BlockDataGenerator();
  private final EthPeer ethPeer;
  private final BlockingQueue<OutgoingMessage> outgoingMessages;
  private final EthProtocolManager ethProtocolManager;
  private final Optional<SnapProtocolManager> snapProtocolManager;
  private final MockPeerConnection peerConnection;

  private RespondingEthPeer(
      final EthProtocolManager ethProtocolManager,
      final Optional<SnapProtocolManager> snapProtocolManager,
      final MockPeerConnection peerConnection,
      final EthPeer ethPeer,
      final BlockingQueue<OutgoingMessage> outgoingMessages) {
    this.ethProtocolManager = ethProtocolManager;
    this.snapProtocolManager = snapProtocolManager;
    this.peerConnection = peerConnection;
    this.ethPeer = ethPeer;
    this.outgoingMessages = outgoingMessages;
  }

  public static void respondOnce(final Responder responder, final List<RespondingEthPeer> peers) {
    for (final RespondingEthPeer peer : peers) {
      if (peer.respond(responder)) {
        break;
      }
    }
  }

  public static void respondOnce(final Responder responder, final RespondingEthPeer... peers) {
    respondOnce(responder, Arrays.asList(peers));
  }

  public boolean disconnect(final DisconnectReason reason) {
    if (ethPeer.isDisconnected()) {
      return false;
    }

    ethPeer.disconnect(reason);
    ethProtocolManager.handleDisconnect(getPeerConnection(), reason, true);
    return true;
  }

  public MockPeerConnection getPeerConnection() {
    return peerConnection;
  }

  public static Builder builder() {
    return new Builder();
  }

  private static RespondingEthPeer create(
      final EthProtocolManager ethProtocolManager,
      final Optional<SnapProtocolManager> snapProtocolManager,
      final Hash chainHeadHash,
      final Difficulty totalDifficulty,
      final OptionalLong estimatedHeight,
      final List<PeerValidator> peerValidators) {
    final EthPeers ethPeers = ethProtocolManager.ethContext().getEthPeers();

    final Set<Capability> caps = new HashSet<>(Collections.singletonList(EthProtocol.ETH63));
    final BlockingQueue<OutgoingMessage> outgoingMessages = new ArrayBlockingQueue<>(1000);
    final MockPeerConnection peerConnection =
        new MockPeerConnection(
            caps, (cap, msg, conn) -> outgoingMessages.add(new OutgoingMessage(cap, msg)));
    ethPeers.registerConnection(peerConnection, peerValidators);
    final EthPeer peer = ethPeers.peer(peerConnection);
    peer.registerStatusReceived(chainHeadHash, totalDifficulty, 63);
    estimatedHeight.ifPresent(height -> peer.chainState().update(chainHeadHash, height));
    peer.registerStatusSent();

    return new RespondingEthPeer(
        ethProtocolManager, snapProtocolManager, peerConnection, peer, outgoingMessages);
  }

  public EthPeer getEthPeer() {
    return ethPeer;
  }

  public void respondWhile(final Responder responder, final RespondWhileCondition condition) {
    int counter = 0;
    while (condition.shouldRespond()) {
      respond(responder);
      counter++;
      if (counter > 10_000) {
        // Limit applied to avoid tests hanging forever which is hard to track down.
        throw new IllegalStateException(
            "Responded 10,000 times and stop condition still not reached.");
      }
    }
  }

  public void respondWhileOtherThreadsWork(
      final Responder responder, final RespondWhileCondition condition) {
    int counter = 0;
    while (condition.shouldRespond()) {
      try {
        final OutgoingMessage message = outgoingMessages.poll(1, TimeUnit.SECONDS);
        if (message != null) {
          respondToMessage(responder, message);
          counter++;
          if (counter > 10_000) {
            // Limit applied to avoid tests hanging forever which is hard to track down.
            throw new IllegalStateException(
                "Responded 10,000 times and stop condition still not reached.");
          }
        }
      } catch (final InterruptedException e) {
        // Ignore and recheck condition.
      }
    }
  }

  public void respondTimes(final Responder responder, final int maxCycles) {
    // Respond repeatedly, as each round may produce new outgoing messages
    int count = 0;
    while (!outgoingMessages.isEmpty()) {
      count++;
      respond(responder);
      if (count >= maxCycles) {
        break;
      }
    }
  }

  /** @return True if any requests were processed */
  public boolean respond(final Responder responder) {
    // Respond to queued messages
    final List<OutgoingMessage> currentMessages = new ArrayList<>();
    outgoingMessages.drainTo(currentMessages);
    for (final OutgoingMessage msg : currentMessages) {
      respondToMessage(responder, msg);
    }
    return currentMessages.size() > 0;
  }

  private void respondToMessage(final Responder responder, final OutgoingMessage msg) {
    final Optional<MessageData> maybeResponse = responder.respond(msg.capability, msg.messageData);
    maybeResponse.ifPresent(
        (response) -> {
          if (ethProtocolManager.getSupportedCapabilities().contains(msg.capability)) {
            ethProtocolManager.processMessage(
                msg.capability, new DefaultMessage(peerConnection, response));
          } else
            snapProtocolManager.ifPresent(
                protocolManager ->
                    protocolManager.processMessage(
                        msg.capability, new DefaultMessage(peerConnection, response)));
        });
  }

  public Optional<MessageData> peekNextOutgoingRequest() {
    if (outgoingMessages.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(outgoingMessages.peek().messageData);
  }

  public Stream<MessageData> streamPendingOutgoingRequests() {
    return outgoingMessages.stream().map(OutgoingMessage::messageData);
  }

  public boolean hasOutstandingRequests() {
    return !outgoingMessages.isEmpty();
  }

  public static Responder targetedResponder(
      final BiFunction<Capability, MessageData, Boolean> requestFilter,
      final BiFunction<Capability, MessageData, MessageData> responseGenerator) {
    return (cap, msg) -> {
      if (requestFilter.apply(cap, msg)) {
        return Optional.of(responseGenerator.apply(cap, msg));
      } else {
        return Optional.empty();
      }
    };
  }

  private static TransactionPool createTransactionPool() {
    return mock(TransactionPool.class);
  }

  public static Responder blockchainResponder(
      final Blockchain blockchain, final WorldStateArchive worldStateArchive) {
    return blockchainResponder(blockchain, worldStateArchive, createTransactionPool());
  }

  public static Responder blockchainResponder(final Blockchain blockchain) {
    return blockchainResponder(
        blockchain, createInMemoryWorldStateArchive(), createTransactionPool());
  }

  public static Responder blockchainResponder(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final TransactionPool transactionPool) {
    final int maxMsgSize = EthProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE;
    return (cap, msg) -> {
      MessageData response = null;
      switch (msg.getCode()) {
        case EthPV62.GET_BLOCK_HEADERS:
          response = EthServer.constructGetHeadersResponse(blockchain, msg, 200, maxMsgSize);
          break;
        case EthPV62.GET_BLOCK_BODIES:
          response = EthServer.constructGetBodiesResponse(blockchain, msg, 200, maxMsgSize);
          break;
        case EthPV63.GET_RECEIPTS:
          response = EthServer.constructGetReceiptsResponse(blockchain, msg, 200, maxMsgSize);
          break;
        case EthPV63.GET_NODE_DATA:
          response =
              EthServer.constructGetNodeDataResponse(worldStateArchive, msg, 200, maxMsgSize);
          break;
        case EthPV65.GET_POOLED_TRANSACTIONS:
          response =
              EthServer.constructGetPooledTransactionsResponse(
                  transactionPool, msg, 200, maxMsgSize);
      }
      return Optional.ofNullable(response);
    };
  }

  public static Responder wrapResponderWithCollector(
      final Responder responder, final List<MessageData> messageCollector) {
    return (cap, msg) -> {
      messageCollector.add(msg);
      return responder.respond(cap, msg);
    };
  }

  /**
   * Create a responder that only responds with a fixed portion of the available data.
   *
   * @param portion The portion of the available data to return, from 0 to 1
   */
  public static Responder partialResponder(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final TransactionPool transactionPool,
      final ProtocolSchedule protocolSchedule,
      final float portion) {
    checkArgument(portion >= 0.0 && portion <= 1.0, "Portion is in the range [0.0..1.0]");

    final Responder fullResponder =
        blockchainResponder(blockchain, worldStateArchive, transactionPool);
    return (cap, msg) -> {
      final Optional<MessageData> maybeResponse = fullResponder.respond(cap, msg);
      if (!maybeResponse.isPresent()) {
        return maybeResponse;
      }
      // Rewrite response with a subset of data
      final MessageData originalResponse = maybeResponse.get();
      MessageData partialResponse = originalResponse;
      switch (msg.getCode()) {
        case EthPV62.GET_BLOCK_HEADERS:
          final BlockHeadersMessage headersMessage = BlockHeadersMessage.readFrom(originalResponse);
          final List<BlockHeader> originalHeaders =
              Lists.newArrayList(headersMessage.getHeaders(protocolSchedule));
          final List<BlockHeader> partialHeaders =
              originalHeaders.subList(0, (int) (originalHeaders.size() * portion));
          partialResponse = BlockHeadersMessage.create(partialHeaders);
          break;
        case EthPV62.GET_BLOCK_BODIES:
          final BlockBodiesMessage bodiesMessage = BlockBodiesMessage.readFrom(originalResponse);
          final List<BlockBody> originalBodies =
              Lists.newArrayList(bodiesMessage.bodies(protocolSchedule));
          final List<BlockBody> partialBodies =
              originalBodies.subList(0, (int) (originalBodies.size() * portion));
          partialResponse = BlockBodiesMessage.create(partialBodies);
          break;
        case EthPV63.GET_RECEIPTS:
          final ReceiptsMessage receiptsMessage = ReceiptsMessage.readFrom(originalResponse);
          final List<List<TransactionReceipt>> originalReceipts =
              Lists.newArrayList(receiptsMessage.receipts());
          final List<List<TransactionReceipt>> partialReceipts =
              originalReceipts.subList(0, (int) (originalReceipts.size() * portion));
          partialResponse = ReceiptsMessage.create(partialReceipts);
          break;
        case EthPV63.GET_NODE_DATA:
          final NodeDataMessage nodeDataMessage = NodeDataMessage.readFrom(originalResponse);
          final List<Bytes> originalNodeData = Lists.newArrayList(nodeDataMessage.nodeData());
          final List<Bytes> partialNodeData =
              originalNodeData.subList(0, (int) (originalNodeData.size() * portion));
          partialResponse = NodeDataMessage.create(partialNodeData);
          break;
        case EthPV65.GET_POOLED_TRANSACTIONS:
          final PooledTransactionsMessage pooledTransactionsMessage =
              PooledTransactionsMessage.readFrom(originalResponse);
          final List<Transaction> originalPooledTx =
              Lists.newArrayList(pooledTransactionsMessage.transactions());
          final List<Transaction> partialPooledTx =
              originalPooledTx.subList(0, (int) (originalPooledTx.size() * portion));
          partialResponse = PooledTransactionsMessage.create(partialPooledTx);
          break;
      }
      return Optional.of(partialResponse);
    };
  }

  public static Responder emptyResponder() {
    return (cap, msg) -> {
      MessageData response = null;
      switch (msg.getCode()) {
        case EthPV62.GET_BLOCK_HEADERS:
          response = BlockHeadersMessage.create(Collections.emptyList());
          break;
        case EthPV62.GET_BLOCK_BODIES:
          response = BlockBodiesMessage.create(Collections.emptyList());
          break;
        case EthPV63.GET_RECEIPTS:
          response = ReceiptsMessage.create(Collections.emptyList());
          break;
        case EthPV63.GET_NODE_DATA:
          response = NodeDataMessage.create(Collections.emptyList());
          break;
        case EthPV65.GET_POOLED_TRANSACTIONS:
          response = PooledTransactionsMessage.create(Collections.emptyList());
          break;
      }
      return Optional.ofNullable(response);
    };
  }

  public static class Builder {
    private EthProtocolManager ethProtocolManager;
    private Optional<SnapProtocolManager> snapProtocolManager = Optional.empty();
    private Hash chainHeadHash = gen.hash();
    private Difficulty totalDifficulty = Difficulty.of(1000L);
    private OptionalLong estimatedHeight = OptionalLong.of(1000L);
    private final List<PeerValidator> peerValidators = new ArrayList<>();

    public RespondingEthPeer build() {
      checkNotNull(ethProtocolManager, "Must configure EthProtocolManager");

      return RespondingEthPeer.create(
          ethProtocolManager,
          snapProtocolManager,
          chainHeadHash,
          totalDifficulty,
          estimatedHeight,
          peerValidators);
    }

    public Builder ethProtocolManager(final EthProtocolManager ethProtocolManager) {
      checkNotNull(ethProtocolManager);
      this.ethProtocolManager = ethProtocolManager;
      return this;
    }

    public Builder snapProtocolManager(final SnapProtocolManager snapProtocolManager) {
      checkNotNull(snapProtocolManager);
      this.snapProtocolManager = Optional.of(snapProtocolManager);
      return this;
    }

    public Builder chainHeadHash(final Hash chainHeadHash) {
      checkNotNull(chainHeadHash);
      this.chainHeadHash = chainHeadHash;
      return this;
    }

    public Builder totalDifficulty(final Difficulty totalDifficulty) {
      checkNotNull(totalDifficulty);
      this.totalDifficulty = totalDifficulty;
      return this;
    }

    public Builder estimatedHeight(final OptionalLong estimatedHeight) {
      checkNotNull(estimatedHeight);
      this.estimatedHeight = estimatedHeight;
      return this;
    }

    public Builder estimatedHeight(final long estimatedHeight) {
      this.estimatedHeight = OptionalLong.of(estimatedHeight);
      return this;
    }

    public Builder peerValidators(final List<PeerValidator> peerValidators) {
      checkNotNull(peerValidators);
      this.peerValidators.addAll(peerValidators);
      return this;
    }

    public Builder peerValidators(final PeerValidator... peerValidators) {
      peerValidators(Arrays.asList(peerValidators));
      return this;
    }
  }

  static class OutgoingMessage {
    private final Capability capability;
    private final MessageData messageData;

    OutgoingMessage(final Capability capability, final MessageData messageData) {
      this.capability = capability;
      this.messageData = messageData;
    }

    public Capability capability() {
      return capability;
    }

    public MessageData messageData() {
      return messageData;
    }
  }

  @FunctionalInterface
  public interface Responder {
    Optional<MessageData> respond(Capability cap, MessageData msg);
  }

  public interface RespondWhileCondition {
    boolean shouldRespond();
  }
}
