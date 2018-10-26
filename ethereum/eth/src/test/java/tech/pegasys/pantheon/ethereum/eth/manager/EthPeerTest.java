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

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.manager.RequestManager.ResponseCallback;
import tech.pegasys.pantheon.ethereum.eth.manager.RequestManager.ResponseStream;
import tech.pegasys.pantheon.ethereum.eth.messages.BlockBodiesMessage;
import tech.pegasys.pantheon.ethereum.eth.messages.BlockHeadersMessage;
import tech.pegasys.pantheon.ethereum.eth.messages.ReceiptsMessage;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.testutil.BlockDataGenerator;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.Test;

public class EthPeerTest {
  private static final BlockDataGenerator gen = new BlockDataGenerator();

  @Test
  public void getHeadersStream() throws PeerNotConnected {
    final ResponseStreamSupplier getStream =
        (peer) -> peer.getHeadersByHash(gen.hash(), 5, 0, false);
    final MessageData targetMessage =
        BlockHeadersMessage.create(Arrays.asList(gen.header(), gen.header()));
    final MessageData otherMessage =
        BlockBodiesMessage.create(Arrays.asList(gen.body(), gen.body()));

    messageStream(getStream, targetMessage, otherMessage);
  }

  @Test
  public void getBodiesStream() throws PeerNotConnected {
    final ResponseStreamSupplier getStream =
        (peer) -> peer.getBodies(Arrays.asList(gen.hash(), gen.hash()));
    final MessageData targetMessage =
        BlockBodiesMessage.create(Arrays.asList(gen.body(), gen.body()));
    final MessageData otherMessage =
        BlockHeadersMessage.create(Arrays.asList(gen.header(), gen.header()));

    messageStream(getStream, targetMessage, otherMessage);
  }

  @Test
  public void getReceiptsStream() throws PeerNotConnected {
    final ResponseStreamSupplier getStream =
        (peer) -> peer.getReceipts(Arrays.asList(gen.hash(), gen.hash()));
    final MessageData targetMessage =
        ReceiptsMessage.create(Collections.singletonList(gen.receipts(gen.block())));
    final MessageData otherMessage =
        BlockHeadersMessage.create(Arrays.asList(gen.header(), gen.header()));

    messageStream(getStream, targetMessage, otherMessage);
  }

  @Test
  public void closeStreamsOnPeerDisconnect() throws PeerNotConnected {
    final EthPeer peer = createPeer();
    // Setup headers stream
    final AtomicInteger headersClosedCount = new AtomicInteger(0);
    peer.getHeadersByHash(gen.hash(), 5, 0, false)
        .then(
            (closed, msg, p) -> {
              if (closed) {
                headersClosedCount.incrementAndGet();
              }
            });
    // Bodies stream
    final AtomicInteger bodiesClosedCount = new AtomicInteger(0);
    peer.getBodies(Arrays.asList(gen.hash(), gen.hash()))
        .then(
            (closed, msg, p) -> {
              if (closed) {
                bodiesClosedCount.incrementAndGet();
              }
            });
    // Receipts stream
    final AtomicInteger receiptsClosedCount = new AtomicInteger(0);
    peer.getReceipts(Arrays.asList(gen.hash(), gen.hash()))
        .then(
            (closed, msg, p) -> {
              if (closed) {
                receiptsClosedCount.incrementAndGet();
              }
            });

    // Sanity check
    assertThat(headersClosedCount.get()).isEqualTo(0);
    assertThat(bodiesClosedCount.get()).isEqualTo(0);
    assertThat(receiptsClosedCount.get()).isEqualTo(0);

    // Disconnect and check
    peer.handleDisconnect();
    assertThat(headersClosedCount.get()).isEqualTo(1);
    assertThat(bodiesClosedCount.get()).isEqualTo(1);
    assertThat(receiptsClosedCount.get()).isEqualTo(1);
  }

  @Test
  public void listenForMultipleStreams() throws PeerNotConnected {
    // Setup peer and messages
    final EthPeer peer = createPeer();
    final EthMessage headersMessage =
        new EthMessage(peer, BlockHeadersMessage.create(Arrays.asList(gen.header(), gen.header())));
    final EthMessage bodiesMessage =
        new EthMessage(peer, BlockBodiesMessage.create(Arrays.asList(gen.body(), gen.body())));
    final EthMessage otherMessage =
        new EthMessage(
            peer, ReceiptsMessage.create(Collections.singletonList(gen.receipts(gen.block()))));

    // Set up stream for headers
    final AtomicInteger headersMessageCount = new AtomicInteger(0);
    final AtomicInteger headersClosedCount = new AtomicInteger(0);
    final ResponseStream headersStream =
        peer.getHeadersByHash(gen.hash(), 5, 0, false)
            .then(
                (closed, msg, p) -> {
                  if (closed) {
                    headersClosedCount.incrementAndGet();
                  } else {
                    headersMessageCount.incrementAndGet();
                    assertThat(msg.getCode()).isEqualTo(headersMessage.getData().getCode());
                  }
                });
    // Set up stream for bodies
    final AtomicInteger bodiesMessageCount = new AtomicInteger(0);
    final AtomicInteger bodiesClosedCount = new AtomicInteger(0);
    final ResponseStream bodiesStream =
        peer.getBodies(Arrays.asList(gen.hash(), gen.hash()))
            .then(
                (closed, msg, p) -> {
                  if (closed) {
                    bodiesClosedCount.incrementAndGet();
                  } else {
                    bodiesMessageCount.incrementAndGet();
                    assertThat(msg.getCode()).isEqualTo(bodiesMessage.getData().getCode());
                  }
                });

    // Dispatch some messages and check expectations
    peer.dispatch(headersMessage);
    assertThat(headersMessageCount.get()).isEqualTo(1);
    assertThat(headersClosedCount.get()).isEqualTo(1);
    assertThat(bodiesMessageCount.get()).isEqualTo(0);
    assertThat(bodiesClosedCount.get()).isEqualTo(0);

    peer.dispatch(bodiesMessage);
    assertThat(headersMessageCount.get()).isEqualTo(1);
    assertThat(headersClosedCount.get()).isEqualTo(1);
    assertThat(bodiesMessageCount.get()).isEqualTo(1);
    assertThat(bodiesClosedCount.get()).isEqualTo(1);

    peer.dispatch(otherMessage);
    assertThat(headersMessageCount.get()).isEqualTo(1);
    assertThat(headersClosedCount.get()).isEqualTo(1);
    assertThat(bodiesMessageCount.get()).isEqualTo(1);
    assertThat(bodiesClosedCount.get()).isEqualTo(1);

    // Dispatch again after close and check that nothing fires
    peer.dispatch(headersMessage);
    peer.dispatch(bodiesMessage);
    peer.dispatch(otherMessage);
    assertThat(headersMessageCount.get()).isEqualTo(1);
    assertThat(headersClosedCount.get()).isEqualTo(1);
    assertThat(bodiesMessageCount.get()).isEqualTo(1);
    assertThat(bodiesClosedCount.get()).isEqualTo(1);
  }

  private void messageStream(
      final ResponseStreamSupplier getStream,
      final MessageData targetMessage,
      final MessageData otherMessage)
      throws PeerNotConnected {
    // Setup peer and ask for stream
    final EthPeer peer = createPeer();
    final AtomicInteger messageCount = new AtomicInteger(0);
    final AtomicInteger closedCount = new AtomicInteger(0);
    final int targetCode = targetMessage.getCode();
    final ResponseCallback responseHandler =
        (closed, msg, p) -> {
          if (closed) {
            closedCount.incrementAndGet();
          } else {
            messageCount.incrementAndGet();
            assertThat(msg.getCode()).isEqualTo(targetCode);
          }
        };

    // Set up 1 stream
    getStream.get(peer).then(responseHandler);

    final EthMessage targetEthMessage = new EthMessage(peer, targetMessage);
    // Dispatch message and check that stream processes messages
    peer.dispatch(targetEthMessage);
    assertThat(messageCount.get()).isEqualTo(1);
    assertThat(closedCount.get()).isEqualTo(1);

    // Check that no new messages are delivered
    getStream.get(peer);
    peer.dispatch(targetEthMessage);
    assertThat(messageCount.get()).isEqualTo(1);
    assertThat(closedCount.get()).isEqualTo(1);

    // Set up 2 streams
    getStream.get(peer).then(responseHandler);
    getStream.get(peer).then(responseHandler);

    // Reset counters
    messageCount.set(0);
    closedCount.set(0);

    // Dispatch message and check that stream processes messages
    peer.dispatch(targetEthMessage);
    assertThat(messageCount.get()).isEqualTo(2);
    assertThat(closedCount.get()).isEqualTo(0);

    // Dispatch unrelated message and check that it is not process
    final EthMessage otherEthMessage = new EthMessage(peer, otherMessage);
    peer.dispatch(otherEthMessage);
    assertThat(messageCount.get()).isEqualTo(2);
    assertThat(closedCount.get()).isEqualTo(0);

    // Dispatch last oustanding message and check that streams are closed
    peer.dispatch(targetEthMessage);
    assertThat(messageCount.get()).isEqualTo(4);
    assertThat(closedCount.get()).isEqualTo(2);

    // Check that no new messages are delivered
    getStream.get(peer);
    peer.dispatch(targetEthMessage);
    assertThat(messageCount.get()).isEqualTo(4);
    assertThat(closedCount.get()).isEqualTo(2);

    // Open stream, then close it and check no messages are processed
    final ResponseStream stream = getStream.get(peer).then(responseHandler);
    // Reset counters
    messageCount.set(0);
    closedCount.set(0);
    stream.close();
    getStream.get(peer);
    peer.dispatch(targetEthMessage);
    assertThat(messageCount.get()).isEqualTo(0);
    assertThat(closedCount.get()).isEqualTo(1);
  }

  private EthPeer createPeer() {
    final Set<Capability> caps = new HashSet<>(Collections.singletonList(EthProtocol.ETH63));
    final PeerConnection peerConnection = new MockPeerConnection(caps);
    final Consumer<EthPeer> onPeerReady = (peer) -> {};
    return new EthPeer(peerConnection, EthProtocol.NAME, onPeerReady);
  }

  @FunctionalInterface
  interface ResponseStreamSupplier {
    ResponseStream get(EthPeer peer) throws PeerNotConnected;
  }
}
