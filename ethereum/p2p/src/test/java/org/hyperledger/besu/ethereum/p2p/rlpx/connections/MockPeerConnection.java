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

import static org.hyperledger.besu.ethereum.p2p.peers.PeerTestHelper.createPeer;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.CapabilityMultiplexer;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MockSubProtocol;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.PeerInfo;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class MockPeerConnection extends AbstractPeerConnection {
  static final AtomicInteger connectionId = new AtomicInteger(0);
  private Optional<DisconnectReason> disconnectReason = Optional.empty();

  private MockPeerConnection(
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

  public static MockPeerConnection create() {
    return create(createPeer());
  }

  public static MockPeerConnection create(final Peer peer) {
    return create(peer, mock(PeerConnectionEventDispatcher.class));
  }

  public static MockPeerConnection create(
      final Peer peer, final PeerConnectionEventDispatcher eventDispatcher) {
    final List<SubProtocol> subProtocols = Arrays.asList(MockSubProtocol.create("eth"));
    final List<Capability> caps = Arrays.asList(Capability.create("eth", 63));
    final CapabilityMultiplexer multiplexer = new CapabilityMultiplexer(subProtocols, caps, caps);
    final PeerInfo peerInfo =
        new PeerInfo(5, "test", caps, peer.getEnodeURL().getListeningPortOrZero(), peer.getId());

    return new MockPeerConnection(
        peer,
        peerInfo,
        mock(InetSocketAddress.class),
        mock(InetSocketAddress.class),
        Integer.toString(connectionId.incrementAndGet()),
        multiplexer,
        eventDispatcher,
        NoOpMetricsSystem.NO_OP_LABELLED_3_COUNTER);
  }

  @Override
  protected void doSendMessage(final Capability capability, final MessageData message) {
    // Do nothing
  }

  @Override
  protected void closeConnectionImmediately() {
    // Do nothing
  }

  @Override
  protected void closeConnection() {
    // Do nothing
  }

  @Override
  public void disconnect(final DisconnectReason reason) {
    super.disconnect(reason);
    disconnectReason = Optional.of(reason);
  }

  public Optional<DisconnectReason> getDisconnectReason() {
    return disconnectReason;
  }
}
