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
package tech.pegasys.pantheon.ethereum.p2p.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.RlpxConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.network.exceptions.IncompatiblePeerException;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.EnodeURL;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.permissions.PeerPermissions;
import tech.pegasys.pantheon.ethereum.p2p.permissions.PeerPermissions.Action;
import tech.pegasys.pantheon.ethereum.p2p.permissions.PeerPermissionsBlacklist;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.SubProtocol;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class P2PNetworkTest {
  private final Vertx vertx = Vertx.vertx();
  private final NetworkingConfiguration config =
      NetworkingConfiguration.create()
          .setDiscovery(DiscoveryConfiguration.create().setActive(false))
          .setRlpx(RlpxConfiguration.create().setBindPort(0).setSupportedProtocols(subProtocol()));

  @After
  public void closeVertx() {
    vertx.close();
  }

  @Test
  public void handshaking() throws Exception {
    final SECP256K1.KeyPair listenKp = SECP256K1.KeyPair.generate();
    try (final P2PNetwork listener = builder().keyPair(listenKp).build();
        final P2PNetwork connector = builder().build()) {

      listener.start();
      connector.start();
      final EnodeURL listenerEnode = listener.getLocalEnode().get();
      final BytesValue listenId = listenerEnode.getNodeId();
      final int listenPort = listenerEnode.getListeningPort().getAsInt();

      assertThat(
              connector
                  .connect(createPeer(listenId, listenPort))
                  .get(30L, TimeUnit.SECONDS)
                  .getPeerInfo()
                  .getNodeId())
          .isEqualTo(listenId);
    }
  }

  @Test
  public void preventMultipleConnections() throws Exception {
    final SECP256K1.KeyPair listenKp = SECP256K1.KeyPair.generate();
    try (final P2PNetwork listener = builder().keyPair(listenKp).build();
        final P2PNetwork connector = builder().build()) {

      listener.start();
      connector.start();
      final EnodeURL listenerEnode = listener.getLocalEnode().get();
      final BytesValue listenId = listenerEnode.getNodeId();
      final int listenPort = listenerEnode.getListeningPort().getAsInt();

      final CompletableFuture<PeerConnection> firstFuture =
          connector.connect(createPeer(listenId, listenPort));
      final CompletableFuture<PeerConnection> secondFuture =
          connector.connect(createPeer(listenId, listenPort));

      final PeerConnection firstConnection = firstFuture.get(30L, TimeUnit.SECONDS);
      final PeerConnection secondConnection = secondFuture.get(30L, TimeUnit.SECONDS);
      assertThat(firstConnection.getPeerInfo().getNodeId()).isEqualTo(listenId);

      // Connections should reference the same instance - i.e. we shouldn't create 2 distinct
      // connections
      assertThat(firstConnection == secondConnection).isTrue();
    }
  }

  /**
   * Tests that max peers setting is honoured and inbound connections that would exceed the limit
   * are correctly disconnected.
   *
   * @throws Exception On Failure
   */
  @Test
  public void limitMaxPeers() throws Exception {
    final SECP256K1.KeyPair listenKp = SECP256K1.KeyPair.generate();
    final int maxPeers = 1;
    final NetworkingConfiguration listenerConfig =
        NetworkingConfiguration.create()
            .setDiscovery(DiscoveryConfiguration.create().setActive(false))
            .setRlpx(
                RlpxConfiguration.create()
                    .setBindPort(0)
                    .setMaxPeers(maxPeers)
                    .setSupportedProtocols(subProtocol()));
    try (final P2PNetwork listener = builder().keyPair(listenKp).config(listenerConfig).build();
        final P2PNetwork connector1 = builder().build();
        final P2PNetwork connector2 = builder().build()) {

      // Setup listener and first connection
      listener.start();
      connector1.start();
      final EnodeURL listenerEnode = listener.getLocalEnode().get();
      final BytesValue listenId = listenerEnode.getNodeId();
      final int listenPort = listenerEnode.getListeningPort().getAsInt();

      final Peer listeningPeer = createPeer(listenId, listenPort);
      assertThat(
              connector1
                  .connect(listeningPeer)
                  .get(30L, TimeUnit.SECONDS)
                  .getPeerInfo()
                  .getNodeId())
          .isEqualTo(listenId);

      // Setup second connection and check that connection is not accepted
      final CompletableFuture<PeerConnection> peerFuture = new CompletableFuture<>();
      final CompletableFuture<DisconnectReason> reasonFuture = new CompletableFuture<>();
      connector2.subscribeDisconnect(
          (peerConnection, reason, initiatedByPeer) -> {
            peerFuture.complete(peerConnection);
            reasonFuture.complete(reason);
          });
      connector2.start();
      assertThat(
              connector2
                  .connect(listeningPeer)
                  .get(30L, TimeUnit.SECONDS)
                  .getPeerInfo()
                  .getNodeId())
          .isEqualTo(listenId);
      assertThat(peerFuture.get(30L, TimeUnit.SECONDS).getPeerInfo().getNodeId())
          .isEqualTo(listenId);
      assertThat(reasonFuture.get(30L, TimeUnit.SECONDS))
          .isEqualByComparingTo(DisconnectReason.TOO_MANY_PEERS);
    }
  }

  @Test
  public void rejectPeerWithNoSharedCaps() throws Exception {
    final SECP256K1.KeyPair listenKp = SECP256K1.KeyPair.generate();

    final SubProtocol subprotocol1 = subProtocol("eth");
    final Capability cap1 = Capability.create(subprotocol1.getName(), 63);
    final SubProtocol subprotocol2 = subProtocol("oth");
    final Capability cap2 = Capability.create(subprotocol2.getName(), 63);
    try (final P2PNetwork listener =
            builder().keyPair(listenKp).supportedCapabilities(cap1).build();
        final P2PNetwork connector =
            builder().keyPair(SECP256K1.KeyPair.generate()).supportedCapabilities(cap2).build()) {
      listener.start();
      connector.start();
      final EnodeURL listenerEnode = listener.getLocalEnode().get();
      final BytesValue listenId = listenerEnode.getNodeId();
      final int listenPort = listenerEnode.getListeningPort().getAsInt();

      final Peer listenerPeer = createPeer(listenId, listenPort);
      final CompletableFuture<PeerConnection> connectFuture = connector.connect(listenerPeer);
      assertThatThrownBy(connectFuture::get).hasCauseInstanceOf(IncompatiblePeerException.class);
    }
  }

  @Test
  public void rejectIncomingConnectionFromBlacklistedPeer() throws Exception {
    final PeerPermissionsBlacklist localBlacklist = PeerPermissionsBlacklist.create();

    try (final P2PNetwork localNetwork = builder().peerPermissions(localBlacklist).build();
        final P2PNetwork remoteNetwork = builder().build()) {

      localNetwork.start();
      remoteNetwork.start();

      final EnodeURL localEnode = localNetwork.getLocalEnode().get();
      final BytesValue localId = localEnode.getNodeId();
      final int localPort = localEnode.getListeningPort().getAsInt();

      final EnodeURL remoteEnode = remoteNetwork.getLocalEnode().get();
      final BytesValue remoteId = remoteEnode.getNodeId();
      final int remotePort = remoteEnode.getListeningPort().getAsInt();

      final Peer localPeer = createPeer(localId, localPort);
      final Peer remotePeer = createPeer(remoteId, remotePort);

      // Blacklist the remote peer
      localBlacklist.add(remotePeer);

      // Setup disconnect listener
      final CompletableFuture<PeerConnection> peerFuture = new CompletableFuture<>();
      final CompletableFuture<DisconnectReason> reasonFuture = new CompletableFuture<>();
      remoteNetwork.subscribeDisconnect(
          (peerConnection, reason, initiatedByPeer) -> {
            peerFuture.complete(peerConnection);
            reasonFuture.complete(reason);
          });

      // Remote connect to local
      final CompletableFuture<PeerConnection> connectFuture = remoteNetwork.connect(localPeer);

      // Check connection is made, and then a disconnect is registered at remote
      assertThat(connectFuture.get(5L, TimeUnit.SECONDS).getPeerInfo().getNodeId())
          .isEqualTo(localId);
      assertThat(peerFuture.get(5L, TimeUnit.SECONDS).getPeerInfo().getNodeId()).isEqualTo(localId);
      assertThat(reasonFuture.get(5L, TimeUnit.SECONDS))
          .isEqualByComparingTo(DisconnectReason.UNKNOWN);
    }
  }

  @Test
  public void rejectIncomingConnectionFromDisallowedPeer() throws Exception {
    final PeerPermissions peerPermissions = mock(PeerPermissions.class);
    when(peerPermissions.isPermitted(any(), any(), any())).thenReturn(true);

    try (final P2PNetwork localNetwork = builder().peerPermissions(peerPermissions).build();
        final P2PNetwork remoteNetwork = builder().build()) {

      localNetwork.start();
      remoteNetwork.start();

      final EnodeURL localEnode = localNetwork.getLocalEnode().get();
      final Peer localPeer = DefaultPeer.fromEnodeURL(localEnode);
      final Peer remotePeer = DefaultPeer.fromEnodeURL(remoteNetwork.getLocalEnode().get());

      // Deny incoming connection permissions for remotePeer
      when(peerPermissions.isPermitted(
              eq(localPeer), eq(remotePeer), eq(Action.RLPX_ALLOW_NEW_INBOUND_CONNECTION)))
          .thenReturn(false);

      // Setup disconnect listener
      final CompletableFuture<PeerConnection> peerFuture = new CompletableFuture<>();
      final CompletableFuture<DisconnectReason> reasonFuture = new CompletableFuture<>();
      remoteNetwork.subscribeDisconnect(
          (peerConnection, reason, initiatedByPeer) -> {
            peerFuture.complete(peerConnection);
            reasonFuture.complete(reason);
          });

      // Remote connect to local
      final CompletableFuture<PeerConnection> connectFuture = remoteNetwork.connect(localPeer);

      // Check connection is made, and then a disconnect is registered at remote
      final BytesValue localId = localEnode.getNodeId();
      assertThat(connectFuture.get(5L, TimeUnit.SECONDS).getPeerInfo().getNodeId())
          .isEqualTo(localId);
      assertThat(peerFuture.get(5L, TimeUnit.SECONDS).getPeerInfo().getNodeId()).isEqualTo(localId);
      assertThat(reasonFuture.get(5L, TimeUnit.SECONDS))
          .isEqualByComparingTo(DisconnectReason.UNKNOWN);
    }
  }

  private Peer createPeer(final BytesValue nodeId, final int listenPort) {
    return DefaultPeer.fromEnodeURL(
        EnodeURL.builder()
            .ipAddress(InetAddress.getLoopbackAddress().getHostAddress())
            .nodeId(nodeId)
            .discoveryAndListeningPorts(listenPort)
            .build());
  }

  private static SubProtocol subProtocol() {
    return subProtocol("eth");
  }

  private static SubProtocol subProtocol(final String name) {
    return new SubProtocol() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public int messageSpace(final int protocolVersion) {
        return 8;
      }

      @Override
      public boolean isValidMessageCode(final int protocolVersion, final int code) {
        return true;
      }

      @Override
      public String messageName(final int protocolVersion, final int code) {
        return INVALID_MESSAGE_NAME;
      }
    };
  }

  private DefaultP2PNetwork.Builder builder() {
    return DefaultP2PNetwork.builder()
        .vertx(vertx)
        .config(config)
        .keyPair(KeyPair.generate())
        .metricsSystem(new NoOpMetricsSystem())
        .clock(TestClock.fixed())
        .supportedCapabilities(Arrays.asList(Capability.create("eth", 63)));
  }
}
