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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.testutil.TestClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class RequestManagerTest {

  private final AtomicLong requestIdCounter = new AtomicLong(1);

  @Test
  public void dispatchesMessagesReceivedAfterRegisteringCallback() throws Exception {
    for (final boolean supportsRequestId : List.of(true, false)) {
      final EthPeer peer = createPeer();
      final RequestManager requestManager =
          new RequestManager(peer, supportsRequestId, EthProtocol.NAME);

      final AtomicInteger sendCount = new AtomicInteger(0);
      final RequestManager.RequestSender sender = __ -> sendCount.incrementAndGet();
      final List<MessageData> receivedMessages = new ArrayList<>();
      final AtomicInteger closedCount = new AtomicInteger(0);
      final RequestManager.ResponseCallback responseHandler =
          (closed, msg, p) -> {
            if (closed) {
              closedCount.incrementAndGet();
            } else {
              receivedMessages.add(msg);
            }
          };

      // Send request
      final RequestManager.ResponseStream stream =
          requestManager.dispatchRequest(sender, new RawMessage(0x01, Bytes.EMPTY));
      assertThat(sendCount.get()).isEqualTo(1);
      stream.then(responseHandler);

      // Dispatch message
      final EthMessage mockMessage = mockMessage(peer, supportsRequestId);
      requestManager.dispatchResponse(mockMessage);

      // Response handler should get message
      assertThat(receivedMessages).hasSize(1);
      assertResponseCorrect(receivedMessages.get(0), mockMessage, supportsRequestId);
      assertThat(closedCount.get()).isEqualTo(1);
    }
  }

  @Test
  public void dispatchesMessagesReceivedBeforeRegisteringCallback() throws Exception {
    for (final boolean supportsRequestId : List.of(true, false)) {
      final EthPeer peer = createPeer();
      final RequestManager requestManager =
          new RequestManager(peer, supportsRequestId, EthProtocol.NAME);

      final AtomicInteger sendCount = new AtomicInteger(0);
      final RequestManager.RequestSender sender = __ -> sendCount.incrementAndGet();
      final List<MessageData> receivedMessages = new ArrayList<>();
      final AtomicInteger closedCount = new AtomicInteger(0);
      final RequestManager.ResponseCallback responseHandler =
          (closed, msg, p) -> {
            if (closed) {
              closedCount.incrementAndGet();
            } else {
              receivedMessages.add(msg);
            }
          };

      // Send request
      final RequestManager.ResponseStream stream =
          requestManager.dispatchRequest(sender, new RawMessage(0x01, Bytes.EMPTY));
      assertThat(sendCount.get()).isEqualTo(1);

      // Dispatch message
      final EthMessage mockMessage = mockMessage(peer, supportsRequestId);
      requestManager.dispatchResponse(mockMessage);

      // Response handler should get message
      stream.then(responseHandler);
      assertThat(receivedMessages).hasSize(1);
      assertResponseCorrect(receivedMessages.get(0), mockMessage, supportsRequestId);
      assertThat(closedCount.get()).isEqualTo(1);
    }
  }

  @Test
  public void dispatchesMessagesReceivedBeforeAndAfterRegisteringCallback() throws Exception {
    final EthPeer peer = createPeer();
    final RequestManager requestManager = new RequestManager(peer, false, EthProtocol.NAME);

    final AtomicInteger sendCount = new AtomicInteger(0);
    final RequestManager.RequestSender sender = __ -> sendCount.incrementAndGet();
    final List<MessageData> receivedMessages = new ArrayList<>();
    final AtomicInteger closedCount = new AtomicInteger(0);
    final RequestManager.ResponseCallback responseHandler =
        (closed, msg, p) -> {
          if (closed) {
            closedCount.incrementAndGet();
          } else {
            receivedMessages.add(msg);
          }
        };

    // Send 2 requests so we can receive 2 messages before closing
    final RequestManager.ResponseStream stream =
        requestManager.dispatchRequest(sender, new RawMessage(0x01, Bytes.EMPTY));
    assertThat(sendCount.get()).isEqualTo(1);
    requestManager.dispatchRequest(sender, new RawMessage(0x01, Bytes.EMPTY));
    assertThat(sendCount.get()).isEqualTo(2);

    // Dispatch first message
    EthMessage mockMessage = mockMessage(peer, false);
    requestManager.dispatchResponse(mockMessage);

    // Response handler should get messages sent before it is registered
    stream.then(responseHandler);
    assertThat(receivedMessages).hasSize(1);
    assertResponseCorrect(receivedMessages.get(0), mockMessage, false);
    assertThat(closedCount.get()).isZero();

    // Dispatch second message
    mockMessage = mockMessage(peer, false);
    requestManager.dispatchResponse(mockMessage);

    // Response handler should get messages sent after it is registered
    assertThat(receivedMessages).hasSize(2);
    assertResponseCorrect(receivedMessages.get(1), mockMessage, false);
    assertThat(closedCount.get()).isEqualTo(1);
  }

  @Test
  public void dispatchesMessagesToMultipleStreamsIfNoRequestId() throws Exception {
    final EthPeer peer = createPeer();
    final RequestManager requestManager = new RequestManager(peer, false, EthProtocol.NAME);

    final AtomicInteger sendCount = new AtomicInteger(0);
    final RequestManager.RequestSender sender = __ -> sendCount.incrementAndGet();

    final List<MessageData> receivedMessagesA = new ArrayList<>();
    final AtomicInteger closedCountA = new AtomicInteger(0);
    final RequestManager.ResponseCallback responseHandlerA =
        (closed, msg, p) -> {
          if (closed) {
            closedCountA.incrementAndGet();
          } else {
            receivedMessagesA.add(msg);
          }
        };
    final List<MessageData> receivedMessagesB = new ArrayList<>();
    final AtomicInteger closedCountB = new AtomicInteger(0);
    final RequestManager.ResponseCallback responseHandlerB =
        (closed, msg, p) -> {
          if (closed) {
            closedCountB.incrementAndGet();
          } else {
            receivedMessagesB.add(msg);
          }
        };

    // Send request
    final RequestManager.ResponseStream streamA =
        requestManager.dispatchRequest(sender, new RawMessage(0x01, Bytes.EMPTY));
    final RequestManager.ResponseStream streamB =
        requestManager.dispatchRequest(sender, new RawMessage(0x01, Bytes.EMPTY));
    assertThat(sendCount.get()).isEqualTo(2);
    streamA.then(responseHandlerA);

    // Dispatch message
    EthMessage mockMessage = mockMessage(peer, false);
    requestManager.dispatchResponse(mockMessage);

    // Response handler A should get message
    assertThat(receivedMessagesA).hasSize(1);
    assertResponseCorrect(receivedMessagesA.get(0), mockMessage, false);
    assertThat(closedCountA.get()).isZero();

    streamB.then(responseHandlerB);

    // Response handler B should get message
    assertThat(receivedMessagesB).hasSize(1);
    assertResponseCorrect(receivedMessagesB.get(0), mockMessage, false);
    assertThat(closedCountB.get()).isZero();

    // Dispatch second message
    mockMessage = mockMessage(peer, false);
    requestManager.dispatchResponse(mockMessage);

    // Response handler A should get message
    assertThat(receivedMessagesA).hasSize(2);
    assertResponseCorrect(receivedMessagesA.get(1), mockMessage, false);
    assertThat(closedCountA.get()).isEqualTo(1);
    // Response handler B should get message
    assertThat(receivedMessagesB).hasSize(2);
    assertResponseCorrect(receivedMessagesB.get(1), mockMessage, false);
    assertThat(closedCountB.get()).isEqualTo(1);
  }

  @Test
  public void dispatchesMessagesToSingleStreamIfRequestId() throws Exception {
    final EthPeer peer = createPeer();
    final RequestManager requestManager = new RequestManager(peer, true, EthProtocol.NAME);

    final AtomicInteger sendCount = new AtomicInteger(0);
    final RequestManager.RequestSender sender = __ -> sendCount.incrementAndGet();

    final List<MessageData> receivedMessagesA = new ArrayList<>();
    final AtomicInteger closedCountA = new AtomicInteger(0);
    final RequestManager.ResponseCallback responseHandlerA =
        (closed, msg, p) -> {
          if (closed) {
            closedCountA.incrementAndGet();
          } else {
            receivedMessagesA.add(msg);
          }
        };
    final List<MessageData> receivedMessagesB = new ArrayList<>();
    final AtomicInteger closedCountB = new AtomicInteger(0);
    final RequestManager.ResponseCallback responseHandlerB =
        (closed, msg, p) -> {
          if (closed) {
            closedCountB.incrementAndGet();
          } else {
            receivedMessagesB.add(msg);
          }
        };

    // Send request
    final RequestManager.ResponseStream streamA =
        requestManager.dispatchRequest(sender, new RawMessage(0x01, Bytes.EMPTY));
    final RequestManager.ResponseStream streamB =
        requestManager.dispatchRequest(sender, new RawMessage(0x01, Bytes.EMPTY));
    assertThat(sendCount.get()).isEqualTo(2);

    streamA.then(responseHandlerA);
    streamB.then(responseHandlerB);

    // Dispatch message
    final EthMessage mockMessage = mockMessage(peer, true);
    requestManager.dispatchResponse(mockMessage);

    // Only handler A or B should get message
    assertThat(receivedMessagesA.size() + receivedMessagesB.size()).isEqualTo(1);
  }

  private EthMessage mockMessage(final EthPeer peer, final boolean supportsRequestId) {
    if (!supportsRequestId) {
      return new EthMessage(peer, new RawMessage(1, Bytes.EMPTY));
    }
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    rlpOutput.startList();
    final long requestId = requestIdCounter.getAndIncrement();
    rlpOutput.writeLongScalar(requestId);
    rlpOutput.writeBytes(Bytes.EMPTY);
    rlpOutput.endList();
    return new EthMessage(peer, new RawMessage(1, rlpOutput.encoded()));
  }

  private void assertResponseCorrect(
      final MessageData response, final EthMessage mockMessage, final boolean supportsRequestId) {
    assertThat(response)
        .isEqualTo(
            (supportsRequestId
                ? mockMessage.getData().unwrapMessageData().getValue()
                : mockMessage.getData()));
  }

  private EthPeer createPeer() {
    final Set<Capability> caps = new HashSet<>(Collections.singletonList(EthProtocol.ETH63));
    final PeerConnection peerConnection = new MockPeerConnection(caps);
    final Consumer<EthPeer> onPeerReady = (peer) -> {};
    return new EthPeer(
        peerConnection,
        onPeerReady,
        Collections.emptyList(),
        EthProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE,
        TestClock.fixed(),
        Collections.emptyList(),
        Bytes.random(64));
  }

  @Test
  public void disconnectsPeerOnBadMessage() throws Exception {
    for (final boolean supportsRequestId : List.of(true, false)) {
      final EthPeer peer = createPeer();
      final RequestManager requestManager =
          new RequestManager(peer, supportsRequestId, EthProtocol.NAME);

      requestManager
          .dispatchRequest(
              messageData -> RLP.input(messageData.getData()).nextSize(),
              new RawMessage(0x01, Bytes.EMPTY))
          .then(
              (closed, msg, p) -> {
                if (!closed) {
                  RLP.input(msg.getData()).skipNext();
                }
              });
      final EthMessage mockMessage =
          new EthMessage(peer, new RawMessage(1, Bytes.of(0x81, 0x82, 0x83, 0x84)));

      requestManager.dispatchResponse(mockMessage);
      assertThat(peer.isDisconnected()).isTrue();
    }
  }
}
