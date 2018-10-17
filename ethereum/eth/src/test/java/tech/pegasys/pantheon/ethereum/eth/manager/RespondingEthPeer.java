/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.manager;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.messages.BlockBodiesMessage;
import tech.pegasys.pantheon.ethereum.eth.messages.BlockHeadersMessage;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV62;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV63;
import tech.pegasys.pantheon.ethereum.eth.messages.NodeDataMessage;
import tech.pegasys.pantheon.ethereum.eth.messages.ReceiptsMessage;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.DefaultMessage;
import tech.pegasys.pantheon.ethereum.testutil.BlockDataGenerator;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.collect.Lists;

public class RespondingEthPeer {
  private static final BlockDataGenerator gen = new BlockDataGenerator();
  private static final int DEFAULT_ESTIMATED_HEIGHT = 1000;
  private final EthPeer ethPeer;
  private final Queue<OutgoingMessage> outgoingMessages;
  private final EthProtocolManager ethProtocolManager;
  private final MockPeerConnection peerConnection;

  private RespondingEthPeer(
      final EthProtocolManager ethProtocolManager,
      final MockPeerConnection peerConnection,
      final EthPeer ethPeer,
      final Queue<OutgoingMessage> outgoingMessages) {
    this.ethProtocolManager = ethProtocolManager;
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

  public MockPeerConnection getPeerConnection() {
    return peerConnection;
  }

  public static RespondingEthPeer create(
      final EthProtocolManager ethProtocolManager, final UInt256 totalDifficulty) {
    return create(ethProtocolManager, totalDifficulty, DEFAULT_ESTIMATED_HEIGHT);
  }

  public static RespondingEthPeer create(
      final EthProtocolManager ethProtocolManager,
      final UInt256 totalDifficulty,
      final long estimatedHeight) {
    final Hash chainHeadHash = gen.hash();
    return create(ethProtocolManager, chainHeadHash, totalDifficulty, estimatedHeight);
  }

  public static RespondingEthPeer create(
      final EthProtocolManager ethProtocolManager,
      final Hash chainHeadHash,
      final UInt256 totalDifficulty) {
    return create(ethProtocolManager, chainHeadHash, totalDifficulty, DEFAULT_ESTIMATED_HEIGHT);
  }

  public static RespondingEthPeer create(
      final EthProtocolManager ethProtocolManager,
      final Hash chainHeadHash,
      final UInt256 totalDifficulty,
      final long estimatedHeight) {
    final EthPeers ethPeers = ethProtocolManager.ethContext().getEthPeers();

    final Set<Capability> caps = new HashSet<>(Collections.singletonList(EthProtocol.ETH63));
    final Queue<OutgoingMessage> outgoingMessages = new ArrayDeque<>();
    final MockPeerConnection peerConnection =
        new MockPeerConnection(
            caps, (cap, msg, conn) -> outgoingMessages.add(new OutgoingMessage(cap, msg)));
    ethPeers.registerConnection(peerConnection);
    final EthPeer peer = ethPeers.peer(peerConnection);
    peer.registerStatusReceived(chainHeadHash, totalDifficulty);
    peer.chainState().update(chainHeadHash, estimatedHeight);
    peer.registerStatusSent();

    return new RespondingEthPeer(ethProtocolManager, peerConnection, peer, outgoingMessages);
  }

  public EthPeer getEthPeer() {
    return ethPeer;
  }

  public void respondWhile(final Responder responder, final RespondWhileCondition condition) {
    while (condition.shouldRespond()) {
      respond(responder);
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

  /**
   * @param responder
   * @return True if any requests were processed
   */
  public boolean respond(final Responder responder) {
    // Respond to queued messages
    final List<OutgoingMessage> currentMessages = new ArrayList<>(outgoingMessages);
    outgoingMessages.clear();
    for (final OutgoingMessage msg : currentMessages) {
      try {
        final Optional<MessageData> maybeResponse =
            responder.respond(msg.capability, msg.messageData);
        maybeResponse.ifPresent(
            (response) -> {
              try {
                ethProtocolManager.processMessage(
                    msg.capability, new DefaultMessage(peerConnection, response));
              } finally {
                response.release();
              }
            });
      } finally {
        msg.messageData.release();
      }
    }
    return currentMessages.size() > 0;
  }

  public Optional<MessageData> peekNextOutgoingRequest() {
    if (outgoingMessages.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(outgoingMessages.peek().messageData);
  }

  public Stream<MessageData> pendingOutgoingRequests() {
    return outgoingMessages.stream().map(OutgoingMessage::messageData);
  }

  public boolean hasOutstandingRequests() {
    return !outgoingMessages.isEmpty();
  }

  public static Responder blockchainResponder(final Blockchain blockchain) {
    return (cap, msg) -> {
      MessageData response = null;
      switch (msg.getCode()) {
        case EthPV62.GET_BLOCK_HEADERS:
          response = EthServer.constructGetHeadersResponse(blockchain, msg, 200);
          break;
        case EthPV62.GET_BLOCK_BODIES:
          response = EthServer.constructGetBodiesResponse(blockchain, msg, 200);
          break;
        case EthPV63.GET_RECEIPTS:
          response = EthServer.constructGetReceiptsResponse(blockchain, msg, 200);
          break;
        case EthPV63.GET_NODE_DATA:
          response = EthServer.constructGetNodeDataResponse(msg, 200);
          break;
      }
      return Optional.ofNullable(response);
    };
  }

  public static <C> Responder partialResponder(
      final Blockchain blockchain, final ProtocolSchedule<C> protocolSchedule) {
    final Responder fullResponder = blockchainResponder(blockchain);
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
          try {
            final List<BlockHeader> originalHeaders =
                Lists.newArrayList(headersMessage.getHeaders(protocolSchedule));
            final List<BlockHeader> partialHeaders =
                originalHeaders.subList(0, originalHeaders.size() / 2);
            partialResponse = BlockHeadersMessage.create(partialHeaders);
          } finally {
            headersMessage.release();
          }
          break;
        case EthPV62.GET_BLOCK_BODIES:
          final BlockBodiesMessage bodiesMessage = BlockBodiesMessage.readFrom(originalResponse);
          try {
            final List<BlockBody> originalBodies =
                Lists.newArrayList(bodiesMessage.bodies(protocolSchedule));
            final List<BlockBody> partialBodies =
                originalBodies.subList(0, originalBodies.size() / 2);
            partialResponse = BlockBodiesMessage.create(partialBodies);
          } finally {
            bodiesMessage.release();
          }
          break;
        case EthPV63.GET_RECEIPTS:
          final ReceiptsMessage receiptsMessage = ReceiptsMessage.readFrom(originalResponse);
          try {
            final List<List<TransactionReceipt>> originalReceipts =
                Lists.newArrayList(receiptsMessage.receipts());
            final List<List<TransactionReceipt>> partialReceipts =
                originalReceipts.subList(0, originalReceipts.size() / 2);
            partialResponse = ReceiptsMessage.create(partialReceipts);
          } finally {
            receiptsMessage.release();
          }
          break;
        case EthPV63.GET_NODE_DATA:
          final NodeDataMessage nodeDataMessage = NodeDataMessage.readFrom(originalResponse);
          try {
            final List<BytesValue> originalNodeData =
                Lists.newArrayList(nodeDataMessage.nodeData());
            final List<BytesValue> partialNodeData =
                originalNodeData.subList(0, originalNodeData.size() / 2);
            partialResponse = NodeDataMessage.create(partialNodeData);
          } finally {
            nodeDataMessage.release();
          }
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
      }
      return Optional.ofNullable(response);
    };
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
