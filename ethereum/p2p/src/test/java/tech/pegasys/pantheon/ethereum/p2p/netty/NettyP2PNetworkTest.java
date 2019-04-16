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
package tech.pegasys.pantheon.ethereum.p2p.netty;

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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.RlpxConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryEvent.PeerBondedEvent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryStatus;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
  private final EnodeURL selfEnode = EnodeURL.fromString(selfEnodeString);

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
                new PeerBlacklist(),
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty())) {

      listener.start();
      connector.start();
      final EnodeURL listenerEnode = listener.getLocalEnode().get();
      final BytesValue listenId = listenerEnode.getNodeId();
      final int listenPort = listenerEnode.getListeningPort();

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
                  .getPeerInfo()
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
                new PeerBlacklist(),
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty())) {

      listener.start();
      connector.start();
      final EnodeURL listenerEnode = listener.getLocalEnode().get();
      final BytesValue listenId = listenerEnode.getNodeId();
      final int listenPort = listenerEnode.getListeningPort();

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
                  .getPeerInfo()
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
                new PeerBlacklist(),
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty())) {

      // Setup listener and first connection
      listener.start();
      connector1.start();
      final EnodeURL listenerEnode = listener.getLocalEnode().get();
      final BytesValue listenId = listenerEnode.getNodeId();
      final int listenPort = listenerEnode.getListeningPort();

      final Peer listeningPeer =
          new DefaultPeer(
              listenId,
              new Endpoint(
                  InetAddress.getLoopbackAddress().getHostAddress(),
                  listenPort,
                  OptionalInt.of(listenPort)));
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
                new PeerBlacklist(),
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty())) {

      listener.start();
      connector.start();
      final EnodeURL listenerEnode = listener.getLocalEnode().get();
      final BytesValue listenId = listenerEnode.getNodeId();
      final int listenPort = listenerEnode.getListeningPort();

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
                remoteBlacklist,
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty())) {

      localNetwork.start();
      remoteNetwork.start();

      final EnodeURL localEnode = localNetwork.getLocalEnode().get();
      final BytesValue localId = localEnode.getNodeId();
      final int localPort = localEnode.getListeningPort();

      final EnodeURL remoteEnode = remoteNetwork.getLocalEnode().get();
      final BytesValue remoteId = remoteEnode.getNodeId();
      final int remotePort = remoteEnode.getListeningPort();

      final Peer localPeer =
          new DefaultPeer(
              localId,
              new Endpoint(
                  InetAddress.getLoopbackAddress().getHostAddress(),
                  localPort,
                  OptionalInt.of(localPort)));

      final Peer remotePeer =
          new DefaultPeer(
              remoteId,
              new Endpoint(
                  InetAddress.getLoopbackAddress().getHostAddress(),
                  remotePort,
                  OptionalInt.of(remotePort)));

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
      assertThat(connectFuture.get(5L, TimeUnit.SECONDS).getPeerInfo().getNodeId())
          .isEqualTo(localId);
      assertThat(peerFuture.get(5L, TimeUnit.SECONDS).getPeerInfo().getNodeId()).isEqualTo(localId);
      assertThat(reasonFuture.get(5L, TimeUnit.SECONDS))
          .isEqualByComparingTo(DisconnectReason.UNKNOWN);
    }
  }

  @Test
  public void rejectIncomingConnectionFromNonWhitelistedPeer() throws Exception {
    final DiscoveryConfiguration noDiscovery = DiscoveryConfiguration.create().setActive(false);
    final SECP256K1.KeyPair localKp = SECP256K1.KeyPair.generate();
    final SECP256K1.KeyPair remoteKp = SECP256K1.KeyPair.generate();
    final PeerBlacklist localBlacklist = new PeerBlacklist();
    final PeerBlacklist remoteBlacklist = new PeerBlacklist();
    final LocalPermissioningConfiguration config = LocalPermissioningConfiguration.createDefault();
    final Path tempFile = Files.createTempFile("test", "test");
    tempFile.toFile().deleteOnExit();
    config.setNodePermissioningConfigFilePath(tempFile.toAbsolutePath().toString());

    final NodeLocalConfigPermissioningController localWhitelistController =
        new NodeLocalConfigPermissioningController(config, Collections.emptyList(), selfEnode);
    // turn on whitelisting by adding a different node NOT remote node
    localWhitelistController.addNodes(Arrays.asList(mockPeer().getEnodeURLString()));
    final NodePermissioningController nodePermissioningController =
        new NodePermissioningController(
            Optional.empty(), Collections.singletonList(localWhitelistController));

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
                localBlacklist,
                new NoOpMetricsSystem(),
                Optional.of(localWhitelistController),
                Optional.of(nodePermissioningController),
                blockchain);
        final P2PNetwork remoteNetwork =
            new NettyP2PNetwork(
                vertx,
                remoteKp,
                NetworkingConfiguration.create()
                    .setSupportedProtocols(subprotocol)
                    .setRlpx(RlpxConfiguration.create().setBindPort(0))
                    .setDiscovery(noDiscovery),
                singletonList(cap),
                remoteBlacklist,
                new NoOpMetricsSystem(),
                Optional.empty(),
                Optional.empty())) {

      localNetwork.start();
      remoteNetwork.start();

      final EnodeURL localEnode = localNetwork.getLocalEnode().get();
      final BytesValue localId = localEnode.getNodeId();
      final int localPort = localEnode.getListeningPort();

      final Peer localPeer =
          new DefaultPeer(
              localId,
              new Endpoint(
                  InetAddress.getLoopbackAddress().getHostAddress(),
                  localPort,
                  OptionalInt.of(localPort)));

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
  public void addingMaintainedNetworkPeerStartsConnection() {
    final NettyP2PNetwork network = mockNettyP2PNetwork();
    final Peer peer = mockPeer();

    assertThat(network.addMaintainConnectionPeer(peer)).isTrue();

    assertThat(network.peerMaintainConnectionList).contains(peer);
    verify(network, times(1)).connect(peer);
  }

  @Test
  public void addingRepeatMaintainedPeersReturnsFalse() {
    final NettyP2PNetwork network = nettyP2PNetwork();
    final Peer peer = mockPeer();
    assertThat(network.addMaintainConnectionPeer(peer)).isTrue();
    assertThat(network.addMaintainConnectionPeer(peer)).isFalse();
  }

  @Test
  public void checkMaintainedConnectionPeersTriesToConnect() {
    final NettyP2PNetwork network = mockNettyP2PNetwork();
    final Peer peer = mockPeer();
    network.peerMaintainConnectionList.add(peer);

    network.checkMaintainedConnectionPeers();
    verify(network, times(1)).connect(peer);
  }

  @Test
  public void checkMaintainedConnectionPeersDoesntReconnectPendingPeers() {
    final NettyP2PNetwork network = mockNettyP2PNetwork();
    final Peer peer = mockPeer();

    network.pendingConnections.put(peer, new CompletableFuture<>());

    network.checkMaintainedConnectionPeers();
    verify(network, times(0)).connect(peer);
  }

  @Test
  public void checkMaintainedConnectionPeersDoesntReconnectConnectedPeers() {
    final NettyP2PNetwork network = spy(nettyP2PNetwork());
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
    final NettyP2PNetwork nettyP2PNetwork = nettyP2PNetwork();
    final Peer peer = mockPeer();
    final PeerConnection peerConnection = mockPeerConnection();

    nettyP2PNetwork.connect(peer).complete(peerConnection);
    nettyP2PNetwork.stop();

    verify(peerConnection).disconnect(eq(DisconnectReason.CLIENT_QUITTING));
  }

  @Test
  public void shouldntAttemptNewConnectionToPendingPeer() {
    final NettyP2PNetwork nettyP2PNetwork = nettyP2PNetwork();
    final Peer peer = mockPeer();

    final CompletableFuture<PeerConnection> connectingFuture = nettyP2PNetwork.connect(peer);
    assertThat(nettyP2PNetwork.connect(peer)).isEqualTo(connectingFuture);
  }

  @Test
  public void whenStartingNetworkWithNodePermissioningShouldSubscribeToBlockAddedEvents() {
    final NettyP2PNetwork nettyP2PNetwork = nettyP2PNetwork();

    nettyP2PNetwork.start();

    verify(blockchain).observeBlockAdded(any());
  }

  @Test
  public void whenStartingNetworkWithNodePermissioningWithoutBlockchainShouldThrowIllegalState() {
    blockchain = null;
    final NettyP2PNetwork nettyP2PNetwork = nettyP2PNetwork();

    final Throwable throwable = catchThrowable(nettyP2PNetwork::start);
    assertThat(throwable)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "NettyP2PNetwork permissioning needs to listen to BlockAddedEvents. Blockchain can't be null.");
  }

  @Test
  public void whenStoppingNetworkWithNodePermissioningShouldUnsubscribeBlockAddedEvents() {
    final NettyP2PNetwork nettyP2PNetwork = nettyP2PNetwork();

    nettyP2PNetwork.start();
    nettyP2PNetwork.stop();

    verify(blockchain).removeObserver(eq(1L));
  }

  @Test
  public void onBlockAddedShouldCheckPermissionsForAllPeers() {
    final BlockAddedEvent blockAddedEvent = blockAddedEvent();
    final NettyP2PNetwork nettyP2PNetwork = nettyP2PNetwork();
    final Peer remotePeer1 = mockPeer("127.0.0.2", 30302);
    final Peer remotePeer2 = mockPeer("127.0.0.3", 30303);

    final PeerConnection peerConnection1 = mockPeerConnection(remotePeer1);
    final PeerConnection peerConnection2 = mockPeerConnection(remotePeer2);

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
    final NettyP2PNetwork nettyP2PNetwork = nettyP2PNetwork();

    final Peer permittedPeer = mockPeer("127.0.0.2", 30302);
    final Peer notPermittedPeer = mockPeer("127.0.0.3", 30303);

    final PeerConnection permittedPeerConnection = mockPeerConnection(permittedPeer);
    final PeerConnection notPermittedPeerConnection = mockPeerConnection(notPermittedPeer);

    final EnodeURL permittedEnodeURL = EnodeURL.fromString(permittedPeer.getEnodeURLString());
    final EnodeURL notPermittedEnodeURL = EnodeURL.fromString(notPermittedPeer.getEnodeURLString());

    nettyP2PNetwork.start();
    nettyP2PNetwork.connect(permittedPeer).complete(permittedPeerConnection);
    nettyP2PNetwork.connect(notPermittedPeer).complete(notPermittedPeerConnection);

    reset(nodePermissioningController);

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

  @Test
  public void removePeerReturnsTrueIfNodeWasInMaintaineConnectionsAndDisconnectsIfInPending() {
    final NettyP2PNetwork nettyP2PNetwork = nettyP2PNetwork();
    nettyP2PNetwork.start();

    final Peer remotePeer = mockPeer("127.0.0.2", 30302);
    final PeerConnection peerConnection = mockPeerConnection(remotePeer);

    nettyP2PNetwork.addMaintainConnectionPeer(remotePeer);
    assertThat(nettyP2PNetwork.peerMaintainConnectionList.contains(remotePeer)).isTrue();
    assertThat(nettyP2PNetwork.pendingConnections.containsKey(remotePeer)).isTrue();
    assertThat(nettyP2PNetwork.removeMaintainedConnectionPeer(remotePeer)).isTrue();
    assertThat(nettyP2PNetwork.peerMaintainConnectionList.contains(remotePeer)).isFalse();

    // Note: The pendingConnection future is not removed.
    assertThat(nettyP2PNetwork.pendingConnections.containsKey(remotePeer)).isTrue();

    // Complete the connection, and ensure "disconnect is automatically called.
    nettyP2PNetwork.pendingConnections.get(remotePeer).complete(peerConnection);
    verify(peerConnection).disconnect(DisconnectReason.REQUESTED);
  }

  @Test
  public void removePeerReturnsFalseIfNotInMaintainedListButDisconnectsPeer() {
    final NettyP2PNetwork nettyP2PNetwork = nettyP2PNetwork();
    nettyP2PNetwork.start();

    final Peer remotePeer = mockPeer("127.0.0.2", 30302);
    final PeerConnection peerConnection = mockPeerConnection(remotePeer);

    CompletableFuture<PeerConnection> future = nettyP2PNetwork.connect(remotePeer);

    assertThat(nettyP2PNetwork.peerMaintainConnectionList.contains(remotePeer)).isFalse();
    assertThat(nettyP2PNetwork.pendingConnections.containsKey(remotePeer)).isTrue();
    future.complete(peerConnection);
    assertThat(nettyP2PNetwork.pendingConnections.containsKey(remotePeer)).isFalse();

    assertThat(nettyP2PNetwork.removeMaintainedConnectionPeer(remotePeer)).isFalse();
    assertThat(nettyP2PNetwork.peerMaintainConnectionList.contains(remotePeer)).isFalse();

    verify(peerConnection).disconnect(DisconnectReason.REQUESTED);
  }

  @Test
  public void beforeStartingNetworkEnodeURLShouldNotBePresent() {
    final NettyP2PNetwork nettyP2PNetwork = mockNettyP2PNetwork();

    assertThat(nettyP2PNetwork.getLocalEnode()).isNotPresent();
  }

  @Test
  public void afterStartingNetworkEnodeURLShouldBePresent() {
    final NettyP2PNetwork nettyP2PNetwork = mockNettyP2PNetwork();
    nettyP2PNetwork.start();

    assertThat(nettyP2PNetwork.getLocalEnode()).isPresent();
  }

  @Test
  public void handlePeerBondedEvent_forPeerWithNoTcpPort() {
    final NettyP2PNetwork network = mockNettyP2PNetwork();
    DiscoveryPeer peer =
        new DiscoveryPeer(generatePeerId(0), "127.0.0.1", 999, OptionalInt.empty());
    PeerBondedEvent peerBondedEvent = new PeerBondedEvent(peer, System.currentTimeMillis());

    network.handlePeerBondedEvent().accept(peerBondedEvent);
    verify(network, times(1)).connect(peer);
  }

  @Test
  public void attemptPeerConnections_connectsToValidPeer() {
    final int maxPeers = 5;
    final NettyP2PNetwork network =
        mockNettyP2PNetwork(() -> RlpxConfiguration.create().setMaxPeers(maxPeers));

    doReturn(2).when(network).connectionCount();
    DiscoveryPeer peer = createDiscoveryPeer(0);
    peer.setStatus(PeerDiscoveryStatus.BONDED);

    doReturn(Stream.of(peer)).when(network).getDiscoveryPeers();
    ArgumentCaptor<DiscoveryPeer> peerCapture = ArgumentCaptor.forClass(DiscoveryPeer.class);
    doReturn(CompletableFuture.completedFuture(mock(PeerConnection.class)))
        .when(network)
        .connect(peerCapture.capture());

    network.attemptPeerConnections();
    verify(network, times(1)).connect(any());
    assertThat(peerCapture.getValue()).isEqualTo(peer);
  }

  @Test
  public void attemptPeerConnections_ignoresUnbondedPeer() {
    final int maxPeers = 5;
    final NettyP2PNetwork network =
        mockNettyP2PNetwork(() -> RlpxConfiguration.create().setMaxPeers(maxPeers));

    doReturn(2).when(network).connectionCount();
    DiscoveryPeer peer = createDiscoveryPeer(0);
    peer.setStatus(PeerDiscoveryStatus.KNOWN);

    doReturn(Stream.of(peer)).when(network).getDiscoveryPeers();

    network.attemptPeerConnections();
    verify(network, times(0)).connect(any());
  }

  @Test
  public void attemptPeerConnections_ignoresConnectingPeer() {
    final int maxPeers = 5;
    final NettyP2PNetwork network =
        mockNettyP2PNetwork(() -> RlpxConfiguration.create().setMaxPeers(maxPeers));

    doReturn(2).when(network).connectionCount();
    DiscoveryPeer peer = createDiscoveryPeer(0);
    peer.setStatus(PeerDiscoveryStatus.BONDED);

    doReturn(true).when(network).isConnecting(peer);
    doReturn(Stream.of(peer)).when(network).getDiscoveryPeers();

    network.attemptPeerConnections();
    verify(network, times(0)).connect(any());
  }

  @Test
  public void attemptPeerConnections_ignoresConnectedPeer() {
    final int maxPeers = 5;
    final NettyP2PNetwork network =
        mockNettyP2PNetwork(() -> RlpxConfiguration.create().setMaxPeers(maxPeers));

    doReturn(2).when(network).connectionCount();
    DiscoveryPeer peer = createDiscoveryPeer(0);
    peer.setStatus(PeerDiscoveryStatus.BONDED);

    doReturn(true).when(network).isConnected(peer);
    doReturn(Stream.of(peer)).when(network).getDiscoveryPeers();

    network.attemptPeerConnections();
    verify(network, times(0)).connect(any());
  }

  @Test
  public void attemptPeerConnections_withSlotsAvailable() {
    final int maxPeers = 5;
    final NettyP2PNetwork network =
        mockNettyP2PNetwork(() -> RlpxConfiguration.create().setMaxPeers(maxPeers));

    doReturn(2).when(network).connectionCount();
    List<DiscoveryPeer> peers =
        Stream.iterate(1, n -> n + 1)
            .limit(10)
            .map(
                (seed) -> {
                  DiscoveryPeer peer = createDiscoveryPeer(seed);
                  peer.setStatus(PeerDiscoveryStatus.BONDED);
                  return peer;
                })
            .collect(Collectors.toList());

    doReturn(peers.stream()).when(network).getDiscoveryPeers();
    ArgumentCaptor<DiscoveryPeer> peerCapture = ArgumentCaptor.forClass(DiscoveryPeer.class);
    doReturn(CompletableFuture.completedFuture(mock(PeerConnection.class)))
        .when(network)
        .connect(peerCapture.capture());

    network.attemptPeerConnections();
    verify(network, times(3)).connect(any());
    assertThat(peers.containsAll(peerCapture.getAllValues())).isTrue();
  }

  @Test
  public void attemptPeerConnections_withNoSlotsAvailable() {
    final int maxPeers = 5;
    final NettyP2PNetwork network =
        mockNettyP2PNetwork(() -> RlpxConfiguration.create().setMaxPeers(maxPeers));

    doReturn(maxPeers).when(network).connectionCount();
    List<DiscoveryPeer> peers =
        Stream.iterate(1, n -> n + 1)
            .limit(10)
            .map(
                (seed) -> {
                  DiscoveryPeer peer = createDiscoveryPeer(seed);
                  peer.setStatus(PeerDiscoveryStatus.BONDED);
                  return peer;
                })
            .collect(Collectors.toList());

    lenient().doReturn(peers.stream()).when(network).getDiscoveryPeers();

    network.attemptPeerConnections();
    verify(network, times(0)).connect(any());
  }

  private DiscoveryPeer createDiscoveryPeer(final int seed) {
    return new DiscoveryPeer(generatePeerId(seed), "127.0.0.1", 999, OptionalInt.empty());
  }

  private BytesValue generatePeerId(final int seed) {
    BlockDataGenerator gen = new BlockDataGenerator(seed);
    return gen.bytesValue(DefaultPeer.PEER_ID_SIZE);
  }

  private BlockAddedEvent blockAddedEvent() {
    return mock(BlockAddedEvent.class);
  }

  private PeerConnection mockPeerConnection(final BytesValue id) {
    final PeerInfo peerInfo = mock(PeerInfo.class);
    when(peerInfo.getNodeId()).thenReturn(id);
    final PeerConnection peerConnection = mock(PeerConnection.class);
    when(peerConnection.getPeerInfo()).thenReturn(peerInfo);
    return peerConnection;
  }

  private PeerConnection mockPeerConnection() {
    return mockPeerConnection(BytesValue.fromHexString("0x00"));
  }

  private PeerConnection mockPeerConnection(final Peer remotePeer) {
    final EnodeURL remoteEnode = remotePeer.getEnodeURL();
    final PeerInfo peerInfo =
        new PeerInfo(
            5,
            "test",
            Arrays.asList(Capability.create("eth", 63)),
            remoteEnode.getListeningPort(),
            remoteEnode.getNodeId());

    final PeerConnection peerConnection = mock(PeerConnection.class);
    when(peerConnection.getRemoteEnode()).thenReturn(remoteEnode);
    when(peerConnection.getPeerInfo()).thenReturn(peerInfo);

    return peerConnection;
  }

  private NettyP2PNetwork mockNettyP2PNetwork() {
    return mockNettyP2PNetwork(RlpxConfiguration::create);
  }

  private NettyP2PNetwork mockNettyP2PNetwork(final Supplier<RlpxConfiguration> rlpxConfig) {
    NettyP2PNetwork network = spy(nettyP2PNetwork(rlpxConfig));
    lenient().doReturn(new CompletableFuture<>()).when(network).connect(any());
    return network;
  }

  private NettyP2PNetwork nettyP2PNetwork() {
    return nettyP2PNetwork(RlpxConfiguration::create);
  }

  private NettyP2PNetwork nettyP2PNetwork(final Supplier<RlpxConfiguration> rlpxConfig) {
    final DiscoveryConfiguration noDiscovery = DiscoveryConfiguration.create().setActive(false);
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    final Capability cap = Capability.create("eth", 63);
    final NetworkingConfiguration networkingConfiguration =
        NetworkingConfiguration.create()
            .setDiscovery(noDiscovery)
            .setSupportedProtocols(subProtocol())
            .setRlpx(rlpxConfig.get().setBindPort(0));

    lenient().when(nodePermissioningController.isPermitted(any(), any())).thenReturn(true);

    return new NettyP2PNetwork(
        mock(Vertx.class),
        keyPair,
        networkingConfiguration,
        singletonList(cap),
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
    final Endpoint endpoint = new Endpoint(host, port, OptionalInt.of(port));
    final String enodeURL =
        String.format(
            "enode://%s@%s:%d?discport=%d",
            id.toString().substring(2),
            endpoint.getHost(),
            endpoint.getUdpPort(),
            endpoint.getTcpPort().getAsInt());

    return DefaultPeer.fromURI(enodeURL);
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
