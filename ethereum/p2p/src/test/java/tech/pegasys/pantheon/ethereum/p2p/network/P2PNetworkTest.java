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
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.RlpxConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.network.exceptions.IncompatiblePeerException;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
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
import java.util.Optional;
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

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class P2PNetworkTest {

  @Mock private Blockchain blockchain;

  private ArgumentCaptor<BlockAddedObserver> observerCaptor =
      ArgumentCaptor.forClass(BlockAddedObserver.class);

  private final Vertx vertx = Vertx.vertx();
  private final NetworkingConfiguration config =
      NetworkingConfiguration.create()
          .setDiscovery(DiscoveryConfiguration.create().setActive(false))
          .setSupportedProtocols(subProtocol())
          .setRlpx(RlpxConfiguration.create().setBindPort(0));

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
    final SECP256K1.KeyPair listenKp = SECP256K1.KeyPair.generate();
    try (final P2PNetwork listener = builder().keyPair(listenKp).build();
        final P2PNetwork connector = builder().build()) {

      listener.start();
      connector.start();
      final EnodeURL listenerEnode = listener.getLocalEnode().get();
      final BytesValue listenId = listenerEnode.getNodeId();
      final int listenPort = listenerEnode.getListeningPort();

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
      final int listenPort = listenerEnode.getListeningPort();

      assertThat(
              connector
                  .connect(createPeer(listenId, listenPort))
                  .get(30L, TimeUnit.SECONDS)
                  .getPeerInfo()
                  .getNodeId())
          .isEqualTo(listenId);
      final CompletableFuture<PeerConnection> secondConnectionFuture =
          connector.connect(createPeer(listenId, listenPort));
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
    final SECP256K1.KeyPair listenKp = SECP256K1.KeyPair.generate();
    final int maxPeers = 1;
    final NetworkingConfiguration listenerConfig =
        NetworkingConfiguration.create()
            .setDiscovery(DiscoveryConfiguration.create().setActive(false))
            .setSupportedProtocols(subProtocol())
            .setRlpx(RlpxConfiguration.create().setBindPort(0).setMaxPeers(maxPeers));
    try (final P2PNetwork listener = builder().keyPair(listenKp).config(listenerConfig).build();
        final P2PNetwork connector1 = builder().build();
        final P2PNetwork connector2 = builder().build()) {

      // Setup listener and first connection
      listener.start();
      connector1.start();
      final EnodeURL listenerEnode = listener.getLocalEnode().get();
      final BytesValue listenId = listenerEnode.getNodeId();
      final int listenPort = listenerEnode.getListeningPort();

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
      final int listenPort = listenerEnode.getListeningPort();

      final Peer listenerPeer = createPeer(listenId, listenPort);
      final CompletableFuture<PeerConnection> connectFuture = connector.connect(listenerPeer);
      assertThatThrownBy(connectFuture::get).hasCauseInstanceOf(IncompatiblePeerException.class);
    }
  }

  @Test
  public void rejectIncomingConnectionFromBlacklistedPeer() throws Exception {
    final PeerBlacklist localBlacklist = new PeerBlacklist();
    final PeerBlacklist remoteBlacklist = new PeerBlacklist();

    try (final P2PNetwork localNetwork = builder().peerBlacklist(localBlacklist).build();
        final P2PNetwork remoteNetwork = builder().peerBlacklist(remoteBlacklist).build()) {

      localNetwork.start();
      remoteNetwork.start();

      final EnodeURL localEnode = localNetwork.getLocalEnode().get();
      final BytesValue localId = localEnode.getNodeId();
      final int localPort = localEnode.getListeningPort();

      final EnodeURL remoteEnode = remoteNetwork.getLocalEnode().get();
      final BytesValue remoteId = remoteEnode.getNodeId();
      final int remotePort = remoteEnode.getListeningPort();

      final Peer localPeer = createPeer(localId, localPort);
      final Peer remotePeer = createPeer(remoteId, remotePort);

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
    final LocalPermissioningConfiguration config = LocalPermissioningConfiguration.createDefault();
    final Path tempFile = Files.createTempFile("test", "test");
    tempFile.toFile().deleteOnExit();
    config.setNodePermissioningConfigFilePath(tempFile.toAbsolutePath().toString());

    final NodeLocalConfigPermissioningController localWhitelistController =
        new NodeLocalConfigPermissioningController(
            config, Collections.emptyList(), selfEnode.getNodeId());
    // turn on whitelisting by adding a different node NOT remote node
    localWhitelistController.addNode(
        EnodeURL.builder().ipAddress("127.0.0.1").nodeId(Peer.randomId()).build());
    final NodePermissioningController nodePermissioningController =
        new NodePermissioningController(
            Optional.empty(), Collections.singletonList(localWhitelistController));

    try (final P2PNetwork localNetwork =
            builder()
                .nodePermissioningController(nodePermissioningController)
                .nodeLocalConfigPermissioningController(localWhitelistController)
                .blockchain(blockchain)
                .build();
        final P2PNetwork remoteNetwork = builder().build()) {

      localNetwork.start();
      remoteNetwork.start();

      final EnodeURL localEnode = localNetwork.getLocalEnode().get();
      final BytesValue localId = localEnode.getNodeId();
      final int localPort = localEnode.getListeningPort();

      final Peer localPeer = createPeer(localId, localPort);

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

  private Peer createPeer(final BytesValue nodeId, final int listenPort) {
    return DefaultPeer.fromEnodeURL(
        EnodeURL.builder()
            .ipAddress(InetAddress.getLoopbackAddress().getHostAddress())
            .nodeId(nodeId)
            .listeningPort(listenPort)
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
            && enodeURL.getListeningPort() == argument.getListeningPort();
      }
    }
  }

  private DefaultP2PNetwork.Builder builder() {
    return DefaultP2PNetwork.builder()
        .vertx(vertx)
        .config(config)
        .keyPair(KeyPair.generate())
        .peerBlacklist(new PeerBlacklist())
        .metricsSystem(new NoOpMetricsSystem())
        .supportedCapabilities(Arrays.asList(Capability.create("eth", 63)));
  }
}
