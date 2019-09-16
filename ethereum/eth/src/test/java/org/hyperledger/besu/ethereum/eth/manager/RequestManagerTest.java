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
package org.hyperledger.besu.ethereum.eth.manager;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.testutil.TestClock;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.Test;

public class RequestManagerTest {

  @Test
  public void dispatchesMessagesReceivedAfterRegisteringCallback() throws Exception {
    final EthPeer peer = createPeer();
    final RequestManager requestManager = new RequestManager(peer);

    final AtomicInteger sendCount = new AtomicInteger(0);
    final RequestManager.RequestSender sender = sendCount::incrementAndGet;
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
    final RequestManager.ResponseStream stream = requestManager.dispatchRequest(sender);
    assertThat(sendCount.get()).isEqualTo(1);
    stream.then(responseHandler);

    // Dispatch message
    final EthMessage mockMessage = mockMessage(peer);
    requestManager.dispatchResponse(mockMessage);

    // Response handler should get message
    assertThat(receivedMessages.size()).isEqualTo(1);
    assertThat(receivedMessages.get(0)).isEqualTo(mockMessage.getData());
    assertThat(closedCount.get()).isEqualTo(1);
  }

  @Test
  public void dispatchesMessagesReceivedBeforeRegisteringCallback() throws Exception {
    final EthPeer peer = createPeer();
    final RequestManager requestManager = new RequestManager(peer);

    final AtomicInteger sendCount = new AtomicInteger(0);
    final RequestManager.RequestSender sender = sendCount::incrementAndGet;
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
    final RequestManager.ResponseStream stream = requestManager.dispatchRequest(sender);
    assertThat(sendCount.get()).isEqualTo(1);

    // Dispatch message
    final EthMessage mockMessage = mockMessage(peer);
    requestManager.dispatchResponse(mockMessage);

    // Response handler should get message
    stream.then(responseHandler);
    assertThat(receivedMessages.size()).isEqualTo(1);
    assertThat(receivedMessages.get(0)).isEqualTo(mockMessage.getData());
    assertThat(closedCount.get()).isEqualTo(1);
  }

  @Test
  public void dispatchesMessagesReceivedBeforeAndAfterRegisteringCallback() throws Exception {
    final EthPeer peer = createPeer();
    final RequestManager requestManager = new RequestManager(peer);

    final AtomicInteger sendCount = new AtomicInteger(0);
    final RequestManager.RequestSender sender = sendCount::incrementAndGet;
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
    final RequestManager.ResponseStream stream = requestManager.dispatchRequest(sender);
    assertThat(sendCount.get()).isEqualTo(1);
    requestManager.dispatchRequest(sender);
    assertThat(sendCount.get()).isEqualTo(2);

    // Dispatch first message
    EthMessage mockMessage = mockMessage(peer);
    requestManager.dispatchResponse(mockMessage);

    // Response handler should get messages sent before it is registered
    stream.then(responseHandler);
    assertThat(receivedMessages.size()).isEqualTo(1);
    assertThat(receivedMessages.get(0)).isEqualTo(mockMessage.getData());
    assertThat(closedCount.get()).isEqualTo(0);

    // Dispatch second message
    mockMessage = mockMessage(peer);
    requestManager.dispatchResponse(mockMessage);

    // Response handler should get messages sent after it is registered
    assertThat(receivedMessages.size()).isEqualTo(2);
    assertThat(receivedMessages.get(1)).isEqualTo(mockMessage.getData());
    assertThat(closedCount.get()).isEqualTo(1);
  }

  @Test
  public void dispatchesMessagesToMultipleStreams() throws Exception {
    final EthPeer peer = createPeer();
    final RequestManager requestManager = new RequestManager(peer);

    final AtomicInteger sendCount = new AtomicInteger(0);
    final RequestManager.RequestSender sender = sendCount::incrementAndGet;

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
    final RequestManager.ResponseStream streamA = requestManager.dispatchRequest(sender);
    final RequestManager.ResponseStream streamB = requestManager.dispatchRequest(sender);
    assertThat(sendCount.get()).isEqualTo(2);
    streamA.then(responseHandlerA);

    // Dispatch message
    EthMessage mockMessage = mockMessage(peer);
    requestManager.dispatchResponse(mockMessage);

    // Response handler A should get message
    assertThat(receivedMessagesA.size()).isEqualTo(1);
    assertThat(receivedMessagesA.get(0)).isEqualTo(mockMessage.getData());
    assertThat(closedCountA.get()).isEqualTo(0);

    streamB.then(responseHandlerB);

    // Response handler B should get message
    assertThat(receivedMessagesB.size()).isEqualTo(1);
    assertThat(receivedMessagesB.get(0)).isEqualTo(mockMessage.getData());
    assertThat(closedCountB.get()).isEqualTo(0);

    // Dispatch second message
    mockMessage = mockMessage(peer);
    requestManager.dispatchResponse(mockMessage);

    // Response handler A should get message
    assertThat(receivedMessagesA.size()).isEqualTo(2);
    assertThat(receivedMessagesA.get(1)).isEqualTo(mockMessage.getData());
    assertThat(closedCountA.get()).isEqualTo(1);
    // Response handler B should get message
    assertThat(receivedMessagesB.size()).isEqualTo(2);
    assertThat(receivedMessagesB.get(1)).isEqualTo(mockMessage.getData());
    assertThat(closedCountB.get()).isEqualTo(1);
  }

  private EthMessage mockMessage(final EthPeer peer) {
    return new EthMessage(peer, new RawMessage(1, BytesValue.EMPTY));
  }

  private EthPeer createPeer() {
    final Set<Capability> caps = new HashSet<>(Collections.singletonList(EthProtocol.ETH63));
    final PeerConnection peerConnection = new MockPeerConnection(caps);
    final Consumer<EthPeer> onPeerReady = (peer) -> {};
    return new EthPeer(peerConnection, EthProtocol.NAME, onPeerReady, TestClock.fixed());
  }
}
