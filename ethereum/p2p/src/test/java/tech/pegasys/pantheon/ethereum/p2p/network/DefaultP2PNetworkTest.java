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
package tech.pegasys.pantheon.ethereum.p2p.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.assertThatThrownBy;
import static org.assertj.core.api.Java6Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.RlpxConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.discovery.Endpoint;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryEvent.PeerBondedEvent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryStatus;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.MaintainedPeers;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.permissions.PeerPermissions;
import tech.pegasys.pantheon.ethereum.p2p.permissions.PeerPermissions.Action;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.SubProtocol;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.permissioning.node.NodePermissioningController;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link DefaultP2PNetwork}. */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public final class DefaultP2PNetworkTest {

  @Mock private NodePermissioningController nodePermissioningController;
  private TestPeerPermissions peerPermissions = spy(new TestPeerPermissions());

  @Mock private Blockchain blockchain;

  private final ArgumentCaptor<BlockAddedObserver> observerCaptor =
      ArgumentCaptor.forClass(BlockAddedObserver.class);

  private final Vertx vertx = Vertx.vertx();
  private final NetworkingConfiguration config =
      NetworkingConfiguration.create()
          .setDiscovery(DiscoveryConfiguration.create().setActive(false))
          .setSupportedProtocols(subProtocol())
          .setRlpx(RlpxConfiguration.create().setBindPort(0));

  @Before
  public void before() {
    when(blockchain.observeBlockAdded(observerCaptor.capture())).thenReturn(1L);
    // Make permissions lenient by default
    lenient().when(nodePermissioningController.isPermitted(any(), any())).thenReturn(true);
  }

  @After
  public void closeVertx() {
    vertx.close();
  }

  @Test
  public void addingMaintainedConnectionPeer_startsConnection() {
    final MaintainedPeers maintainedPeers = new MaintainedPeers();
    final DefaultP2PNetwork network =
        spy((DefaultP2PNetwork) builder().maintainedPeers(maintainedPeers).build());
    network.start();
    final Peer peer = mockPeer();

    assertThat(network.addMaintainConnectionPeer(peer)).isTrue();

    assertThat(maintainedPeers.contains(peer)).isTrue();
    verify(network, times(1)).initiateOutboundConnection(eq(peer), any());
  }

  @Test
  public void addingMaintainedConnectionPeer_forDisallowedPeer() {
    final MaintainedPeers maintainedPeers = new MaintainedPeers();
    final DefaultP2PNetwork network =
        spy((DefaultP2PNetwork) builder().maintainedPeers(maintainedPeers).build());
    network.start();

    final Peer localNode = DefaultPeer.fromEnodeURL(network.getLocalEnode().get());
    final Peer peer = mockPeer();
    doReturn(false)
        .when(peerPermissions)
        .isPermitted(eq(localNode), eq(peer), eq(Action.RLPX_ALLOW_NEW_OUTBOUND_CONNECTION));

    assertThat(network.addMaintainConnectionPeer(peer)).isTrue();

    // Add peer but do not connect
    assertThat(maintainedPeers.contains(peer)).isTrue();
    verify(network, times(0)).initiateOutboundConnection(eq(peer), any());
  }

  @Test
  public void addMaintainConnectionPeer_beforeStartingNetwork() {
    final MaintainedPeers maintainedPeers = new MaintainedPeers();
    final DefaultP2PNetwork network =
        spy((DefaultP2PNetwork) builder().maintainedPeers(maintainedPeers).build());
    final Peer peer = mockPeer();

    assertThat(network.addMaintainConnectionPeer(peer)).isTrue();

    assertThat(maintainedPeers.contains(peer)).isTrue();
    verify(network, never()).connect(peer);
  }

  @Test
  public void addMaintainConnectionPeer_withNonListeningEnode() {
    final DefaultP2PNetwork network = mockNetwork();
    network.start();
    final Peer peer =
        DefaultPeer.fromEnodeURL(
            EnodeURL.builder()
                .nodeId(Peer.randomId())
                .ipAddress("127.0.0.1")
                .useDefaultPorts()
                .disableListening()
                .build());

    assertThatThrownBy(() -> network.addMaintainConnectionPeer(peer))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Enode url must contain a non-zero listening port");

    verify(network, never()).connect(peer);
  }

  @Test
  public void addingMaintainedConnectionPeer_repeatInvocationReturnsFalse() {
    final P2PNetwork network = network();
    network.start();
    final Peer peer = mockPeer();
    assertThat(network.addMaintainConnectionPeer(peer)).isTrue();
    assertThat(network.addMaintainConnectionPeer(peer)).isFalse();
  }

  @Test
  public void checkMaintainedConnectionPeersTriesToConnect() {
    final MaintainedPeers maintainedPeers = new MaintainedPeers();
    final DefaultP2PNetwork network =
        spy((DefaultP2PNetwork) builder().maintainedPeers(maintainedPeers).build());
    final Peer peer = mockPeer();
    maintainedPeers.add(peer);

    network.start();

    verify(network, times(0)).connect(peer);

    network.checkMaintainedConnectionPeers();
    verify(network, times(1)).initiateOutboundConnection(eq(peer), any());
  }

  @Test
  public void checkMaintainedConnectionPeers_doesNotConnectToDisallowedPeer() {
    final MaintainedPeers maintainedPeers = new MaintainedPeers();
    final DefaultP2PNetwork network =
        spy((DefaultP2PNetwork) builder().maintainedPeers(maintainedPeers).build());
    final Peer peer = mockPeer();
    maintainedPeers.add(peer);

    network.start();

    // Add peer that is not permitted
    final Peer localNode = DefaultPeer.fromEnodeURL(network.getLocalEnode().get());
    doReturn(false)
        .when(peerPermissions)
        .isPermitted(eq(localNode), eq(peer), eq(Action.RLPX_ALLOW_NEW_OUTBOUND_CONNECTION));

    network.checkMaintainedConnectionPeers();
    verify(network, times(0)).initiateOutboundConnection(eq(peer), any());
  }

  @Test
  public void checkMaintainedConnectionPeersDoesntReconnectPendingPeers() {
    final DefaultP2PNetwork network = mockNetwork();
    final Peer peer = mockPeer();

    network.pendingConnections.put(peer, new CompletableFuture<>());

    network.checkMaintainedConnectionPeers();
    verify(network, times(0)).connect(peer);
  }

  @Test
  public void checkMaintainedConnectionPeersDoesntReconnectConnectedPeers() {
    final DefaultP2PNetwork network = spy(network());
    network.start();
    final Peer peer = mockPeer();

    // Connect to Peer
    verify(network, never()).connect(peer);
    network.connect(peer);
    verify(network, times(1)).connect(peer);

    // Add peer to maintained list
    assertThat(network.addMaintainConnectionPeer(peer)).isTrue();
    verify(network, times(1)).initiateOutboundConnection(eq(peer), any());

    // Check maintained connections
    network.checkMaintainedConnectionPeers();
    verify(network, times(1)).initiateOutboundConnection(eq(peer), any());
  }

  @Test
  public void shouldSendClientQuittingWhenNetworkStops() {
    final P2PNetwork network = network();
    final Peer peer = mockPeer();
    final PeerConnection peerConnection = mockPeerConnection();

    network.start();
    network.connect(peer).complete(peerConnection);
    network.stop();

    verify(peerConnection).disconnect(eq(DisconnectReason.CLIENT_QUITTING));
  }

  @Test
  public void shouldntAttemptNewConnectionToPendingPeer() {
    final P2PNetwork network = network();
    network.start();
    final Peer peer = mockPeer();

    final CompletableFuture<PeerConnection> connectingFuture = network.connect(peer);
    assertThat(network.connect(peer)).isEqualTo(connectingFuture);
  }

  @Test
  public void whenStartingNetworkWithNodePermissioningShouldSubscribeToBlockAddedEvents() {
    final P2PNetwork network = network();

    network.start();

    verify(blockchain).observeBlockAdded(any());
  }

  @Test
  public void whenBuildingNetworkWithNodePermissioningWithoutBlockchainShouldThrowIllegalState() {
    blockchain = null;
    final Throwable throwable = catchThrowable(this::network);
    assertThat(throwable)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Network permissioning needs to listen to BlockAddedEvents. Blockchain can't be null.");
  }

  @Test
  public void stop_removesListeners() {
    final P2PNetwork network = network();

    network.start();
    verify(blockchain, never()).removeObserver(anyLong());
    verify(nodePermissioningController, never()).unsubscribeFromUpdates(anyLong());

    network.stop();
    network.awaitStop();

    verify(blockchain, times(1)).removeObserver(anyLong());
    verify(nodePermissioningController, times(1)).unsubscribeFromUpdates(anyLong());
  }

  @Test
  public void onBlockAddedShouldCheckPermissionsForAllPeers() {
    final BlockAddedEvent blockAddedEvent = blockAddedEvent();
    final P2PNetwork network = network();
    final Peer remotePeer1 = mockPeer("127.0.0.2", 30302);
    final Peer remotePeer2 = mockPeer("127.0.0.3", 30303);

    final PeerConnection peerConnection1 = mockPeerConnection(remotePeer1);
    final PeerConnection peerConnection2 = mockPeerConnection(remotePeer2);

    network.start();
    network.connect(remotePeer1).complete(peerConnection1);
    network.connect(remotePeer2).complete(peerConnection2);
    // Permissions are checked on connection
    verify(nodePermissioningController, times(2)).isPermitted(any(), any());

    final BlockAddedObserver blockAddedObserver = observerCaptor.getValue();
    blockAddedObserver.onBlockAdded(blockAddedEvent, blockchain);

    // Permissions should be checked again after block is added
    verify(nodePermissioningController, times(4)).isPermitted(any(), any());
  }

  @Test
  public void onBlockAddedAndPeerNotPermittedShouldDisconnect() {
    final BlockAddedEvent blockAddedEvent = blockAddedEvent();
    final P2PNetwork network = network();

    final Peer permittedPeer = mockPeer("127.0.0.2", 30302);
    final Peer notPermittedPeer = mockPeer("127.0.0.3", 30303);

    final PeerConnection permittedPeerConnection = mockPeerConnection(permittedPeer);
    final PeerConnection notPermittedPeerConnection = mockPeerConnection(notPermittedPeer);

    final EnodeURL permittedEnodeURL = EnodeURL.fromString(permittedPeer.getEnodeURLString());
    final EnodeURL notPermittedEnodeURL = EnodeURL.fromString(notPermittedPeer.getEnodeURLString());

    network.start();
    network.connect(permittedPeer).complete(permittedPeerConnection);
    network.connect(notPermittedPeer).complete(notPermittedPeerConnection);

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
  public void onPermissionsUpdate_permissionsRestrictedWithNoListOfPeers() {
    final P2PNetwork network = network();

    final Peer permittedPeer = mockPeer("127.0.0.2", 30302);
    final Peer notPermittedPeer = mockPeer("127.0.0.3", 30303);
    final PeerConnection permittedPeerConnection = mockPeerConnection(permittedPeer);
    final PeerConnection notPermittedPeerConnection = mockPeerConnection(notPermittedPeer);

    network.start();
    final Peer localNode = DefaultPeer.fromEnodeURL(network.getLocalEnode().get());

    network.connect(permittedPeer).complete(permittedPeerConnection);
    network.connect(notPermittedPeer).complete(notPermittedPeerConnection);
    verify(peerPermissions, times(2)).isPermitted(any(), any(), any());

    doReturn(false)
        .when(peerPermissions)
        .isPermitted(eq(localNode), eq(notPermittedPeer), eq(Action.RLPX_ALLOW_ONGOING_CONNECTION));
    peerPermissions.testDispatchUpdate(true, Optional.empty());

    verify(peerPermissions, times(4)).isPermitted(any(), any(), any());
    verify(notPermittedPeerConnection).disconnect(eq(DisconnectReason.REQUESTED));
    verify(permittedPeerConnection, never()).disconnect(any());
  }

  @Test
  public void onPermissionsUpdate_permissionsRestrictedWithListOfPeers() {
    final P2PNetwork network = network();

    final Peer permittedPeer = mockPeer("127.0.0.2", 30302);
    final Peer notPermittedPeer = mockPeer("127.0.0.3", 30303);
    final PeerConnection permittedPeerConnection = mockPeerConnection(permittedPeer);
    final PeerConnection notPermittedPeerConnection = mockPeerConnection(notPermittedPeer);

    network.start();
    final Peer localNode = DefaultPeer.fromEnodeURL(network.getLocalEnode().get());

    network.connect(permittedPeer).complete(permittedPeerConnection);
    network.connect(notPermittedPeer).complete(notPermittedPeerConnection);
    verify(peerPermissions, times(2)).isPermitted(any(), any(), any());

    doReturn(false)
        .when(peerPermissions)
        .isPermitted(eq(localNode), eq(notPermittedPeer), eq(Action.RLPX_ALLOW_ONGOING_CONNECTION));
    peerPermissions.testDispatchUpdate(
        true, Optional.of(Collections.singletonList(notPermittedPeer)));

    verify(peerPermissions, times(3)).isPermitted(any(), any(), any());
    verify(notPermittedPeerConnection).disconnect(eq(DisconnectReason.REQUESTED));
    verify(permittedPeerConnection, never()).disconnect(any());
  }

  @Test
  public void onPermissionsUpdate_permissionsRelaxedWithNoListOfPeers() {
    final P2PNetwork network = network();

    final Peer permittedPeer = mockPeer("127.0.0.2", 30302);
    final Peer notPermittedPeer = mockPeer("127.0.0.3", 30303);
    final PeerConnection permittedPeerConnection = mockPeerConnection(permittedPeer);
    final PeerConnection notPermittedPeerConnection = mockPeerConnection(notPermittedPeer);

    network.start();

    network.connect(permittedPeer).complete(permittedPeerConnection);
    network.connect(notPermittedPeer).complete(notPermittedPeerConnection);
    verify(peerPermissions, times(2)).isPermitted(any(), any(), any());

    peerPermissions.testDispatchUpdate(false, Optional.empty());

    verify(peerPermissions, times(2)).isPermitted(any(), any(), any());
    verify(notPermittedPeerConnection, never()).disconnect(any());
    verify(permittedPeerConnection, never()).disconnect(any());
  }

  @Test
  public void onPermissionsUpdate_permissionsRelaxedWithListOfPeers() {
    final P2PNetwork network = network();

    final Peer permittedPeer = mockPeer("127.0.0.2", 30302);
    final Peer notPermittedPeer = mockPeer("127.0.0.3", 30303);
    final PeerConnection permittedPeerConnection = mockPeerConnection(permittedPeer);
    final PeerConnection notPermittedPeerConnection = mockPeerConnection(notPermittedPeer);

    network.start();

    network.connect(permittedPeer).complete(permittedPeerConnection);
    network.connect(notPermittedPeer).complete(notPermittedPeerConnection);
    verify(peerPermissions, times(2)).isPermitted(any(), any(), any());

    peerPermissions.testDispatchUpdate(
        false, Optional.of(Collections.singletonList(notPermittedPeer)));

    verify(peerPermissions, times(2)).isPermitted(any(), any(), any());
    verify(notPermittedPeerConnection, never()).disconnect(any());
    verify(permittedPeerConnection, never()).disconnect(any());
  }

  @Test
  public void removePeerReturnsTrueIfNodeWasInMaintainedConnectionsAndDisconnectsIfInPending() {
    final MaintainedPeers maintainedPeers = new MaintainedPeers();
    final DefaultP2PNetwork network =
        spy((DefaultP2PNetwork) builder().maintainedPeers(maintainedPeers).build());
    network.start();

    final Peer remotePeer = mockPeer("127.0.0.2", 30302);
    final PeerConnection peerConnection = mockPeerConnection(remotePeer);

    network.addMaintainConnectionPeer(remotePeer);
    assertThat(maintainedPeers.contains(remotePeer)).isTrue();
    assertThat(network.pendingConnections.containsKey(remotePeer)).isTrue();
    assertThat(network.removeMaintainedConnectionPeer(remotePeer)).isTrue();
    assertThat(maintainedPeers.contains(remotePeer)).isFalse();

    // Note: The pendingConnection future is not removed.
    assertThat(network.pendingConnections.containsKey(remotePeer)).isTrue();

    // Complete the connection, and ensure disconnect is automatically called.
    network.pendingConnections.get(remotePeer).complete(peerConnection);
    verify(peerConnection).disconnect(DisconnectReason.REQUESTED);
  }

  @Test
  public void removePeerReturnsFalseIfNotInMaintainedListButDisconnectsPeer() {
    final MaintainedPeers maintainedPeers = new MaintainedPeers();
    final DefaultP2PNetwork network =
        spy((DefaultP2PNetwork) builder().maintainedPeers(maintainedPeers).build());
    network.start();

    final Peer remotePeer = mockPeer("127.0.0.2", 30302);
    final PeerConnection peerConnection = mockPeerConnection(remotePeer);

    final CompletableFuture<PeerConnection> future = network.connect(remotePeer);

    assertThat(maintainedPeers.contains(remotePeer)).isFalse();
    assertThat(network.pendingConnections.containsKey(remotePeer)).isTrue();
    future.complete(peerConnection);
    assertThat(network.pendingConnections.containsKey(remotePeer)).isFalse();

    assertThat(network.removeMaintainedConnectionPeer(remotePeer)).isFalse();
    assertThat(maintainedPeers.contains(remotePeer)).isFalse();

    verify(peerConnection).disconnect(DisconnectReason.REQUESTED);
  }

  @Test
  public void beforeStartingNetworkEnodeURLShouldNotBePresent() {
    final P2PNetwork network = mockNetwork();

    assertThat(network.getLocalEnode()).isNotPresent();
  }

  @Test
  public void afterStartingNetworkEnodeURLShouldBePresent() {
    final P2PNetwork network = mockNetwork();
    network.start();

    assertThat(network.getLocalEnode()).isPresent();
  }

  @Test
  public void handlePeerBondedEvent_forPeerWithNoTcpPort() {
    final DefaultP2PNetwork network = mockNetwork();
    final DiscoveryPeer peer =
        DiscoveryPeer.fromIdAndEndpoint(
            Peer.randomId(), new Endpoint("127.0.0.1", 999, OptionalInt.empty()));
    final PeerBondedEvent peerBondedEvent = new PeerBondedEvent(peer, System.currentTimeMillis());

    network.handlePeerBondedEvent().accept(peerBondedEvent);
    verify(network, times(1)).connect(peer);
  }

  @Test
  public void attemptPeerConnections_connectsToValidPeer() {
    final int maxPeers = 5;
    final DefaultP2PNetwork network =
        mockNetwork(() -> RlpxConfiguration.create().setMaxPeers(maxPeers));
    network.start();

    doReturn(2).when(network).connectionCount();
    DiscoveryPeer peer = createDiscoveryPeer();
    peer.setStatus(PeerDiscoveryStatus.BONDED);

    doReturn(Stream.of(peer)).when(network).streamDiscoveredPeers();
    final ArgumentCaptor<DiscoveryPeer> peerCapture = ArgumentCaptor.forClass(DiscoveryPeer.class);
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
    final DefaultP2PNetwork network =
        mockNetwork(() -> RlpxConfiguration.create().setMaxPeers(maxPeers));
    network.start();

    doReturn(2).when(network).connectionCount();
    DiscoveryPeer peer = createDiscoveryPeer();
    peer.setStatus(PeerDiscoveryStatus.KNOWN);

    doReturn(Stream.of(peer)).when(network).streamDiscoveredPeers();

    network.attemptPeerConnections();
    verify(network, times(0)).connect(any());
  }

  @Test
  public void attemptPeerConnections_ignoresConnectingPeer() {
    final int maxPeers = 5;
    final DefaultP2PNetwork network =
        mockNetwork(() -> RlpxConfiguration.create().setMaxPeers(maxPeers));
    network.start();

    doReturn(2).when(network).connectionCount();
    DiscoveryPeer peer = createDiscoveryPeer();
    peer.setStatus(PeerDiscoveryStatus.BONDED);

    doReturn(true).when(network).isConnecting(peer);
    doReturn(Stream.of(peer)).when(network).streamDiscoveredPeers();

    network.attemptPeerConnections();
    verify(network, times(0)).connect(any());
  }

  @Test
  public void attemptPeerConnections_ignoresConnectedPeer() {
    final int maxPeers = 5;
    final DefaultP2PNetwork network =
        mockNetwork(() -> RlpxConfiguration.create().setMaxPeers(maxPeers));
    network.start();

    doReturn(2).when(network).connectionCount();
    DiscoveryPeer peer = createDiscoveryPeer();
    peer.setStatus(PeerDiscoveryStatus.BONDED);

    doReturn(true).when(network).isConnected(peer);
    doReturn(Stream.of(peer)).when(network).streamDiscoveredPeers();

    network.attemptPeerConnections();
    verify(network, times(0)).connect(any());
  }

  @Test
  public void attemptPeerConnections_withSlotsAvailable() {
    final int maxPeers = 5;
    final DefaultP2PNetwork network =
        mockNetwork(() -> RlpxConfiguration.create().setMaxPeers(maxPeers));
    network.start();

    doReturn(2).when(network).connectionCount();
    final List<DiscoveryPeer> peers =
        Stream.iterate(1, n -> n + 1)
            .limit(10)
            .map(
                (seed) -> {
                  DiscoveryPeer peer = createDiscoveryPeer();
                  peer.setStatus(PeerDiscoveryStatus.BONDED);
                  return peer;
                })
            .collect(Collectors.toList());

    final List<DiscoveryPeer> highestValuePeers = peers.subList(5, 8);
    // Mark as high value by lowering the lastAttemptedConnection value
    peers.forEach(p -> p.setLastAttemptedConnection(100));
    highestValuePeers.forEach(p -> p.setLastAttemptedConnection(1));

    doReturn(peers.stream()).when(network).streamDiscoveredPeers();
    final ArgumentCaptor<DiscoveryPeer> peerCapture = ArgumentCaptor.forClass(DiscoveryPeer.class);
    doReturn(CompletableFuture.completedFuture(mock(PeerConnection.class)))
        .when(network)
        .connect(peerCapture.capture());

    network.attemptPeerConnections();
    verify(network, times(3)).connect(any());
    assertThat(peers.containsAll(peerCapture.getAllValues())).isTrue();
    assertThat(peerCapture.getAllValues()).containsExactlyInAnyOrderElementsOf(highestValuePeers);
  }

  @Test
  public void attemptPeerConnections_withNonPermittedPeers() {
    final int maxPeers = 5;
    final DefaultP2PNetwork network =
        mockNetwork(() -> RlpxConfiguration.create().setMaxPeers(maxPeers));
    network.start();

    final Peer localNode = DefaultPeer.fromEnodeURL(network.getLocalEnode().get());

    doReturn(2).when(network).connectionCount();
    final List<DiscoveryPeer> peers =
        Stream.iterate(1, n -> n + 1)
            .limit(10)
            .map(
                (seed) -> {
                  DiscoveryPeer peer = createDiscoveryPeer();
                  peer.setStatus(PeerDiscoveryStatus.BONDED);
                  return peer;
                })
            .collect(Collectors.toList());

    // Prioritize peers
    final List<DiscoveryPeer> highestValuePeers = peers.subList(5, 8);
    peers.forEach(p -> p.setLastAttemptedConnection(100));
    highestValuePeers.forEach(p -> p.setLastAttemptedConnection(2));
    // Set up the highest value peer to lack permissions
    DiscoveryPeer highestValueNonPermittedPeer = peers.get(0);
    highestValueNonPermittedPeer.setLastAttemptedConnection(1);
    doReturn(false)
        .when(peerPermissions)
        .isPermitted(
            eq(localNode),
            eq(highestValueNonPermittedPeer),
            eq(Action.RLPX_ALLOW_NEW_OUTBOUND_CONNECTION));

    doReturn(peers.stream()).when(network).streamDiscoveredPeers();
    final ArgumentCaptor<DiscoveryPeer> peerCapture = ArgumentCaptor.forClass(DiscoveryPeer.class);
    doReturn(CompletableFuture.completedFuture(mock(PeerConnection.class)))
        .when(network)
        .connect(peerCapture.capture());

    network.attemptPeerConnections();
    verify(network, times(3)).connect(any());
    assertThat(peers.containsAll(peerCapture.getAllValues())).isTrue();
    assertThat(peerCapture.getAllValues()).containsExactlyInAnyOrderElementsOf(highestValuePeers);
  }

  @Test
  public void attemptPeerConnections_withNoSlotsAvailable() {
    final int maxPeers = 5;
    final DefaultP2PNetwork network =
        mockNetwork(() -> RlpxConfiguration.create().setMaxPeers(maxPeers));
    network.start();

    doReturn(maxPeers).when(network).connectionCount();
    final List<DiscoveryPeer> peers =
        Stream.iterate(1, n -> n + 1)
            .limit(10)
            .map(
                (seed) -> {
                  DiscoveryPeer peer = createDiscoveryPeer();
                  peer.setStatus(PeerDiscoveryStatus.BONDED);
                  return peer;
                })
            .collect(Collectors.toList());

    lenient().doReturn(peers.stream()).when(network).streamDiscoveredPeers();

    network.attemptPeerConnections();
    verify(network, times(0)).connect(any());
  }

  @Test
  public void connect_beforeStartingNetwork() {
    final DefaultP2PNetwork network = network();
    final Peer peer = mockPeer();

    final CompletableFuture<PeerConnection> connectionResult = network.connect(peer);
    assertThat(connectionResult).isCompletedExceptionally();
    assertThatThrownBy(connectionResult::get)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Attempt to connect to peer before network is ready");
  }

  @Test
  public void connect_toNonListeningPeer() {
    final DefaultP2PNetwork network = network();
    network.start();
    final Peer peer =
        DefaultPeer.fromEnodeURL(
            EnodeURL.builder()
                .ipAddress("127.0.0.1")
                .nodeId(Peer.randomId())
                .disableListening()
                .discoveryPort(30303)
                .build());

    final CompletableFuture<PeerConnection> connectionResult = network.connect(peer);
    assertThat(connectionResult).isCompletedExceptionally();
    assertThatThrownBy(connectionResult::get)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Attempt to connect to peer with no listening port: " + peer.getEnodeURLString());
  }

  @Test
  public void connect_toDiscoveryPeerUpdatesStats() {
    final DefaultP2PNetwork network = network();
    network.start();
    final DiscoveryPeer peer = createDiscoveryPeer();

    assertThat(peer.getLastAttemptedConnection()).isEqualTo(0);

    final CompletableFuture<PeerConnection> result = network.connect(peer);
    assertThat(result).isNotCompletedExceptionally();
    assertThat(peer.getLastAttemptedConnection()).isGreaterThan(0);
  }

  @Test
  public void connect_toDisallowedPeer() {
    final DefaultP2PNetwork network = network();
    network.start();
    final Peer localNode = DefaultPeer.fromEnodeURL(network.getLocalEnode().get());
    final DiscoveryPeer peer = createDiscoveryPeer();

    // Setup permissions to deny peer
    doReturn(false)
        .when(peerPermissions)
        .isPermitted(eq(localNode), eq(peer), eq(Action.RLPX_ALLOW_NEW_OUTBOUND_CONNECTION));

    assertThat(peer.getLastAttemptedConnection()).isEqualTo(0);

    final CompletableFuture<PeerConnection> result = network.connect(peer);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unable to connect to disallowed peer");
    // Last contacted should not be updated.
    assertThat(peer.getLastAttemptedConnection()).isEqualTo(0);
  }

  private DiscoveryPeer createDiscoveryPeer() {
    return createDiscoveryPeer(Peer.randomId(), 999);
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
    final Peer peer = DefaultPeer.fromEnodeURL(remoteEnode);
    final PeerInfo peerInfo =
        new PeerInfo(
            5,
            "test",
            Arrays.asList(Capability.create("eth", 63)),
            remoteEnode.getListeningPortOrZero(),
            remoteEnode.getNodeId());

    final PeerConnection peerConnection = mock(PeerConnection.class);
    lenient().when(peerConnection.getRemoteEnode()).thenReturn(remoteEnode);
    lenient().when(peerConnection.getPeerInfo()).thenReturn(peerInfo);
    lenient().when(peerConnection.getPeer()).thenReturn(peer);

    return peerConnection;
  }

  private DefaultP2PNetwork mockNetwork() {
    return mockNetwork(RlpxConfiguration::create);
  }

  private DefaultP2PNetwork mockNetwork(final Supplier<RlpxConfiguration> rlpxConfig) {
    final DefaultP2PNetwork network = spy(network(rlpxConfig));
    lenient().doReturn(new CompletableFuture<>()).when(network).connect(any());
    return network;
  }

  private DefaultP2PNetwork network() {
    return network(RlpxConfiguration::create);
  }

  private DefaultP2PNetwork network(final Supplier<RlpxConfiguration> rlpxConfig) {
    return (DefaultP2PNetwork) builder(rlpxConfig).build();
  }

  private DefaultP2PNetwork.Builder builder() {
    return builder(RlpxConfiguration::create);
  }

  private DefaultP2PNetwork.Builder builder(final Supplier<RlpxConfiguration> rlpxConfig) {
    final DiscoveryConfiguration noDiscovery = DiscoveryConfiguration.create().setActive(false);
    final NetworkingConfiguration networkingConfiguration =
        NetworkingConfiguration.create()
            .setDiscovery(noDiscovery)
            .setSupportedProtocols(subProtocol())
            .setRlpx(rlpxConfig.get().setBindPort(0));

    return DefaultP2PNetwork.builder()
        .vertx(vertx)
        .config(config)
        .keyPair(KeyPair.generate())
        .metricsSystem(new NoOpMetricsSystem())
        .supportedCapabilities(Arrays.asList(Capability.create("eth", 63)))
        .config(networkingConfiguration)
        .nodePermissioningController(nodePermissioningController)
        .peerPermissions(peerPermissions)
        .blockchain(blockchain);
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

  private DiscoveryPeer createDiscoveryPeer(final BytesValue nodeId, final int listenPort) {
    return DiscoveryPeer.fromEnode(createEnode(nodeId, listenPort));
  }

  private EnodeURL createEnode(final BytesValue nodeId, final int listenPort) {
    return EnodeURL.builder()
        .ipAddress(InetAddress.getLoopbackAddress().getHostAddress())
        .nodeId(nodeId)
        .discoveryAndListeningPorts(listenPort)
        .build();
  }

  private EnodeURL enodeEq(final EnodeURL enodeURL) {
    return argThat((EnodeURL e) -> EnodeURL.sameListeningEndpoint(e, enodeURL));
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

  private static class TestPeerPermissions extends PeerPermissions {

    @Override
    public boolean isPermitted(final Peer localNode, final Peer remotePeer, final Action action) {
      return true;
    }

    public void testDispatchUpdate(
        final boolean permissionsRestricted, final Optional<List<Peer>> affectedPeers) {
      this.dispatchUpdate(permissionsRestricted, affectedPeers);
    }
  }
}
