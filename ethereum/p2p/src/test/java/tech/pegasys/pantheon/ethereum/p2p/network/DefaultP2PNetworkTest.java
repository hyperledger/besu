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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.p2p.peers.PeerTestHelper.createPeer;
import static tech.pegasys.pantheon.ethereum.p2p.peers.PeerTestHelper.enode;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.RlpxConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.discovery.Endpoint;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerBondedObserver;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryAgent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryEvent.PeerBondedEvent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryStatus;
import tech.pegasys.pantheon.ethereum.p2p.peers.MaintainedPeers;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.RlpxAgent;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.MockPeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.MockSubProtocol;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.nat.upnp.UpnpNatManager;
import tech.pegasys.pantheon.nat.upnp.UpnpNatManager.Protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public final class DefaultP2PNetworkTest {

  final MaintainedPeers maintainedPeers = new MaintainedPeers();
  @Mock PeerDiscoveryAgent discoveryAgent;
  @Mock RlpxAgent rlpxAgent;
  @Mock UpnpNatManager natManager;

  private final ArgumentCaptor<PeerBondedObserver> discoverySubscriberCaptor =
      ArgumentCaptor.forClass(PeerBondedObserver.class);

  @Captor private ArgumentCaptor<Stream<? extends Peer>> peerStreamCaptor;

  private final NetworkingConfiguration config =
      NetworkingConfiguration.create()
          .setDiscovery(DiscoveryConfiguration.create().setActive(false))
          .setRlpx(
              RlpxConfiguration.create()
                  .setBindPort(0)
                  .setSupportedProtocols(MockSubProtocol.create()));

  @Before
  public void before() {
    lenient().when(rlpxAgent.start()).thenReturn(CompletableFuture.completedFuture(30303));
    lenient().when(rlpxAgent.stop()).thenReturn(CompletableFuture.completedFuture(null));
    lenient()
        .when(discoveryAgent.start(anyInt()))
        .thenReturn(CompletableFuture.completedFuture(30303));
    lenient().when(discoveryAgent.stop()).thenReturn(CompletableFuture.completedFuture(null));
    lenient()
        .when(discoveryAgent.observePeerBondedEvents(discoverySubscriberCaptor.capture()))
        .thenReturn(1L);
  }

  @Test
  public void addMaintainConnectionPeer_newPeer() {
    final DefaultP2PNetwork network = network();
    network.start();
    final Peer peer = createPeer();

    assertThat(network.addMaintainConnectionPeer(peer)).isTrue();

    assertThat(maintainedPeers.contains(peer)).isTrue();
    verify(rlpxAgent, times(1)).connect(peer);
  }

  @Test
  public void addMaintainConnectionPeer_existingPeer() {
    final DefaultP2PNetwork network = network();
    network.start();
    final Peer peer = createPeer();

    assertThat(network.addMaintainConnectionPeer(peer)).isTrue();
    assertThat(network.addMaintainConnectionPeer(peer)).isFalse();
    verify(rlpxAgent, times(2)).connect(peer);
    assertThat(maintainedPeers.contains(peer)).isTrue();
  }

  @Test
  public void removeMaintainedConnectionPeer_alreadyMaintainedPeer() {
    final DefaultP2PNetwork network = network();
    network.start();
    final Peer peer = createPeer();

    assertThat(network.addMaintainConnectionPeer(peer)).isTrue();
    assertThat(network.removeMaintainedConnectionPeer(peer)).isTrue();

    assertThat(maintainedPeers.contains(peer)).isFalse();
    verify(rlpxAgent, times(1)).connect(peer);
    verify(rlpxAgent, times(1)).disconnect(peer.getId(), DisconnectReason.REQUESTED);
    verify(discoveryAgent, times(1)).dropPeer(peer);
  }

  @Test
  public void removeMaintainedConnectionPeer_nonMaintainedPeer() {
    final DefaultP2PNetwork network = network();
    network.start();
    final Peer peer = createPeer();

    assertThat(network.removeMaintainedConnectionPeer(peer)).isFalse();

    assertThat(maintainedPeers.contains(peer)).isFalse();
    verify(rlpxAgent, times(1)).disconnect(peer.getId(), DisconnectReason.REQUESTED);
    verify(discoveryAgent, times(1)).dropPeer(peer);
  }

  @Test
  public void checkMaintainedConnectionPeers_unconnectedPeer() {
    final DefaultP2PNetwork network = network();
    final Peer peer = createPeer();
    maintainedPeers.add(peer);

    network.start();

    verify(rlpxAgent, times(0)).connect(peer);

    network.checkMaintainedConnectionPeers();
    verify(rlpxAgent, times(1)).connect(peer);
  }

  @Test
  public void checkMaintainedConnectionPeers_connectedPeer() {
    final DefaultP2PNetwork network = network();
    final Peer peer = createPeer();
    maintainedPeers.add(peer);

    network.start();

    // Don't connect to an already connected peer
    final CompletableFuture<PeerConnection> connectionFuture =
        CompletableFuture.completedFuture(MockPeerConnection.create(peer));
    when(rlpxAgent.getPeerConnection(peer)).thenReturn(Optional.of(connectionFuture));
    network.checkMaintainedConnectionPeers();
    verify(rlpxAgent, times(0)).connect(peer);
  }

  @Test
  public void checkMaintainedConnectionPeers_connectingPeer() {
    final DefaultP2PNetwork network = network();
    final Peer peer = createPeer();
    maintainedPeers.add(peer);

    network.start();

    // Don't connect when connection is already pending.
    final CompletableFuture<PeerConnection> connectionFuture = new CompletableFuture<>();
    when(rlpxAgent.getPeerConnection(peer)).thenReturn(Optional.of(connectionFuture));
    network.checkMaintainedConnectionPeers();
    verify(rlpxAgent, times(0)).connect(peer);
  }

  @Test
  public void beforeStartingNetworkEnodeURLShouldNotBePresent() {
    final P2PNetwork network = network();

    assertThat(network.getLocalEnode()).isNotPresent();
  }

  @Test
  public void afterStartingNetworkEnodeURLShouldBePresent() {
    final P2PNetwork network = network();
    network.start();

    assertThat(network.getLocalEnode()).isPresent();
  }

  @Test
  public void start_withNatManager() {
    final String externalIp = "127.0.0.3";
    config.getRlpx().setBindPort(30303);
    config.getDiscovery().setBindPort(30301);

    when(natManager.queryExternalIPAddress())
        .thenReturn(CompletableFuture.completedFuture(externalIp));
    final P2PNetwork network = builder().natManager(natManager).build();

    network.start();
    verify(natManager)
        .requestPortForward(eq(config.getRlpx().getBindPort()), eq(Protocol.TCP), any());
    verify(natManager)
        .requestPortForward(eq(config.getDiscovery().getBindPort()), eq(Protocol.UDP), any());

    assertThat(network.getLocalEnode().get().getIpAsString()).isEqualTo(externalIp);
  }

  @Test
  public void handlePeerBondedEvent_forListeningPeer() {
    final DefaultP2PNetwork network = network();
    network.start();
    final DiscoveryPeer peer = DiscoveryPeer.fromEnode(enode());
    final PeerBondedEvent peerBondedEvent = new PeerBondedEvent(peer, System.currentTimeMillis());

    discoverySubscriberCaptor.getValue().onPeerBonded(peerBondedEvent);
    verify(rlpxAgent, times(1)).connect(peer);
  }

  @Test
  public void handlePeerBondedEvent_forPeerWithNoTcpPort() {
    final DefaultP2PNetwork network = network();
    network.start();
    final DiscoveryPeer peer =
        DiscoveryPeer.fromIdAndEndpoint(
            Peer.randomId(), new Endpoint("127.0.0.1", 999, OptionalInt.empty()));
    final PeerBondedEvent peerBondedEvent = new PeerBondedEvent(peer, System.currentTimeMillis());

    discoverySubscriberCaptor.getValue().onPeerBonded(peerBondedEvent);
    verify(rlpxAgent, times(1)).connect(peer);
  }

  @Test
  public void attemptPeerConnections_bondedPeers() {
    final DiscoveryPeer discoPeer = DiscoveryPeer.fromEnode(enode());
    discoPeer.setStatus(PeerDiscoveryStatus.BONDED);
    final Stream<DiscoveryPeer> peerStream = Stream.of(discoPeer);
    when(discoveryAgent.streamDiscoveredPeers()).thenReturn(peerStream);

    final DefaultP2PNetwork network = network();
    network.attemptPeerConnections();
    verify(rlpxAgent, times(1)).connect(peerStreamCaptor.capture());

    List<? extends Peer> capturedPeers = peerStreamCaptor.getValue().collect(Collectors.toList());
    assertThat(capturedPeers.contains(discoPeer)).isTrue();
    assertThat(capturedPeers.size()).isEqualTo(1);
  }

  @Test
  public void attemptPeerConnections_unbondedPeers() {
    final DiscoveryPeer discoPeer = DiscoveryPeer.fromEnode(enode());
    discoPeer.setStatus(PeerDiscoveryStatus.KNOWN);
    final Stream<DiscoveryPeer> peerStream = Stream.of(discoPeer);
    when(discoveryAgent.streamDiscoveredPeers()).thenReturn(peerStream);

    final DefaultP2PNetwork network = network();
    network.attemptPeerConnections();
    verify(rlpxAgent, times(1)).connect(peerStreamCaptor.capture());

    List<? extends Peer> capturedPeers = peerStreamCaptor.getValue().collect(Collectors.toList());
    assertThat(capturedPeers.contains(discoPeer)).isFalse();
    assertThat(capturedPeers.size()).isEqualTo(0);
  }

  @Test
  public void attemptPeerConnections_sortsPeersByLastContacted() {
    final List<DiscoveryPeer> discoPeers = new ArrayList<>();
    discoPeers.add(DiscoveryPeer.fromEnode(enode()));
    discoPeers.add(DiscoveryPeer.fromEnode(enode()));
    discoPeers.add(DiscoveryPeer.fromEnode(enode()));
    discoPeers.forEach(p -> p.setStatus(PeerDiscoveryStatus.BONDED));
    discoPeers.get(0).setLastAttemptedConnection(10);
    discoPeers.get(2).setLastAttemptedConnection(15);
    when(discoveryAgent.streamDiscoveredPeers()).thenReturn(discoPeers.stream());

    final DefaultP2PNetwork network = network();
    network.attemptPeerConnections();
    verify(rlpxAgent, times(1)).connect(peerStreamCaptor.capture());

    List<? extends Peer> capturedPeers = peerStreamCaptor.getValue().collect(Collectors.toList());
    assertThat(capturedPeers.size()).isEqualTo(3);
    assertThat(capturedPeers.get(0)).isEqualTo(discoPeers.get(1));
    assertThat(capturedPeers.get(1)).isEqualTo(discoPeers.get(0));
    assertThat(capturedPeers.get(2)).isEqualTo(discoPeers.get(2));
  }

  private DefaultP2PNetwork network() {
    return (DefaultP2PNetwork) builder().build();
  }

  private DefaultP2PNetwork.Builder builder() {

    final KeyPair keyPair = KeyPair.generate();

    return DefaultP2PNetwork.builder()
        .config(config)
        .peerDiscoveryAgent(discoveryAgent)
        .rlpxAgent(rlpxAgent)
        .keyPair(keyPair)
        .maintainedPeers(maintainedPeers)
        .metricsSystem(new NoOpMetricsSystem())
        .supportedCapabilities(Capability.create("eth", 63));
  }
}
