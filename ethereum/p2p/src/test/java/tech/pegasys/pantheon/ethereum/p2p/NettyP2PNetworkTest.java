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
import static org.assertj.core.api.Java6Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
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
import tech.pegasys.pantheon.ethereum.permissioning.LocalPermissioningConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.NodeLocalConfigPermissioningController;
import tech.pegasys.pantheon.ethereum.permissioning.node.NodePermissioningController;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link NettyP2PNetwork}. */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public final class NettyP2PNetworkTest {

  @Mock private NodePermissioningController nodePermissioningController;

  @Mock private Blockchain blockchain;

  private ArgumentCaptor<BlockAddedObserver> observerCaptor =
      ArgumentCaptor.forClass(BlockAddedObserver.class);

  private final Vertx vertx = Vertx.vertx();

  private final String selfEnodeString =
      "enode://5f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:1111";
  private final EnodeURL selfEnode = new EnodeURL(selfEnodeString);

  @Before
  public void before() {
    when(blockchain.observeBlockAdded(observerCaptor.capture())).thenReturn(1L);
  }

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
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty());
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
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty())) {

      final int listenPort = listener.getLocalPeerInfo().getPort();
      listener.start();
      connector.start();
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
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty());
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
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty())) {
      final int listenPort = listener.getLocalPeerInfo().getPort();
      listener.start();
      connector.start();
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
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty());
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
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty());
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
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty())) {

      final int listenPort = listener.getLocalPeerInfo().getPort();
      // Setup listener and first connection
      listener.start();
      connector1.start();
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
      connector2.start();
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
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty());
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
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty())) {
      final int listenPort = listener.getLocalPeerInfo().getPort();
      listener.start();
      connector.start();
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
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty());
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
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty())) {
      final int localListenPort = localNetwork.getLocalPeerInfo().getPort();
      final int remoteListenPort = remoteNetwork.getLocalPeerInfo().getPort();
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

      localNetwork.start();
      remoteNetwork.start();

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
          .isEqualByComparingTo(DisconnectReason.UNKNOWN);
    }
  }

  @Test
  public void rejectIncomingConnectionFromNonWhitelistedPeer() throws Exception {
    final DiscoveryConfiguration noDiscovery = DiscoveryConfiguration.create().setActive(false);
    final SECP256K1.KeyPair localKp = SECP256K1.KeyPair.generate();
    final SECP256K1.KeyPair remoteKp = SECP256K1.KeyPair.generate();
    final BytesValue localId = localKp.getPublicKey().getEncodedBytes();
    final PeerBlacklist localBlacklist = new PeerBlacklist();
    final PeerBlacklist remoteBlacklist = new PeerBlacklist();
    final LocalPermissioningConfiguration config = LocalPermissioningConfiguration.createDefault();
    final Path tempFile = Files.createTempFile("test", "test");
    tempFile.toFile().deleteOnExit();
    config.setNodePermissioningConfigFilePath(tempFile.toAbsolutePath().toString());

    final NodeLocalConfigPermissioningController localWhitelistController =
        new NodeLocalConfigPermissioningController(config, Collections.emptyList(), selfEnode);
    // turn on whitelisting by adding a different node NOT remote node
    localWhitelistController.addNodes(Arrays.asList(mockPeer().getEnodeURI()));

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
                new NoOpMetricsSystem(),
                Optional.of(localWhitelistController),
                Optional.empty());
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
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty())) {
      final int localListenPort = localNetwork.getLocalPeerInfo().getPort();
      final Peer localPeer =
          new DefaultPeer(
              localId,
              new Endpoint(
                  InetAddress.getLoopbackAddress().getHostAddress(),
                  localListenPort,
                  OptionalInt.of(localListenPort)));

      localNetwork.start();
      remoteNetwork.start();

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
          .isEqualByComparingTo(DisconnectReason.UNKNOWN);
    }
  }

  @Test
  public void addingMaintainedNetworkPeerStartsConnection() {
    final NettyP2PNetwork network = spy(mockNettyP2PNetwork());
    final Peer peer = mockPeer();

    assertThat(network.addMaintainConnectionPeer(peer)).isTrue();

    assertThat(network.peerMaintainConnectionList).contains(peer);
    verify(network, times(1)).connect(peer);
  }

  @Test
  public void addingRepeatMaintainedPeersReturnsFalse() {
    final NettyP2PNetwork network = mockNettyP2PNetwork();
    final Peer peer = mockPeer();
    assertThat(network.addMaintainConnectionPeer(peer)).isTrue();
    assertThat(network.addMaintainConnectionPeer(peer)).isFalse();
  }

  @Test
  public void checkMaintainedConnectionPeersTriesToConnect() {
    final NettyP2PNetwork network = spy(mockNettyP2PNetwork());
    final Peer peer = mockPeer();
    network.peerMaintainConnectionList.add(peer);

    network.checkMaintainedConnectionPeers();
    verify(network, times(1)).connect(peer);
  }

  @Test
  public void checkMaintainedConnectionPeersDoesntReconnectPendingPeers() {
    final NettyP2PNetwork network = spy(mockNettyP2PNetwork());
    final Peer peer = mockPeer();

    network.pendingConnections.put(peer, new CompletableFuture<>());

    network.checkMaintainedConnectionPeers();
    verify(network, times(0)).connect(peer);
  }

  @Test
  public void checkMaintainedConnectionPeersDoesntReconnectConnectedPeers() {
    final NettyP2PNetwork network = spy(mockNettyP2PNetwork());
    final Peer peer = mockPeer();
    verify(network, never()).connect(peer);
    assertThat(network.addMaintainConnectionPeer(peer)).isTrue();
    verify(network, times(1)).connect(peer);

    {
      final CompletableFuture<PeerConnection> connection;
      connection = network.pendingConnections.remove(peer);
      assertThat(connection).isNotNull();
      assertThat(connection.cancel(true)).isTrue();
    }

    {
      final PeerConnection peerConnection = mockPeerConnection(peer.getId());
      network.connections.registerConnection(peerConnection);
      network.checkMaintainedConnectionPeers();
      verify(network, times(1)).connect(peer);
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

      @Override
      public String messageName(final int protocolVersion, final int code) {
        return INVALID_MESSAGE_NAME;
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

      @Override
      public String messageName(final int protocolVersion, final int code) {
        return INVALID_MESSAGE_NAME;
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

  @Test
  public void shouldntAttemptNewConnectionToPendingPeer() {
    final NettyP2PNetwork nettyP2PNetwork = mockNettyP2PNetwork();
    final Peer peer = mockPeer();

    final CompletableFuture<PeerConnection> connectingFuture = nettyP2PNetwork.connect(peer);
    assertThat(nettyP2PNetwork.connect(peer)).isEqualTo(connectingFuture);
  }

  @Test
  public void whenStartingNetworkWithNodePermissioningShouldSubscribeToBlockAddedEvents() {
    final NettyP2PNetwork nettyP2PNetwork = mockNettyP2PNetwork();

    nettyP2PNetwork.start();

    verify(blockchain).observeBlockAdded(any());
  }

  @Test
  public void whenStartingNetworkWithNodePermissioningWithoutBlockchainShouldThrowIllegalState() {
    blockchain = null;
    final NettyP2PNetwork nettyP2PNetwork = mockNettyP2PNetwork();

    final Throwable throwable = catchThrowable(nettyP2PNetwork::start);
    assertThat(throwable)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "NettyP2PNetwork permissioning needs to listen to BlockAddedEvents. Blockchain can't be null.");
  }

  @Test
  public void whenStoppingNetworkWithNodePermissioningShouldUnsubscribeBlockAddedEvents() {
    final NettyP2PNetwork nettyP2PNetwork = mockNettyP2PNetwork();

    nettyP2PNetwork.start();
    nettyP2PNetwork.stop();

    verify(blockchain).removeObserver(eq(1L));
  }

  @Test
  public void onBlockAddedShouldCheckPermissionsForAllPeers() {
    final BlockAddedEvent blockAddedEvent = blockAddedEvent();
    final NettyP2PNetwork nettyP2PNetwork = mockNettyP2PNetwork();
    final Peer localPeer = mockPeer("127.0.0.1", 30301);
    final Peer remotePeer1 = mockPeer("127.0.0.2", 30302);
    final Peer remotePeer2 = mockPeer("127.0.0.3", 30303);

    final PeerConnection peerConnection1 = mockPeerConnection(localPeer, remotePeer1);
    final PeerConnection peerConnection2 = mockPeerConnection(localPeer, remotePeer2);

    nettyP2PNetwork.start();
    nettyP2PNetwork.connect(remotePeer1).complete(peerConnection1);
    nettyP2PNetwork.connect(remotePeer2).complete(peerConnection2);

    final BlockAddedObserver blockAddedObserver = observerCaptor.getValue();
    blockAddedObserver.onBlockAdded(blockAddedEvent, blockchain);

    verify(nodePermissioningController, times(2)).isPermitted(any(), any());
  }

  @Test
  public void onBlockAddedAndPeerNotPermittedShouldDisconnect() {
    final BlockAddedEvent blockAddedEvent = blockAddedEvent();
    final NettyP2PNetwork nettyP2PNetwork = mockNettyP2PNetwork();

    final Peer localPeer = mockPeer("127.0.0.1", 30301);
    final Peer permittedPeer = mockPeer("127.0.0.2", 30302);
    final Peer notPermittedPeer = mockPeer("127.0.0.3", 30303);

    final PeerConnection permittedPeerConnection = mockPeerConnection(localPeer, permittedPeer);
    final PeerConnection notPermittedPeerConnection =
        mockPeerConnection(localPeer, notPermittedPeer);

    final EnodeURL permittedEnodeURL = new EnodeURL(permittedPeer.getEnodeURI());
    final EnodeURL notPermittedEnodeURL = new EnodeURL(notPermittedPeer.getEnodeURI());

    nettyP2PNetwork.start();
    nettyP2PNetwork.connect(permittedPeer).complete(permittedPeerConnection);
    nettyP2PNetwork.connect(notPermittedPeer).complete(notPermittedPeerConnection);

    lenient()
        .when(nodePermissioningController.isPermitted(any(), enodeEq(notPermittedEnodeURL)))
        .thenReturn(false);
    lenient()
        .when(nodePermissioningController.isPermitted(any(), enodeEq(permittedEnodeURL)))
        .thenReturn(true);

    final BlockAddedObserver blockAddedObserver = observerCaptor.getValue();
    blockAddedObserver.onBlockAdded(blockAddedEvent, blockchain);

    verify(notPermittedPeerConnection).disconnect(eq(DisconnectReason.REQUESTED));
    verify(permittedPeerConnection, never()).disconnect(any());
  }

  private BlockAddedEvent blockAddedEvent() {
    return mock(BlockAddedEvent.class);
  }

  private PeerConnection mockPeerConnection(final BytesValue id) {
    final PeerInfo peerInfo = mock(PeerInfo.class);
    when(peerInfo.getNodeId()).thenReturn(id);
    final PeerConnection peerConnection = mock(PeerConnection.class);
    when(peerConnection.getPeer()).thenReturn(peerInfo);
    return peerConnection;
  }

  private PeerConnection mockPeerConnection() {
    return mockPeerConnection(BytesValue.fromHexString("0x00"));
  }

  private PeerConnection mockPeerConnection(final Peer localPeer, final Peer remotePeer) {
    final PeerInfo peerInfo = mock(PeerInfo.class);
    doReturn(remotePeer.getId()).when(peerInfo).getNodeId();

    final PeerConnection peerConnection = mock(PeerConnection.class);
    when(peerConnection.getPeer()).thenReturn(peerInfo);

    Endpoint localEndpoint = localPeer.getEndpoint();
    InetSocketAddress localSocketAddress =
        new InetSocketAddress(localEndpoint.getHost(), localEndpoint.getTcpPort().getAsInt());
    when(peerConnection.getLocalAddress()).thenReturn(localSocketAddress);

    Endpoint remoteEndpoint = remotePeer.getEndpoint();
    InetSocketAddress remoteSocketAddress =
        new InetSocketAddress(remoteEndpoint.getHost(), remoteEndpoint.getTcpPort().getAsInt());
    when(peerConnection.getRemoteAddress()).thenReturn(remoteSocketAddress);

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
        new NoOpMetricsSystem(),
        Optional.empty(),
        Optional.of(nodePermissioningController),
        blockchain);
  }

  private Peer mockPeer() {
    return mockPeer(
        SECP256K1.KeyPair.generate().getPublicKey().getEncodedBytes(), "127.0.0.1", 30303);
  }

  private Peer mockPeer(final String host, final int port) {
    final BytesValue id = SECP256K1.KeyPair.generate().getPublicKey().getEncodedBytes();
    return mockPeer(id, host, port);
  }

  private Peer mockPeer(final BytesValue id, final String host, final int port) {
    final Peer peer = mock(Peer.class);
    final Endpoint endpoint = new Endpoint(host, port, OptionalInt.of(port));
    final String enodeURL =
        String.format(
            "enode://%s@%s:%d?discport=%d",
            id.toString().substring(2),
            endpoint.getHost(),
            endpoint.getUdpPort(),
            endpoint.getTcpPort().getAsInt());

    when(peer.getId()).thenReturn(id);
    when(peer.getEndpoint()).thenReturn(endpoint);
    when(peer.getEnodeURI()).thenReturn(enodeURL);

    return peer;
  }

  public static class EnodeURLMatcher implements ArgumentMatcher<EnodeURL> {

    private final EnodeURL enodeURL;

    EnodeURLMatcher(final EnodeURL enodeURL) {
      this.enodeURL = enodeURL;
    }

    @Override
    public boolean matches(final EnodeURL argument) {
      if (argument == null) {
        return false;
      } else {
        return enodeURL.getNodeId().equals(argument.getNodeId())
            && enodeURL.getIp().equals(argument.getIp())
            && enodeURL.getListeningPort().equals(argument.getListeningPort());
      }
    }
  }

  private EnodeURL enodeEq(final EnodeURL enodeURL) {
    return argThat(new EnodeURLMatcher(enodeURL));
  }
}
