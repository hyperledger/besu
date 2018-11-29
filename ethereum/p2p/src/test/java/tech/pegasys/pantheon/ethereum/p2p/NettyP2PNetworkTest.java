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
package tech.pegasys.pantheon.ethereum.p2p;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.RlpxConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.netty.NettyP2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.netty.exceptions.IncompatiblePeerException;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.Endpoint;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.SubProtocol;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.net.InetAddress;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Test;

/** Tests for {@link NettyP2PNetwork}. */
public final class NettyP2PNetworkTest {

  private final Vertx vertx = Vertx.vertx();

  @After
  public void closeVertx() {
    vertx.close();
  }

  @Test
  public void handshaking() throws Exception {
    final DiscoveryConfiguration noDiscovery = DiscoveryConfiguration.create().setActive(false);
    final SECP256K1.KeyPair listenKp = SECP256K1.KeyPair.generate();
    final Capability cap = Capability.create("eth", 63);
    try (final P2PNetwork listener =
            new NettyP2PNetwork(
                vertx,
                listenKp,
                NetworkingConfiguration.create()
                    .setDiscovery(noDiscovery)
                    .setSupportedProtocols(subProtocol())
                    .setRlpx(RlpxConfiguration.create().setBindPort(0)),
                singletonList(cap),
                () -> false,
                new PeerBlacklist(),
                new NoOpMetricsSystem());
        final P2PNetwork connector =
            new NettyP2PNetwork(
                vertx,
                SECP256K1.KeyPair.generate(),
                NetworkingConfiguration.create()
                    .setSupportedProtocols(subProtocol())
                    .setRlpx(RlpxConfiguration.create().setBindPort(0))
                    .setDiscovery(noDiscovery),
                singletonList(cap),
                () -> false,
                new PeerBlacklist(),
                new NoOpMetricsSystem())) {

      final int listenPort = listener.getSelf().getPort();
      listener.run();
      connector.run();
      final BytesValue listenId = listenKp.getPublicKey().getEncodedBytes();
      assertThat(
              connector
                  .connect(
                      new DefaultPeer(
                          listenId,
                          new Endpoint(
                              InetAddress.getLoopbackAddress().getHostAddress(),
                              listenPort,
                              OptionalInt.of(listenPort))))
                  .get(30L, TimeUnit.SECONDS)
                  .getPeer()
                  .getNodeId())
          .isEqualTo(listenId);
    }
  }

  @Test
  public void preventMultipleConnections() throws Exception {

    final DiscoveryConfiguration noDiscovery = DiscoveryConfiguration.create().setActive(false);
    final SECP256K1.KeyPair listenKp = SECP256K1.KeyPair.generate();
    final List<Capability> capabilities = singletonList(Capability.create("eth", 62));
    final SubProtocol subProtocol = subProtocol();
    try (final P2PNetwork listener =
            new NettyP2PNetwork(
                vertx,
                listenKp,
                NetworkingConfiguration.create()
                    .setSupportedProtocols(subProtocol)
                    .setDiscovery(noDiscovery)
                    .setRlpx(RlpxConfiguration.create().setBindPort(0)),
                capabilities,
                () -> true,
                new PeerBlacklist(),
                new NoOpMetricsSystem());
        final P2PNetwork connector =
            new NettyP2PNetwork(
                vertx,
                SECP256K1.KeyPair.generate(),
                NetworkingConfiguration.create()
                    .setSupportedProtocols(subProtocol)
                    .setRlpx(RlpxConfiguration.create().setBindPort(0))
                    .setDiscovery(noDiscovery),
                capabilities,
                () -> true,
                new PeerBlacklist(),
                new NoOpMetricsSystem())) {
      final int listenPort = listener.getSelf().getPort();
      listener.run();
      connector.run();
      final BytesValue listenId = listenKp.getPublicKey().getEncodedBytes();
      assertThat(
              connector
                  .connect(
                      new DefaultPeer(
                          listenId,
                          new Endpoint(
                              InetAddress.getLoopbackAddress().getHostAddress(),
                              listenPort,
                              OptionalInt.of(listenPort))))
                  .get(30L, TimeUnit.SECONDS)
                  .getPeer()
                  .getNodeId())
          .isEqualTo(listenId);
      final CompletableFuture<PeerConnection> secondConnectionFuture =
          connector.connect(
              new DefaultPeer(
                  listenId,
                  new Endpoint(
                      InetAddress.getLoopbackAddress().getHostAddress(),
                      listenPort,
                      OptionalInt.of(listenPort))));
      assertThatThrownBy(secondConnectionFuture::get)
          .hasCause(new IllegalStateException("Client already connected"));
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
    final DiscoveryConfiguration noDiscovery = DiscoveryConfiguration.create().setActive(false);
    final SECP256K1.KeyPair listenKp = SECP256K1.KeyPair.generate();
    final int maxPeers = 1;
    final List<Capability> cap = singletonList(Capability.create("eth", 62));
    final SubProtocol subProtocol = subProtocol();
    try (final P2PNetwork listener =
            new NettyP2PNetwork(
                vertx,
                listenKp,
                NetworkingConfiguration.create()
                    .setDiscovery(noDiscovery)
                    .setRlpx(RlpxConfiguration.create().setBindPort(0).setMaxPeers(maxPeers))
                    .setSupportedProtocols(subProtocol),
                cap,
                () -> true,
                new PeerBlacklist(),
                new NoOpMetricsSystem());
        final P2PNetwork connector1 =
            new NettyP2PNetwork(
                vertx,
                SECP256K1.KeyPair.generate(),
                NetworkingConfiguration.create()
                    .setDiscovery(noDiscovery)
                    .setRlpx(RlpxConfiguration.create().setBindPort(0))
                    .setSupportedProtocols(subProtocol),
                cap,
                () -> true,
                new PeerBlacklist(),
                new NoOpMetricsSystem());
        final P2PNetwork connector2 =
            new NettyP2PNetwork(
                vertx,
                SECP256K1.KeyPair.generate(),
                NetworkingConfiguration.create()
                    .setDiscovery(noDiscovery)
                    .setRlpx(RlpxConfiguration.create().setBindPort(0))
                    .setSupportedProtocols(subProtocol),
                cap,
                () -> true,
                new PeerBlacklist(),
                new NoOpMetricsSystem())) {

      final int listenPort = listener.getSelf().getPort();
      // Setup listener and first connection
      listener.run();
      connector1.run();
      final BytesValue listenId = listenKp.getPublicKey().getEncodedBytes();
      final Peer listeningPeer =
          new DefaultPeer(
              listenId,
              new Endpoint(
                  InetAddress.getLoopbackAddress().getHostAddress(),
                  listenPort,
                  OptionalInt.of(listenPort)));
      assertThat(connector1.connect(listeningPeer).get(30L, TimeUnit.SECONDS).getPeer().getNodeId())
          .isEqualTo(listenId);

      // Setup second connection and check that connection is not accepted
      final CompletableFuture<PeerConnection> peerFuture = new CompletableFuture<>();
      final CompletableFuture<DisconnectReason> reasonFuture = new CompletableFuture<>();
      connector2.subscribeDisconnect(
          (peerConnection, reason, initiatedByPeer) -> {
            peerFuture.complete(peerConnection);
            reasonFuture.complete(reason);
          });
      connector2.run();
      assertThat(connector2.connect(listeningPeer).get(30L, TimeUnit.SECONDS).getPeer().getNodeId())
          .isEqualTo(listenId);
      assertThat(peerFuture.get(30L, TimeUnit.SECONDS).getPeer().getNodeId()).isEqualTo(listenId);
      assertThat(reasonFuture.get(30L, TimeUnit.SECONDS))
          .isEqualByComparingTo(DisconnectReason.TOO_MANY_PEERS);
    }
  }

  @Test
  public void rejectPeerWithNoSharedCaps() throws Exception {
    final DiscoveryConfiguration noDiscovery = DiscoveryConfiguration.create().setActive(false);
    final SECP256K1.KeyPair listenKp = SECP256K1.KeyPair.generate();

    final SubProtocol subprotocol1 = subProtocol();
    final Capability cap1 = Capability.create(subprotocol1.getName(), 63);
    final SubProtocol subprotocol2 = subProtocol2();
    final Capability cap2 = Capability.create(subprotocol2.getName(), 63);
    try (final P2PNetwork listener =
            new NettyP2PNetwork(
                vertx,
                listenKp,
                NetworkingConfiguration.create()
                    .setDiscovery(noDiscovery)
                    .setSupportedProtocols(subprotocol1)
                    .setRlpx(RlpxConfiguration.create().setBindPort(0)),
                singletonList(cap1),
                () -> false,
                new PeerBlacklist(),
                new NoOpMetricsSystem());
        final P2PNetwork connector =
            new NettyP2PNetwork(
                vertx,
                SECP256K1.KeyPair.generate(),
                NetworkingConfiguration.create()
                    .setSupportedProtocols(subprotocol2)
                    .setRlpx(RlpxConfiguration.create().setBindPort(0))
                    .setDiscovery(noDiscovery),
                singletonList(cap2),
                () -> false,
                new PeerBlacklist(),
                new NoOpMetricsSystem())) {
      final int listenPort = listener.getSelf().getPort();
      listener.run();
      connector.run();
      final BytesValue listenId = listenKp.getPublicKey().getEncodedBytes();

      final Peer listenerPeer =
          new DefaultPeer(
              listenId,
              new Endpoint(
                  InetAddress.getLoopbackAddress().getHostAddress(),
                  listenPort,
                  OptionalInt.of(listenPort)));
      final CompletableFuture<PeerConnection> connectFuture = connector.connect(listenerPeer);
      assertThatThrownBy(connectFuture::get).hasCauseInstanceOf(IncompatiblePeerException.class);
    }
  }

  @Test
  public void rejectIncomingConnectionFromBlacklistedPeer() throws Exception {
    final DiscoveryConfiguration noDiscovery = DiscoveryConfiguration.create().setActive(false);
    final SECP256K1.KeyPair localKp = SECP256K1.KeyPair.generate();
    final SECP256K1.KeyPair remoteKp = SECP256K1.KeyPair.generate();
    final BytesValue localId = localKp.getPublicKey().getEncodedBytes();
    final BytesValue remoteId = remoteKp.getPublicKey().getEncodedBytes();
    final PeerBlacklist localBlacklist = new PeerBlacklist();
    final PeerBlacklist remoteBlacklist = new PeerBlacklist();

    final SubProtocol subprotocol = subProtocol();
    final Capability cap = Capability.create(subprotocol.getName(), 63);
    try (final P2PNetwork localNetwork =
            new NettyP2PNetwork(
                vertx,
                localKp,
                NetworkingConfiguration.create()
                    .setDiscovery(noDiscovery)
                    .setSupportedProtocols(subprotocol)
                    .setRlpx(RlpxConfiguration.create().setBindPort(0)),
                singletonList(cap),
                () -> false,
                localBlacklist,
                new NoOpMetricsSystem());
        final P2PNetwork remoteNetwork =
            new NettyP2PNetwork(
                vertx,
                remoteKp,
                NetworkingConfiguration.create()
                    .setSupportedProtocols(subprotocol)
                    .setRlpx(RlpxConfiguration.create().setBindPort(0))
                    .setDiscovery(noDiscovery),
                singletonList(cap),
                () -> false,
                remoteBlacklist,
                new NoOpMetricsSystem())) {
      final int localListenPort = localNetwork.getSelf().getPort();
      final int remoteListenPort = remoteNetwork.getSelf().getPort();
      final Peer localPeer =
          new DefaultPeer(
              localId,
              new Endpoint(
                  InetAddress.getLoopbackAddress().getHostAddress(),
                  localListenPort,
                  OptionalInt.of(localListenPort)));

      final Peer remotePeer =
          new DefaultPeer(
              remoteId,
              new Endpoint(
                  InetAddress.getLoopbackAddress().getHostAddress(),
                  remoteListenPort,
                  OptionalInt.of(remoteListenPort)));

      // Blacklist the remote peer
      localBlacklist.add(remotePeer);

      localNetwork.run();
      remoteNetwork.run();

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
      assertThat(connectFuture.get(5L, TimeUnit.SECONDS).getPeer().getNodeId()).isEqualTo(localId);
      assertThat(peerFuture.get(5L, TimeUnit.SECONDS).getPeer().getNodeId()).isEqualTo(localId);
      assertThat(reasonFuture.get(5L, TimeUnit.SECONDS))
          .isEqualByComparingTo(DisconnectReason.USELESS_PEER);
    }
  }

  private SubProtocol subProtocol() {
    return new SubProtocol() {
      @Override
      public String getName() {
        return "eth";
      }

      @Override
      public int messageSpace(final int protocolVersion) {
        return 8;
      }

      @Override
      public boolean isValidMessageCode(final int protocolVersion, final int code) {
        return true;
      }
    };
  }

  private SubProtocol subProtocol2() {
    return new SubProtocol() {
      @Override
      public String getName() {
        return "ryj";
      }

      @Override
      public int messageSpace(final int protocolVersion) {
        return 8;
      }

      @Override
      public boolean isValidMessageCode(final int protocolVersion, final int code) {
        return true;
      }
    };
  }

  @Test
  public void shouldSendClientQuittingWhenNetworkStops() {
    final NettyP2PNetwork nettyP2PNetwork = mockNettyP2PNetwork();
    final Peer peer = mockPeer();
    final PeerConnection peerConnection = mockPeerConnection();

    nettyP2PNetwork.connect(peer).complete(peerConnection);
    nettyP2PNetwork.stop();

    verify(peerConnection).disconnect(eq(DisconnectReason.CLIENT_QUITTING));
  }

  private PeerConnection mockPeerConnection() {
    final PeerInfo peerInfo = mock(PeerInfo.class);
    when(peerInfo.getNodeId()).thenReturn(BytesValue.fromHexString("0x00"));
    final PeerConnection peerConnection = mock(PeerConnection.class);
    when(peerConnection.getPeer()).thenReturn(peerInfo);
    return peerConnection;
  }

  private NettyP2PNetwork mockNettyP2PNetwork() {
    final DiscoveryConfiguration noDiscovery = DiscoveryConfiguration.create().setActive(false);
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    final Capability cap = Capability.create("eth", 63);
    final NetworkingConfiguration networkingConfiguration =
        NetworkingConfiguration.create()
            .setDiscovery(noDiscovery)
            .setSupportedProtocols(subProtocol())
            .setRlpx(RlpxConfiguration.create().setBindPort(0));

    return new NettyP2PNetwork(
        mock(Vertx.class),
        keyPair,
        networkingConfiguration,
        singletonList(cap),
        () -> false,
        new PeerBlacklist(),
        new NoOpMetricsSystem());
  }

  private Peer mockPeer() {
    final Peer peer = mock(Peer.class);
    when(peer.getId())
        .thenReturn(
            BytesValue.fromHexString(
                "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));
    when(peer.getEndpoint()).thenReturn(new Endpoint("127.0.0.1", 30303, OptionalInt.of(30303)));
    return peer;
  }
}
