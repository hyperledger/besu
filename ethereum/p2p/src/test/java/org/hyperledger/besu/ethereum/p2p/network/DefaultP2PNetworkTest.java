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
package org.hyperledger.besu.ethereum.p2p.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.RlpxConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryAgent;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryStatus;
import org.hyperledger.besu.ethereum.p2p.peers.MaintainedPeers;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.peers.PeerTestHelper;
import org.hyperledger.besu.ethereum.p2p.rlpx.RlpxAgent;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.MockPeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MockSubProtocol;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;
import org.hyperledger.besu.nat.upnp.UpnpNatManager;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.dns.DnsClient;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class DefaultP2PNetworkTest {
  final MaintainedPeers maintainedPeers = new MaintainedPeers();
  final SECP256K1.SecretKey mockKey =
      SECP256K1.SecretKey.fromBytes(
          Bytes32.fromHexString(
              "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63"));
  @Mock PeerDiscoveryAgent discoveryAgent;
  @Mock RlpxAgent rlpxAgent;

  @Captor private ArgumentCaptor<Stream<? extends Peer>> peerStreamCaptor;

  private final NetworkingConfiguration config =
      NetworkingConfiguration.create()
          .setDiscovery(DiscoveryConfiguration.create().setActive(false))
          .setRlpx(
              RlpxConfiguration.create()
                  .setBindPort(0)
                  .setSupportedProtocols(MockSubProtocol.create()));

  @BeforeEach
  public void before() {
    lenient().when(rlpxAgent.start()).thenReturn(CompletableFuture.completedFuture(30303));
    lenient().when(rlpxAgent.stop()).thenReturn(CompletableFuture.completedFuture(null));
    lenient().when(discoveryAgent.stop()).thenReturn(CompletableFuture.completedFuture(null));
    lenient().when(discoveryAgent.checkForkId(any())).thenReturn(true);
    lenient()
        .when(discoveryAgent.start(anyInt()))
        .thenReturn(CompletableFuture.completedFuture(Integer.valueOf(30301)));
  }

  @Test
  public void addMaintainConnectionPeer_newPeer() {
    final DefaultP2PNetwork network = network();
    network.start();
    final Peer peer = PeerTestHelper.createPeer();

    assertThat(network.addMaintainedConnectionPeer(peer)).isTrue();

    assertThat(maintainedPeers.contains(peer)).isTrue();
    verify(rlpxAgent).connect(peer);
    verify(discoveryAgent).bond(peer);
  }

  @Test
  public void addMaintainConnectionPeer_existingPeer() {
    final DefaultP2PNetwork network = network();
    network.start();
    final Peer peer = PeerTestHelper.createPeer();

    assertThat(network.addMaintainedConnectionPeer(peer)).isTrue();
    assertThat(network.addMaintainedConnectionPeer(peer)).isFalse();
    verify(rlpxAgent, times(2)).connect(peer);
    verify(discoveryAgent, times(2)).bond(peer);
    assertThat(maintainedPeers.contains(peer)).isTrue();
  }

  @Test
  public void removeMaintainedConnectionPeer_alreadyMaintainedPeer() {
    final DefaultP2PNetwork network = network();
    network.start();
    final Peer peer = PeerTestHelper.createPeer();

    assertThat(network.addMaintainedConnectionPeer(peer)).isTrue();
    assertThat(network.removeMaintainedConnectionPeer(peer)).isTrue();

    assertThat(maintainedPeers.contains(peer)).isFalse();
    verify(rlpxAgent).connect(peer);
    verify(discoveryAgent).bond(peer);
    verify(rlpxAgent).disconnect(peer.getId(), DisconnectReason.REQUESTED);
    verify(discoveryAgent).dropPeer(peer);
  }

  @Test
  public void removeMaintainedConnectionPeer_nonMaintainedPeer() {
    final DefaultP2PNetwork network = network();
    network.start();
    final Peer peer = PeerTestHelper.createPeer();

    assertThat(network.removeMaintainedConnectionPeer(peer)).isFalse();

    assertThat(maintainedPeers.contains(peer)).isFalse();
    verify(rlpxAgent, times(1)).disconnect(peer.getId(), DisconnectReason.REQUESTED);
    verify(discoveryAgent, times(1)).dropPeer(peer);
  }

  @Test
  public void checkMaintainedConnectionPeers_doesNotConnectToSelf() {
    final DefaultP2PNetwork network = network();
    network.start();

    final Optional<EnodeURL> maybeSelfEnode = network.getLocalEnode();
    final Peer selfPeer = PeerTestHelper.createPeer(maybeSelfEnode.get());
    maintainedPeers.add(selfPeer);

    verify(rlpxAgent, times(0)).connect(selfPeer);

    network.checkMaintainedConnectionPeers();
    verify(rlpxAgent, times(0)).connect(selfPeer);
  }

  @Test
  public void checkMaintainedConnectionPeers_unconnectedPeer() {
    final DefaultP2PNetwork network = network();
    final Peer peer = PeerTestHelper.createPeer();

    network.start();

    maintainedPeers.add(peer);

    verify(rlpxAgent, times(0)).connect(peer);

    network.checkMaintainedConnectionPeers();
    verify(rlpxAgent, times(1)).connect(peer);
  }

  @Test
  public void checkMaintainedConnectionPeers_connectedPeer() {
    final DefaultP2PNetwork network = network();
    final Peer peer = PeerTestHelper.createPeer();

    network.start();

    maintainedPeers.add(peer);

    // Don't connect to an already connected peer
    when(rlpxAgent.streamActiveConnections())
        .thenReturn(Stream.of(MockPeerConnection.create(peer)));
    network.checkMaintainedConnectionPeers();
    verify(rlpxAgent, times(0)).connect(peer);
  }

  @Test
  public void beforeStartingNetworkEnodeURLShouldNotBePresent() {
    final P2PNetwork network = network();

    Assertions.assertThat(network.getLocalEnode()).isNotPresent();
  }

  @Test
  public void afterStartingNetworkEnodeURLShouldBePresent() {
    final P2PNetwork network = network();
    network.start();

    Assertions.assertThat(network.getLocalEnode()).isPresent();
  }

  @Test
  public void start_withNatManager() {
    final String externalIp = "127.0.0.3";
    config.getRlpx().setBindPort(30303);
    config.getDiscovery().setBindPort(30301);

    final UpnpNatManager upnpNatManager = mock(UpnpNatManager.class);
    when(upnpNatManager.getNatMethod()).thenReturn(NatMethod.UPNP);
    when(upnpNatManager.queryExternalIPAddress())
        .thenReturn(CompletableFuture.completedFuture(externalIp));

    final NatService natService = spy(new NatService(Optional.of(upnpNatManager)));
    final P2PNetwork network = builder().natService(natService).build();

    network.start();
    verify(upnpNatManager)
        .requestPortForward(eq(config.getRlpx().getBindPort()), eq(NetworkProtocol.TCP), any());
    verify(upnpNatManager)
        .requestPortForward(
            eq(config.getDiscovery().getBindPort()), eq(NetworkProtocol.UDP), any());

    Assertions.assertThat(network.getLocalEnode().get().getIpAsString()).isEqualTo(externalIp);
  }

  @Test
  public void start_withNatManagerUpnpP2p() {
    final String externalIp = "127.0.0.3";
    config.getRlpx().setBindPort(30303);
    config.getDiscovery().setBindPort(30301);

    final UpnpNatManager upnpNatManager = mock(UpnpNatManager.class);
    when(upnpNatManager.getNatMethod()).thenReturn(NatMethod.UPNPP2PONLY);
    when(upnpNatManager.queryExternalIPAddress())
        .thenReturn(CompletableFuture.completedFuture(externalIp));

    final NatService natService = spy(new NatService(Optional.of(upnpNatManager)));
    final P2PNetwork network = builder().natService(natService).build();

    network.start();
    verify(upnpNatManager)
        .requestPortForward(eq(config.getRlpx().getBindPort()), eq(NetworkProtocol.TCP), any());
    verify(upnpNatManager)
        .requestPortForward(
            eq(config.getDiscovery().getBindPort()), eq(NetworkProtocol.UDP), any());

    Assertions.assertThat(network.getLocalEnode().get().getIpAsString()).isEqualTo(externalIp);
  }

  @Test
  public void attemptPeerConnections_bondedPeers() {
    final DiscoveryPeer discoPeer = DiscoveryPeer.fromEnode(PeerTestHelper.enode());
    discoPeer.setStatus(PeerDiscoveryStatus.BONDED);
    final Stream<DiscoveryPeer> peerStream = Stream.of(discoPeer);
    when(discoveryAgent.streamDiscoveredPeers()).thenReturn(peerStream);

    final DefaultP2PNetwork network = network();
    network.attemptPeerConnections();
    verify(rlpxAgent, times(1)).connect(peerStreamCaptor.capture());

    final List<? extends Peer> capturedPeers =
        peerStreamCaptor.getValue().collect(Collectors.toList());
    assertThat(capturedPeers.contains(discoPeer)).isTrue();
    assertThat(capturedPeers.size()).isEqualTo(1);
  }

  @Test
  public void attemptPeerConnections_unbondedPeers() {
    final DiscoveryPeer discoPeer = DiscoveryPeer.fromEnode(PeerTestHelper.enode());
    discoPeer.setStatus(PeerDiscoveryStatus.KNOWN);
    final Stream<DiscoveryPeer> peerStream = Stream.of(discoPeer);
    when(discoveryAgent.streamDiscoveredPeers()).thenReturn(peerStream);

    final DefaultP2PNetwork network = network();
    network.attemptPeerConnections();
    verify(rlpxAgent, times(1)).connect(peerStreamCaptor.capture());

    final List<? extends Peer> capturedPeers =
        peerStreamCaptor.getValue().collect(Collectors.toList());
    assertThat(capturedPeers.contains(discoPeer)).isFalse();
    assertThat(capturedPeers.size()).isEqualTo(0);
  }

  @Test
  public void attemptPeerConnections_sortsPeersByLastContacted() {
    final List<DiscoveryPeer> discoPeers = new ArrayList<>();
    discoPeers.add(DiscoveryPeer.fromEnode(PeerTestHelper.enode()));
    discoPeers.add(DiscoveryPeer.fromEnode(PeerTestHelper.enode()));
    discoPeers.add(DiscoveryPeer.fromEnode(PeerTestHelper.enode()));
    discoPeers.forEach(p -> p.setStatus(PeerDiscoveryStatus.BONDED));
    discoPeers.get(0).setLastAttemptedConnection(10);
    discoPeers.get(2).setLastAttemptedConnection(15);
    when(discoveryAgent.streamDiscoveredPeers()).thenReturn(discoPeers.stream());

    final DefaultP2PNetwork network = network();
    network.attemptPeerConnections();
    verify(rlpxAgent, times(1)).connect(peerStreamCaptor.capture());

    final List<? extends Peer> capturedPeers =
        peerStreamCaptor.getValue().collect(Collectors.toList());
    assertThat(capturedPeers.size()).isEqualTo(3);
    assertThat(capturedPeers.get(0)).isEqualTo(discoPeers.get(1));
    assertThat(capturedPeers.get(1)).isEqualTo(discoPeers.get(0));
    assertThat(capturedPeers.get(2)).isEqualTo(discoPeers.get(2));
  }

  @Test
  public void cannotAddNodeWithSameEnodeID() {
    final DefaultP2PNetwork network = network();
    network.start();
    assertThat(network.getLocalEnode()).isPresent();
    final Peer peer = PeerTestHelper.createPeer(network.getLocalEnode().get().getNodeId());
    assertThat(network.addMaintainedConnectionPeer(peer)).isFalse();
  }

  @Test
  public void shouldNotStartDnsDiscoveryWhenDnsURLIsNotConfigured() {
    final DefaultP2PNetwork testClass = network();
    testClass.start();
    // ensure DnsDaemon is NOT present:
    assertThat(testClass.getDnsDaemon()).isNotPresent();
  }

  @Test
  public void shouldStartDnsDiscoveryWhenDnsURLIsConfigured() {
    // create a discovery config with a dns config
    final DiscoveryConfiguration disco =
        DiscoveryConfiguration.create().setDnsDiscoveryURL("enrtree://mock@localhost");

    // spy on config to return dns discovery config:
    final NetworkingConfiguration dnsConfig =
        when(spy(config).getDiscovery()).thenReturn(disco).getMock();

    Vertx vertx = mock(Vertx.class);
    when(vertx.createDnsClient(any())).thenReturn(mock(DnsClient.class));
    when(vertx.getOrCreateContext()).thenReturn(mock(Context.class));

    // spy on DefaultP2PNetwork
    final DefaultP2PNetwork testClass =
        (DefaultP2PNetwork) builder().vertx(vertx).config(dnsConfig).build();

    testClass.start();
    assertThat(testClass.getDnsDaemon()).isPresent();
  }

  @Test
  public void shouldUseDnsServerOverrideIfPresent() {
    // create a discovery config with a dns config
    final DiscoveryConfiguration disco =
        DiscoveryConfiguration.create().setDnsDiscoveryURL("enrtree://mock@localhost");

    // spy on config to return dns discovery config:
    final NetworkingConfiguration dnsConfig = spy(config);
    doReturn(disco).when(dnsConfig).getDiscovery();
    doReturn(Optional.of("localhost")).when(dnsConfig).getDnsDiscoveryServerOverride();

    Vertx vertx = mock(Vertx.class);
    when(vertx.createDnsClient(any())).thenReturn(mock(DnsClient.class));
    when(vertx.getOrCreateContext()).thenReturn(mock(Context.class));

    final DefaultP2PNetwork testClass =
        (DefaultP2PNetwork) builder().config(dnsConfig).vertx(vertx).build();
    testClass.start();

    // ensure we used the dns server override config when building DNSDaemon:
    assertThat(testClass.getDnsDaemon()).isPresent();
    verify(dnsConfig, times(2)).getDnsDiscoveryServerOverride();
  }

  private DefaultP2PNetwork network() {
    return (DefaultP2PNetwork) builder().build();
  }

  private DefaultP2PNetwork.Builder builder() {

    final NodeKey nodeKey = NodeKeyUtils.generate();

    return DefaultP2PNetwork.builder()
        .config(config)
        .peerDiscoveryAgent(discoveryAgent)
        .rlpxAgent(rlpxAgent)
        .nodeKey(nodeKey)
        .maintainedPeers(maintainedPeers)
        .metricsSystem(new NoOpMetricsSystem())
        .supportedCapabilities(Capability.create("eth", 63))
        .storageProvider(new InMemoryKeyValueStorageProvider())
        .blockNumberForks(Collections.emptyList())
        .timestampForks(Collections.emptyList())
        .allConnectionsSupplier(Stream::empty)
        .allActiveConnectionsSupplier(Stream::empty);
  }
}
