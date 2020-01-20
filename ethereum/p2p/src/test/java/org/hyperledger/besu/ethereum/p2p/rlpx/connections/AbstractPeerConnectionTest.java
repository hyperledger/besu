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
package org.hyperledger.besu.ethereum.p2p.rlpx.connections;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.p2p.peers.PeerTestHelper.createPeer;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.CapabilityMultiplexer;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.PeerInfo;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.PingMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.WireMessageCodes;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class AbstractPeerConnectionTest {
  private final String connectionId = "1";
  private final Peer peer = createPeer();
  private final CapabilityMultiplexer multiplexer = mock(CapabilityMultiplexer.class);
  private final PeerConnectionEvents connectionEvents =
      new PeerConnectionEvents(new NoOpMetricsSystem());
  private final PeerInfo peerInfo = new PeerInfo(5, "foo", emptyList(), 0, Bytes.of(1));

  private TestPeerConnection connection;

  @Before
  public void setUp() {
    connection =
        new TestPeerConnection(
            peer,
            peerInfo,
            mock(InetSocketAddress.class),
            mock(InetSocketAddress.class),
            connectionId,
            multiplexer,
            connectionEvents,
            NoOpMetricsSystem.NO_OP_LABELLED_3_COUNTER);
  }

  @Test
  public void disconnect() {
    final AtomicBoolean disconnectCallbackInvoked = new AtomicBoolean(false);
    final DisconnectReason disconnectReason = DisconnectReason.USELESS_PEER;
    connectionEvents.subscribeDisconnect(
        (conn, reason, fromPeer) -> {
          disconnectCallbackInvoked.set(true);
          assertThat(reason).isEqualTo(disconnectReason);
          assertThat(conn).isEqualTo(connection);
          assertThat(fromPeer).isFalse();
          // Check the state of the connection as seen by disconnect handlers
          assertThat(conn.isDisconnected()).isTrue();
          assertThatThrownBy(() -> connection.send(null, PingMessage.get()));
        });
    connection.disconnect(disconnectReason);

    assertThat(disconnectCallbackInvoked).isTrue();
    assertThat(connection.isDisconnected()).isTrue();
    assertThat(connection.closedCount).isEqualTo(1);
    assertThat(connection.sentMessages.size()).isEqualTo(1);
    assertThat(connection.sentMessages.get(0).messageData.getCode())
        .isEqualTo(WireMessageCodes.DISCONNECT);
  }

  @Test
  public void disconnect_multipleInvocations() {
    final AtomicBoolean disconnectCallbackInvoked = new AtomicBoolean(false);
    final DisconnectReason disconnectReason = DisconnectReason.USELESS_PEER;
    connectionEvents.subscribeDisconnect(
        (conn, reason, fromPeer) -> {
          disconnectCallbackInvoked.set(true);
          connection.disconnect(disconnectReason);
        });
    connection.disconnect(disconnectReason);
    connection.disconnect(disconnectReason);
    connection.disconnect(disconnectReason);

    assertThat(disconnectCallbackInvoked).isTrue();
    assertThat(connection.isDisconnected()).isTrue();
    assertThat(connection.closedCount).isEqualTo(1);
    assertThat(connection.sentMessages.size()).isEqualTo(1);
    assertThat(connection.sentMessages.get(0).messageData.getCode())
        .isEqualTo(WireMessageCodes.DISCONNECT);
  }

  @Test
  public void send_successful() throws PeerNotConnected {
    connection.send(null, PingMessage.get());
    assertThat(connection.sentMessages.size()).isEqualTo(1);
    assertThat(connection.sentMessages).contains(new SentMessage(null, PingMessage.get()));
  }

  @Test
  public void send_afterDisconnect() {
    connection.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
    assertThatThrownBy(() -> connection.send(null, PingMessage.get()))
        .isInstanceOfAny(PeerNotConnected.class);
    assertThat(connection.sentMessages.size()).isEqualTo(1);
    assertThat(connection.sentMessages).doesNotContain(new SentMessage(null, PingMessage.get()));
  }

  @Test
  public void equals_true() {
    TestPeerConnection connection2 =
        new TestPeerConnection(
            peer,
            peerInfo,
            mock(InetSocketAddress.class),
            mock(InetSocketAddress.class),
            connectionId,
            multiplexer,
            connectionEvents,
            NoOpMetricsSystem.NO_OP_LABELLED_3_COUNTER);

    assertThat(connection2).isEqualTo(connection);
  }

  @Test
  public void equals_false_connectionIdsDiffer() {
    TestPeerConnection connection2 =
        new TestPeerConnection(
            peer,
            peerInfo,
            mock(InetSocketAddress.class),
            mock(InetSocketAddress.class),
            connectionId + "-other",
            multiplexer,
            connectionEvents,
            NoOpMetricsSystem.NO_OP_LABELLED_3_COUNTER);

    assertThat(connection2).isNotEqualTo(connection);
  }

  private static class TestPeerConnection extends AbstractPeerConnection {
    private final List<SentMessage> sentMessages = new ArrayList<>();
    private int closedCount = 0;

    TestPeerConnection(
        final Peer peer,
        final PeerInfo peerInfo,
        final InetSocketAddress localAddress,
        final InetSocketAddress remoteAddress,
        final String connectionId,
        final CapabilityMultiplexer multiplexer,
        final PeerConnectionEventDispatcher connectionEventDispatcher,
        final LabelledMetric<Counter> outboundMessagesCounter) {
      super(
          peer,
          peerInfo,
          localAddress,
          remoteAddress,
          connectionId,
          multiplexer,
          connectionEventDispatcher,
          outboundMessagesCounter);
    }

    @Override
    protected void doSendMessage(final Capability capability, final MessageData message) {
      sentMessages.add(new SentMessage(capability, message));
    }

    @Override
    protected void closeConnectionImmediately() {
      closedCount++;
    }

    @Override
    protected void closeConnection() {
      closedCount++;
    }
  }

  private static class SentMessage {
    final Capability capability;
    final MessageData messageData;

    private SentMessage(final Capability capability, final MessageData messageData) {
      this.capability = capability;
      this.messageData = messageData;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof SentMessage)) {
        return false;
      }
      final SentMessage that = (SentMessage) o;
      return Objects.equals(capability, that.capability)
          && Objects.equals(messageData, that.messageData);
    }

    @Override
    public int hashCode() {
      return Objects.hash(capability, messageData);
    }
  }
}
