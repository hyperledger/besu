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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.Endpoint;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerBondedObserver;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryStatus;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryTestHelper;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions.Action;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissionsDenylist;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.util.Subscribers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.apache.tuweni.units.bigints.UInt64;
import org.assertj.core.api.Assertions;
import org.ethereum.beacon.discovery.schema.IdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class PeerDiscoveryControllerTest {

  private static final byte MOST_SIGNIFICANT_BIT_MASK = -128;
  private static final PeerRequirement PEER_REQUIREMENT = () -> true;
  private static final long TABLE_REFRESH_INTERVAL_MS = TimeUnit.HOURS.toMillis(1);
  private PeerDiscoveryController controller;
  private DiscoveryPeer localPeer;
  private PeerTable peerTable;
  private NodeKey localNodeKey;
  private final AtomicInteger counter = new AtomicInteger(1);
  private final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();

  private static Long longDelayFunction(final Long prev) {
    return 999999999L;
  }

  private static Long shortDelayFunction(final Long prev) {
    return Math.max(100, prev * 2);
  }

  @Before
  public void initializeMocks() {
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    localNodeKey = nodeKeys.get(0);
    localPeer = helper.createDiscoveryPeer(localNodeKey);
    peerTable = new PeerTable(localPeer.getId());
  }

  @After
  public void stopTable() {
    if (controller != null) {
      controller.stop().join();
    }
  }

  @Test
  public void bootstrapPeersRetriesSent() {
    // Create peers.
    final int peerCount = 3;
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(peerCount);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(nodeKeys);

    final MockTimerUtil timer = spy(new MockTimerUtil());
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers)
            .timerUtil(timer)
            .outboundMessageHandler(outboundMessageHandler)
            .build();
    controller.setRetryDelayFunction(PeerDiscoveryControllerTest::shortDelayFunction);

    // Mock the creation of the PING packet, so that we can control the hash,
    // which gets validated when receiving the PONG.
    final PingPacketData mockPing =
        PingPacketData.create(
            Optional.ofNullable(localPeer.getEndpoint()), peers.get(0).getEndpoint(), UInt64.ONE);
    final Packet mockPacket = Packet.create(PacketType.PING, mockPing, nodeKeys.get(0));
    mockPingPacketCreation(mockPacket);

    controller.start();

    final int timeouts = 4;
    for (int i = 0; i < timeouts; i++) {
      timer.runTimerHandlers();
    }
    final int expectedTimerEvents = (timeouts + 1) * peerCount;
    verify(timer, atLeast(expectedTimerEvents)).setTimer(anyLong(), any());

    // Within this time period, 4 timers should be placed with these timeouts.
    final long[] expectedTimeouts = {100, 200, 400, 800};
    for (final long timeout : expectedTimeouts) {
      verify(timer, times(peerCount)).setTimer(eq(timeout), any());
    }

    // Check that 5 PING packets were sent for each peer (the initial + 4 attempts following
    // timeouts).
    peers.forEach(
        p ->
            verify(outboundMessageHandler, times(timeouts + 1))
                .send(eq(p), matchPacketOfType(PacketType.PING)));

    controller
        .streamDiscoveredPeers()
        .forEach(p -> assertThat(p.getStatus()).isEqualTo(PeerDiscoveryStatus.BONDING));
  }

  private void mockPingPacketCreation(final Packet mockPacket) {
    mockPingPacketCreation(Optional.empty(), mockPacket);
  }

  private void mockPingPacketCreation(final DiscoveryPeer peer, final Packet mockPacket) {
    mockPingPacketCreation(Optional.of(peer), mockPacket);
  }

  private void mockPingPacketCreation(final Optional<DiscoveryPeer> peer, final Packet mockPacket) {
    doAnswer(
            invocation -> {
              final Consumer<Packet> handler = invocation.getArgument(2);
              handler.accept(mockPacket);
              return null;
            })
        .when(controller)
        .createPacket(
            eq(PacketType.PING),
            peer.isPresent() ? matchPingDataForPeer(peer.get()) : any(),
            any());
  }

  @Test
  public void bootstrapPeersRetriesStoppedUponResponse() {
    // Create peers.
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(3);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(nodeKeys);

    final MockTimerUtil timer = new MockTimerUtil();
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers)
            .timerUtil(timer)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    // Mock the creation of the PING packet, so that we can control the hash,
    // which gets validated when receiving the PONG.
    final PingPacketData mockPing =
        PingPacketData.create(
            Optional.ofNullable(localPeer.getEndpoint()), peers.get(0).getEndpoint(), UInt64.ONE);
    final Packet mockPacket = Packet.create(PacketType.PING, mockPing, nodeKeys.get(0));
    mockPingPacketCreation(mockPacket);

    controller.start();

    // Invoke timers several times so that ping to peers should be resent
    for (int i = 0; i < 3; i++) {
      timer.runTimerHandlers();
    }

    // Assert PING packet was sent for peer[0] 4 times.
    for (final DiscoveryPeer peer : peers) {
      verify(outboundMessageHandler, times(4)).send(eq(peer), matchPacketOfType(PacketType.PING));
    }

    // Simulate a PONG message from peer 0.
    final PongPacketData packetData =
        PongPacketData.create(localPeer.getEndpoint(), mockPacket.getHash(), UInt64.ONE);
    final Packet packet = Packet.create(PacketType.PONG, packetData, nodeKeys.get(0));
    controller.onMessage(packet, peers.get(0));

    // Invoke timers again
    for (int i = 0; i < 2; i++) {
      timer.runTimerHandlers();
    }

    // Ensure we receive no more PING packets for peer[0].
    // Assert PING packet was sent for peer[0] 4 times.
    for (final DiscoveryPeer peer : peers) {
      final int expectedCount = peer.equals(peers.get(0)) ? 4 : 6;
      verify(outboundMessageHandler, times(expectedCount))
          .send(eq(peer), matchPacketOfType(PacketType.PING));
    }
  }

  @Test
  public void shouldStopRetryingInteractionWhenLimitIsReached() {
    // Create peers.
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(3);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(nodeKeys);

    final MockTimerUtil timer = new MockTimerUtil();
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers)
            .timerUtil(timer)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    // Mock the creation of the PING packet, so that we can control the hash,
    // which gets validated when receiving the PONG.
    final PingPacketData mockPing =
        PingPacketData.create(
            Optional.ofNullable(localPeer.getEndpoint()), peers.get(0).getEndpoint(), UInt64.ONE);
    final Packet mockPacket = Packet.create(PacketType.PING, mockPing, nodeKeys.get(0));
    mockPingPacketCreation(mockPacket);

    controller.start();

    // Invoke timers several times so that ping to peers should be resent
    for (int i = 0; i < 10; i++) {
      timer.runTimerHandlers();
    }

    // Assert PING packet was sent only 6 times (initial attempt plus 5 retries)
    for (final DiscoveryPeer peer : peers) {
      verify(outboundMessageHandler, times(6)).send(eq(peer), matchPacketOfType(PacketType.PING));
    }
  }

  @Test
  public void shouldRespondToPingRequest() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 1);

    final DiscoveryPeer discoPeer = peers.get(0);

    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final Endpoint localEndpoint = localPeer.getEndpoint();

    // Setup ping to be sent to discoPeer
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localEndpoint), discoPeer.getEndpoint(), UInt64.ONE);
    final Packet discoPeerPing = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));
    mockPingPacketCreation(discoPeer, discoPeerPing);

    controller.onMessage(discoPeerPing, discoPeer);

    verify(outboundMessageHandler, times(1))
        .send(eq(discoPeer), matchPacketOfType(PacketType.PONG));
  }

  @Test
  public void shouldNotRespondToExpiredPingRequest() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 1);

    final DiscoveryPeer discoPeer = peers.get(0);

    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final Endpoint localEndpoint = localPeer.getEndpoint();

    // Setup ping to be sent to discoPeer
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localEndpoint),
            discoPeer.getEndpoint(),
            Instant.now().getEpochSecond() - PacketData.DEFAULT_EXPIRATION_PERIOD_SEC,
            UInt64.ONE);
    final Packet discoPeerPing = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));
    mockPingPacketCreation(discoPeer, discoPeerPing);

    controller.onMessage(discoPeerPing, discoPeer);

    verify(outboundMessageHandler, times(0))
        .send(eq(discoPeer), matchPacketOfType(PacketType.PONG));
  }

  @Test
  public void bootstrapPeersPongReceived_HashMatched() {
    // Create peers.
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(3);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(nodeKeys);

    final MockTimerUtil timer = new MockTimerUtil();
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers)
            .timerUtil(timer)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    // Mock the creation of the PING packet, so that we can control the hash, which gets validated
    // when receiving the PONG.
    final PingPacketData mockPing =
        PingPacketData.create(
            Optional.ofNullable(localPeer.getEndpoint()), peers.get(0).getEndpoint(), UInt64.ONE);
    final Packet mockPacket = Packet.create(PacketType.PING, mockPing, nodeKeys.get(0));
    mockPingPacketCreation(mockPacket);

    controller.start();

    assertThat(
            controller
                .streamDiscoveredPeers()
                .filter(p -> p.getStatus() == PeerDiscoveryStatus.BONDING))
        .hasSize(3);

    // Simulate PONG messages from all peers
    for (int i = 0; i < 3; i++) {
      final PongPacketData packetData =
          PongPacketData.create(localPeer.getEndpoint(), mockPacket.getHash(), UInt64.ONE);
      final Packet packet0 = Packet.create(PacketType.PONG, packetData, nodeKeys.get(i));
      controller.onMessage(packet0, peers.get(i));
    }

    // Ensure that the peer controller is now sending FIND_NEIGHBORS messages for this peer.
    for (int i = 0; i < 3; i++) {
      verify(outboundMessageHandler, times(1))
          .send(eq(peers.get(i)), matchPacketOfType(PacketType.FIND_NEIGHBORS));
    }

    // Invoke timeouts and check that we resent our neighbors request
    timer.runTimerHandlers();
    for (int i = 0; i < 3; i++) {
      verify(outboundMessageHandler, times(2))
          .send(eq(peers.get(i)), matchPacketOfType(PacketType.FIND_NEIGHBORS));
    }

    assertThat(
            controller
                .streamDiscoveredPeers()
                .filter(p -> p.getStatus() == PeerDiscoveryStatus.BONDING))
        .hasSize(0);
    assertThat(
            controller
                .streamDiscoveredPeers()
                .filter(p -> p.getStatus() == PeerDiscoveryStatus.BONDED))
        .hasSize(3);
  }

  @Test
  public void bootstrapPeersPongReceived_HashUnmatched() {
    // Create peers.
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(3);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(nodeKeys);

    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder().peers(peers).outboundMessageHandler(outboundMessageHandler).build();
    controller.setRetryDelayFunction(PeerDiscoveryControllerTest::longDelayFunction);

    // Mock the creation of the PING packet, so that we can control the hash, which gets validated
    // when
    // processing the PONG.
    final PingPacketData mockPing =
        PingPacketData.create(
            Optional.ofNullable(localPeer.getEndpoint()), peers.get(0).getEndpoint(), UInt64.ONE);
    final Packet mockPacket = Packet.create(PacketType.PING, mockPing, nodeKeys.get(0));
    mockPingPacketCreation(mockPacket);

    controller.start();

    assertThat(
            controller
                .streamDiscoveredPeers()
                .filter(p -> p.getStatus() == PeerDiscoveryStatus.BONDING))
        .hasSize(3);

    // Send a PONG packet from peer 1, with an incorrect hash.
    final PongPacketData packetData =
        PongPacketData.create(localPeer.getEndpoint(), Bytes.fromHexString("1212"), UInt64.ONE);
    final Packet packet = Packet.create(PacketType.PONG, packetData, nodeKeys.get(1));
    controller.onMessage(packet, peers.get(1));

    // No FIND_NEIGHBORS packet was sent for peer 1.
    verify(outboundMessageHandler, never())
        .send(eq(peers.get(1)), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    assertThat(
            controller
                .streamDiscoveredPeers()
                .filter(p -> p.getStatus() == PeerDiscoveryStatus.BONDING))
        .hasSize(3);
  }

  @Test
  public void findNeighborsSentAfterBondingFinished() {
    // Create three peers, out of which the first two are bootstrap peers.
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(nodeKeys);

    // Initialize the peer controller, setting a high controller refresh interval and a high timeout
    // threshold,
    // to avoid retries getting in the way of this test.
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers.get(0))
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    // Mock the creation of the PING packet, so that we can control the hash, which gets validated
    // when
    // processing the PONG.
    final PingPacketData mockPing =
        PingPacketData.create(
            Optional.ofNullable(localPeer.getEndpoint()), peers.get(0).getEndpoint(), UInt64.ONE);
    final Packet mockPacket = Packet.create(PacketType.PING, mockPing, nodeKeys.get(0));
    mockPingPacketCreation(mockPacket);
    controller.setRetryDelayFunction((prev) -> 999999999L);
    controller.start();

    // Verify that the PING was sent.
    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(0)), matchPacketOfType(PacketType.PING));

    // Simulate a PONG message from peer[0].
    respondWithPong(peers.get(0), nodeKeys.get(0), mockPacket.getHash());

    // Verify that the FIND_NEIGHBORS packet was sent with target == localPeer.
    final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
    verify(outboundMessageHandler, atLeast(1)).send(eq(peers.get(0)), captor.capture());
    final List<Packet> neighborsPackets =
        captor.getAllValues().stream()
            .filter(p -> p.getType().equals(PacketType.FIND_NEIGHBORS))
            .collect(Collectors.toList());
    assertThat(neighborsPackets.size()).isEqualTo(1);
    final Packet nieghborsPacket = neighborsPackets.get(0);
    final Optional<FindNeighborsPacketData> maybeData =
        nieghborsPacket.getPacketData(FindNeighborsPacketData.class);
    Assertions.assertThat(maybeData).isPresent();
    final FindNeighborsPacketData data = maybeData.get();
    assertThat(data.getTarget()).isEqualTo(localPeer.getId());

    assertThat(controller.streamDiscoveredPeers()).hasSize(1);
    assertThat(controller.streamDiscoveredPeers().findFirst().isPresent()).isTrue();
    assertThat(controller.streamDiscoveredPeers().findFirst().get().getStatus())
        .isEqualTo(PeerDiscoveryStatus.BONDED);
  }

  private ControllerBuilder getControllerBuilder() {
    return ControllerBuilder.create()
        .nodeKey(localNodeKey)
        .localPeer(localPeer)
        .peerTable(peerTable);
  }

  private void respondWithPong(
      final DiscoveryPeer discoveryPeer, final NodeKey nodeKey, final Bytes hash) {
    final PongPacketData packetData0 =
        PongPacketData.create(localPeer.getEndpoint(), hash, UInt64.ONE);
    final Packet pongPacket0 = Packet.create(PacketType.PONG, packetData0, nodeKey);
    controller.onMessage(pongPacket0, discoveryPeer);
  }

  @Test
  public void peerSeenTwice() {
    // Create three peers, out of which the first two are bootstrap peers.
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(3);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(nodeKeys);

    // Initialize the peer controller
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers.get(0), peers.get(1))
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    // Mock the creation of the PING packet, so that we can control the hash, which gets validated
    // when processing the PONG.
    final PingPacketData pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localPeer.getEndpoint()), peers.get(0).getEndpoint(), UInt64.ONE);
    final Packet pingPacket = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));

    mockPingPacketCreation(pingPacket);

    controller.setRetryDelayFunction((prev) -> 999999999L);
    controller.start();

    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(0)), matchPacketOfType(PacketType.PING));
    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(1)), matchPacketOfType(PacketType.PING));

    // Simulate a PONG message from peer[0].
    respondWithPong(peers.get(0), nodeKeys.get(0), pingPacket.getHash());

    // Assert that we're bonding with the third peer.
    assertThat(controller.streamDiscoveredPeers()).hasSize(2);
    assertThat(controller.streamDiscoveredPeers())
        .filteredOn(p -> p.getStatus() == PeerDiscoveryStatus.BONDING)
        .hasSize(1);
    assertThat(controller.streamDiscoveredPeers())
        .filteredOn(p -> p.getStatus() == PeerDiscoveryStatus.BONDED)
        .hasSize(1);

    final PongPacketData pongPacketData =
        PongPacketData.create(localPeer.getEndpoint(), pingPacket.getHash(), UInt64.ONE);
    final Packet pongPacket = Packet.create(PacketType.PONG, pongPacketData, nodeKeys.get(1));
    controller.onMessage(pongPacket, peers.get(1));

    // Now after we got that pong we should have sent a find neighbours message...
    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(0)), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    // Simulate a NEIGHBORS message from peer[0] listing peer[2].
    final NeighborsPacketData neighbors0 =
        NeighborsPacketData.create(Collections.singletonList(peers.get(2)));
    final Packet neighborsPacket0 =
        Packet.create(PacketType.NEIGHBORS, neighbors0, nodeKeys.get(0));
    controller.onMessage(neighborsPacket0, peers.get(0));

    // Assert that we're bonded with the third peer.
    assertThat(controller.streamDiscoveredPeers()).hasSize(2);
    assertThat(controller.streamDiscoveredPeers())
        .filteredOn(p -> p.getStatus() == PeerDiscoveryStatus.BONDED)
        .hasSize(2);

    // Simulate bonding and neighbors packet from the second bootstrap peer, with peer[2] reported
    // in the peer list.
    final NeighborsPacketData neighbors1 =
        NeighborsPacketData.create(Collections.singletonList(peers.get(2)));
    final Packet neighborsPacket1 =
        Packet.create(PacketType.NEIGHBORS, neighbors1, nodeKeys.get(1));
    controller.onMessage(neighborsPacket1, peers.get(1));

    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(2)), matchPacketOfType(PacketType.PING));

    // Send a PONG packet from peer[2], to transition it to the BONDED state.
    final PongPacketData packetData2 =
        PongPacketData.create(localPeer.getEndpoint(), pingPacket.getHash(), UInt64.ONE);
    final Packet pongPacket2 = Packet.create(PacketType.PONG, packetData2, nodeKeys.get(2));
    controller.onMessage(pongPacket2, peers.get(2));

    // Assert we're now bonded with peer[2].
    assertThat(controller.streamDiscoveredPeers())
        .filteredOn(p -> p.equals(peers.get(2)) && p.getStatus() == PeerDiscoveryStatus.BONDED)
        .hasSize(1);

    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(2)), matchPacketOfType(PacketType.PING));
  }

  @Test
  public void startTwice() {
    startPeerDiscoveryController();
    assertThatThrownBy(() -> controller.start()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void stopTwice() {
    startPeerDiscoveryController();
    controller.stop();
    controller.stop();
    // no exception
  }

  @Test
  public void shouldBondWithNewPeerWhenReceivedPing() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 1);
    startPeerDiscoveryController();

    final Packet pingPacket = mockPingPacket(peers.get(0), localPeer);
    controller.onMessage(pingPacket, peers.get(0));
    verify(controller, times(1)).bond(peers.get(0));
  }

  @Test
  public void shouldNotAddSelfWhenReceivedPingFromSelf() {
    startPeerDiscoveryController();
    final DiscoveryPeer localPeer = DiscoveryPeer.fromEnode(this.localPeer.getEnodeURL());

    final Packet pingPacket = mockPingPacket(this.localPeer, this.localPeer);
    controller.onMessage(pingPacket, localPeer);

    assertThat(controller.streamDiscoveredPeers()).doesNotContain(localPeer);
  }

  @Test
  public void shouldNotRemoveExistingPeerWhenReceivedPing() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 1);
    startPeerDiscoveryController();

    peerTable.tryAdd(peers.get(0));
    assertThat(controller.streamDiscoveredPeers()).contains(peers.get(0));

    final Packet pingPacket = mockPingPacket(peers.get(0), localPeer);
    controller.onMessage(pingPacket, peers.get(0));

    assertThat(controller.streamDiscoveredPeers()).contains(peers.get(0));
  }

  @Test
  public void shouldNotAddNewPeerWhenReceivedPongFromDenylistedPeer() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 3);

    final DiscoveryPeer discoPeer = peers.get(0);
    final DiscoveryPeer otherPeer = peers.get(1);
    final DiscoveryPeer otherPeer2 = peers.get(2);

    final PeerPermissionsDenylist denylist = PeerPermissionsDenylist.create();
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .peerPermissions(denylist)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final Endpoint localEndpoint = localPeer.getEndpoint();

    // Setup ping to be sent to discoPeer
    List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    PingPacketData pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localEndpoint), discoPeer.getEndpoint(), UInt64.ONE);
    final Packet discoPeerPing = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));
    mockPingPacketCreation(discoPeer, discoPeerPing);

    controller.start();
    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(0)), matchPacketOfType(PacketType.PING));

    final Packet pongFromDiscoPeer =
        MockPacketDataFactory.mockPongPacket(discoPeer, discoPeerPing.getHash());
    controller.onMessage(pongFromDiscoPeer, discoPeer);

    verify(outboundMessageHandler, times(1))
        .send(eq(discoPeer), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    // Setup ping to be sent to otherPeer after neighbors packet is received
    nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localEndpoint), otherPeer.getEndpoint(), UInt64.ONE);
    final Packet pingPacket = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));
    mockPingPacketCreation(otherPeer, pingPacket);

    // Setup ping to be sent to otherPeer2 after neighbors packet is received
    nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localEndpoint), otherPeer2.getEndpoint(), UInt64.ONE);
    final Packet pingPacket2 = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));
    mockPingPacketCreation(otherPeer2, pingPacket2);

    final Packet neighborsPacket =
        MockPacketDataFactory.mockNeighborsPacket(discoPeer, otherPeer, otherPeer2);
    controller.onMessage(neighborsPacket, discoPeer);

    verify(outboundMessageHandler, times(peers.size()))
        .send(any(), matchPacketOfType(PacketType.PING));

    final Packet pongPacket = MockPacketDataFactory.mockPongPacket(otherPeer, pingPacket.getHash());
    controller.onMessage(pongPacket, otherPeer);

    // Denylist otherPeer2 before sending return pong
    denylist.add(otherPeer2);
    final Packet pongPacket2 =
        MockPacketDataFactory.mockPongPacket(otherPeer2, pingPacket2.getHash());
    controller.onMessage(pongPacket2, otherPeer2);

    assertThat(controller.streamDiscoveredPeers()).hasSize(2);
    assertThat(controller.streamDiscoveredPeers()).contains(discoPeer);
    assertThat(controller.streamDiscoveredPeers()).contains(otherPeer);
    assertThat(controller.streamDiscoveredPeers()).doesNotContain(otherPeer2);
  }

  private PacketData matchPingDataForPeer(final DiscoveryPeer peer) {
    return argThat((PacketData data) -> ((PingPacketData) data).getTo().equals(peer.getEndpoint()));
  }

  private Packet matchPacketOfType(final PacketType type) {
    return argThat((Packet packet) -> packet.getType().equals(type));
  }

  @Test
  public void shouldNotBondWithDenylistedPeer() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 3);

    final DiscoveryPeer discoPeer = peers.get(0);
    final DiscoveryPeer otherPeer = peers.get(1);
    final DiscoveryPeer otherPeer2 = peers.get(2);

    final PeerPermissionsDenylist denylist = PeerPermissionsDenylist.create();
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .peerPermissions(denylist)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final Endpoint localEndpoint = localPeer.getEndpoint();

    // Setup ping to be sent to discoPeer
    List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    PingPacketData pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localEndpoint), discoPeer.getEndpoint(), UInt64.ONE);
    final Packet discoPeerPing = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));
    mockPingPacketCreation(discoPeer, discoPeerPing);

    controller.start();
    verify(outboundMessageHandler, times(1)).send(any(), matchPacketOfType(PacketType.PING));

    final Packet pongFromDiscoPeer =
        MockPacketDataFactory.mockPongPacket(discoPeer, discoPeerPing.getHash());
    controller.onMessage(pongFromDiscoPeer, discoPeer);

    verify(outboundMessageHandler, times(1))
        .send(eq(discoPeer), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    // Setup ping to be sent to otherPeer after neighbors packet is received
    nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localEndpoint), otherPeer.getEndpoint(), UInt64.ONE);
    final Packet pingPacket = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));
    mockPingPacketCreation(otherPeer, pingPacket);

    // Setup ping to be sent to otherPeer2 after neighbors packet is received
    nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localEndpoint), otherPeer2.getEndpoint(), UInt64.ONE);
    final Packet pingPacket2 = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));
    mockPingPacketCreation(otherPeer2, pingPacket2);

    // Denylist peer
    denylist.add(otherPeer);

    final Packet neighborsPacket =
        MockPacketDataFactory.mockNeighborsPacket(discoPeer, otherPeer, otherPeer2);
    controller.onMessage(neighborsPacket, discoPeer);

    verify(controller, times(0)).bond(otherPeer);
    verify(controller, times(1)).bond(otherPeer2);
  }

  @Test
  public void shouldRespondToNeighborsRequestFromKnownPeer() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 1);

    final DiscoveryPeer discoPeer = peers.get(0);

    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final Endpoint localEndpoint = localPeer.getEndpoint();

    // Setup ping to be sent to discoPeer
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localEndpoint), discoPeer.getEndpoint(), UInt64.ONE);
    final Packet discoPeerPing = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));
    mockPingPacketCreation(discoPeer, discoPeerPing);

    controller.start();
    verify(outboundMessageHandler, times(1)).send(any(), matchPacketOfType(PacketType.PING));

    final Packet pongFromDiscoPeer =
        MockPacketDataFactory.mockPongPacket(discoPeer, discoPeerPing.getHash());
    controller.onMessage(pongFromDiscoPeer, discoPeer);

    verify(outboundMessageHandler, times(1))
        .send(eq(discoPeer), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    final Packet findNeighborsPacket = MockPacketDataFactory.mockFindNeighborsPacket(discoPeer);
    controller.onMessage(findNeighborsPacket, discoPeer);

    verify(outboundMessageHandler, times(1))
        .send(eq(discoPeer), matchPacketOfType(PacketType.NEIGHBORS));
  }

  @Test
  public void shouldNotRespondToNeighborsRequestFromUnknownPeer() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 2);

    final DiscoveryPeer discoPeer = peers.get(0);
    final DiscoveryPeer otherPeer = peers.get(1);

    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final Endpoint localEndpoint = localPeer.getEndpoint();

    // Setup ping to be sent to discoPeer
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localEndpoint), discoPeer.getEndpoint(), UInt64.ONE);
    final Packet discoPeerPing = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));
    mockPingPacketCreation(discoPeer, discoPeerPing);

    controller.start();
    verify(outboundMessageHandler, times(1)).send(any(), matchPacketOfType(PacketType.PING));

    final Packet pongFromDiscoPeer =
        MockPacketDataFactory.mockPongPacket(discoPeer, discoPeerPing.getHash());
    controller.onMessage(pongFromDiscoPeer, discoPeer);

    verify(outboundMessageHandler, times(1))
        .send(eq(discoPeer), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    final Packet findNeighborsPacket = MockPacketDataFactory.mockFindNeighborsPacket(discoPeer);
    controller.onMessage(findNeighborsPacket, otherPeer);

    verify(outboundMessageHandler, times(0))
        .send(eq(otherPeer), matchPacketOfType(PacketType.NEIGHBORS));
  }

  @Test
  public void shouldNotRespondToExpiredNeighborsRequest() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 1);

    final DiscoveryPeer discoPeer = peers.get(0);

    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final Endpoint localEndpoint = localPeer.getEndpoint();

    // Setup ping to be sent to discoPeer
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localEndpoint), discoPeer.getEndpoint(), UInt64.ONE);
    final Packet discoPeerPing = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));
    mockPingPacketCreation(discoPeer, discoPeerPing);

    controller.start();
    verify(outboundMessageHandler, times(1)).send(any(), matchPacketOfType(PacketType.PING));

    final Packet pongFromDiscoPeer =
        MockPacketDataFactory.mockPongPacket(discoPeer, discoPeerPing.getHash());
    controller.onMessage(pongFromDiscoPeer, discoPeer);

    verify(outboundMessageHandler, times(1))
        .send(eq(discoPeer), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    final Packet findNeighborsPacket =
        MockPacketDataFactory.mockFindNeighborsPacket(
            discoPeer, Instant.now().getEpochSecond() - PacketData.DEFAULT_EXPIRATION_PERIOD_SEC);
    controller.onMessage(findNeighborsPacket, discoPeer);

    verify(outboundMessageHandler, times(0))
        .send(eq(discoPeer), matchPacketOfType(PacketType.NEIGHBORS));
  }

  @Test
  public void shouldNotRespondToNeighborsRequestFromDenylistedPeer() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 1);

    final DiscoveryPeer discoPeer = peers.get(0);

    final PeerPermissionsDenylist denylist = PeerPermissionsDenylist.create();
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .peerPermissions(denylist)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final Endpoint localEndpoint = localPeer.getEndpoint();

    // Setup ping to be sent to discoPeer
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localEndpoint), discoPeer.getEndpoint(), UInt64.ONE);
    final Packet discoPeerPing = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));
    mockPingPacketCreation(discoPeer, discoPeerPing);

    controller.start();
    verify(outboundMessageHandler, times(1)).send(any(), matchPacketOfType(PacketType.PING));

    final Packet pongFromDiscoPeer =
        MockPacketDataFactory.mockPongPacket(discoPeer, discoPeerPing.getHash());
    controller.onMessage(pongFromDiscoPeer, discoPeer);

    verify(outboundMessageHandler, times(1))
        .send(eq(discoPeer), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    denylist.add(discoPeer);
    final Packet findNeighborsPacket = MockPacketDataFactory.mockFindNeighborsPacket(discoPeer);
    controller.onMessage(findNeighborsPacket, discoPeer);

    verify(outboundMessageHandler, times(0))
        .send(eq(discoPeer), matchPacketOfType(PacketType.NEIGHBORS));
  }

  @Test
  public void shouldAddNewPeerWhenReceivedPongAndPeerTableBucketIsNotFull() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 1);

    // Mock the creation of the PING packet to control hash for PONG.
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localPeer.getEndpoint()), peers.get(0).getEndpoint(), UInt64.ONE);
    final Packet pingPacket = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));

    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers.get(0))
            .outboundMessageHandler(outboundMessageHandler)
            .build();
    mockPingPacketCreation(pingPacket);

    controller.setRetryDelayFunction((prev) -> 999999999L);
    controller.start();

    verify(outboundMessageHandler, times(1)).send(any(), matchPacketOfType(PacketType.PING));

    final Packet pongPacket =
        MockPacketDataFactory.mockPongPacket(peers.get(0), pingPacket.getHash());
    controller.onMessage(pongPacket, peers.get(0));

    assertThat(controller.streamDiscoveredPeers()).contains(peers.get(0));
  }

  @Test
  public void shouldAddNewPeerWhenReceivedPongAndPeerTableBucketIsFull() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 17);

    final List<DiscoveryPeer> bootstrapPeers = peers.subList(0, 16);
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(bootstrapPeers)
            .outboundMessageHandler(outboundMessageHandler)
            .build();
    controller.setRetryDelayFunction(PeerDiscoveryControllerTest::longDelayFunction);

    // Mock the creation of PING packets to control hash PONG packets.
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localPeer.getEndpoint()), peers.get(0).getEndpoint(), UInt64.ONE);
    final Packet pingPacket = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));
    mockPingPacketCreation(pingPacket);

    controller.start();

    verify(outboundMessageHandler, times(16)).send(any(), matchPacketOfType(PacketType.PING));

    for (int i = 0; i <= 14; i++) {
      final Packet pongPacket =
          MockPacketDataFactory.mockPongPacket(peers.get(i), pingPacket.getHash());
      controller.onMessage(pongPacket, peers.get(i));
    }

    verify(outboundMessageHandler, times(0))
        .send(any(), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    final Packet pongPacket15 =
        MockPacketDataFactory.mockPongPacket(peers.get(15), pingPacket.getHash());
    controller.onMessage(pongPacket15, peers.get(15));

    verify(outboundMessageHandler, times(3))
        .send(any(), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    for (int i = 0; i <= 15; i++) {
      final Packet neighborsPacket =
          MockPacketDataFactory.mockNeighborsPacket(peers.get(i), peers.get(16));
      controller.onMessage(neighborsPacket, peers.get(i));
    }

    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(16)), matchPacketOfType(PacketType.PING));

    final Packet pongPacket16 =
        MockPacketDataFactory.mockPongPacket(peers.get(16), pingPacket.getHash());
    controller.onMessage(pongPacket16, peers.get(16));

    assertThat(controller.streamDiscoveredPeers()).contains(peers.get(16));
    assertThat(controller.streamDiscoveredPeers().collect(Collectors.toList())).hasSize(16);
    assertThat(evictedPeerFromBucket(bootstrapPeers, controller)).isTrue();
  }

  private boolean evictedPeerFromBucket(
      final List<DiscoveryPeer> peers, final PeerDiscoveryController controller) {
    for (final DiscoveryPeer peer : peers) {
      if (controller.streamDiscoveredPeers().noneMatch(candidate -> candidate.equals(peer))) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void shouldNotAddPeerInNeighborsPacketWithoutBonding() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 2);

    // Mock the creation of the PING packet to control hash for PONG.
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localPeer.getEndpoint()), peers.get(0).getEndpoint(), UInt64.ONE);
    final Packet pingPacket = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));

    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers.get(0))
            .outboundMessageHandler(outboundMessageHandler)
            .build();
    mockPingPacketCreation(pingPacket);
    controller.start();

    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(0)), matchPacketOfType(PacketType.PING));

    final Packet pongPacket =
        MockPacketDataFactory.mockPongPacket(peers.get(0), pingPacket.getHash());
    controller.onMessage(pongPacket, peers.get(0));

    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(0)), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    assertThat(controller.streamDiscoveredPeers()).doesNotContain(peers.get(1));
  }

  @Test
  public void streamDiscoveredPeers() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 3);
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    final PeerPermissions peerPermissions = mock(PeerPermissions.class);
    doReturn(true).when(peerPermissions).isPermitted(any(), any(), any());

    final DiscoveryPeer localNode =
        DiscoveryPeer.fromEnode(
            EnodeURLImpl.builder()
                .ipAddress("127.0.0.1")
                .nodeId(Peer.randomId())
                .discoveryAndListeningPorts(30303)
                .build());
    localNode.setNodeRecord(
        NodeRecord.fromValues(IdentitySchemaInterpreter.V4, UInt64.ONE, Collections.emptyList()));

    controller =
        getControllerBuilder()
            .localPeer(localNode)
            .peers(peers)
            .outboundMessageHandler(outboundMessageHandler)
            .peerPermissions(peerPermissions)
            .build();
    controller.start();

    assertThat(controller.streamDiscoveredPeers().collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(peers);

    // Disallow peer - it should be filtered from list
    final Peer disallowed = peers.get(0);
    doReturn(false)
        .when(peerPermissions)
        .isPermitted(eq(localNode), eq(disallowed), eq(Action.DISCOVERY_ALLOW_IN_PEER_TABLE));

    // Peer stream should filter disallowed
    assertThat(controller.streamDiscoveredPeers().collect(Collectors.toList()))
        .containsExactlyInAnyOrder(peers.get(1), peers.get(2));
  }

  @Test
  public void updatePermissions_restrictWithList() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 3);
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    final TestPeerPermissions peerPermissions = spy(new TestPeerPermissions());
    doReturn(true).when(peerPermissions).isPermitted(any(), any(), any());

    final DiscoveryPeer localNode =
        DiscoveryPeer.fromEnode(
            EnodeURLImpl.builder()
                .ipAddress("127.0.0.1")
                .nodeId(Peer.randomId())
                .discoveryAndListeningPorts(30303)
                .build());
    localNode.setNodeRecord(
        NodeRecord.fromValues(IdentitySchemaInterpreter.V4, UInt64.ONE, Collections.emptyList()));

    controller =
        getControllerBuilder()
            .localPeer(localNode)
            .peers(peers)
            .outboundMessageHandler(outboundMessageHandler)
            .peerPermissions(peerPermissions)
            .build();
    controller.start();

    assertThat(controller.streamDiscoveredPeers().collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(peers);

    // Disallow peer - it should be filtered from list
    final Peer disallowed = peers.get(0);
    doReturn(false)
        .when(peerPermissions)
        .isPermitted(eq(localNode), eq(disallowed), eq(Action.DISCOVERY_ALLOW_IN_PEER_TABLE));
    peerPermissions.testDispatchUpdate(true, Optional.of(Collections.singletonList(disallowed)));

    // Peer stream should filter disallowed
    assertThat(controller.streamDiscoveredPeers().collect(Collectors.toList()))
        .containsExactlyInAnyOrder(peers.get(1), peers.get(2));

    // Peer should be dropped
    verify(controller, times(1)).dropPeer(any());
    verify(controller, times(1)).dropPeer(disallowed);
  }

  @Test
  public void updatePermissions_restrictWithNoList() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 3);
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    final TestPeerPermissions peerPermissions = spy(new TestPeerPermissions());
    final MockTimerUtil timerUtil = new MockTimerUtil();
    doReturn(true).when(peerPermissions).isPermitted(any(), any(), any());

    final DiscoveryPeer localNode =
        DiscoveryPeer.fromEnode(
            EnodeURLImpl.builder()
                .ipAddress("127.0.0.1")
                .nodeId(Peer.randomId())
                .discoveryAndListeningPorts(30303)
                .build());
    localNode.setNodeRecord(
        NodeRecord.fromValues(IdentitySchemaInterpreter.V4, UInt64.ONE, Collections.emptyList()));

    controller =
        getControllerBuilder()
            .localPeer(localNode)
            .peers(peers)
            .outboundMessageHandler(outboundMessageHandler)
            .peerPermissions(peerPermissions)
            .timerUtil(timerUtil)
            .build();
    controller.start();

    assertThat(controller.streamDiscoveredPeers().collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(peers);

    // Disallow peer - it should be filtered from list
    final Peer disallowed = peers.get(0);
    doReturn(false)
        .when(peerPermissions)
        .isPermitted(eq(localNode), eq(disallowed), eq(Action.DISCOVERY_ALLOW_IN_PEER_TABLE));
    peerPermissions.testDispatchUpdate(true, Optional.empty());
    timerUtil.runHandlers();

    // Peer stream should filter disallowed
    assertThat(controller.streamDiscoveredPeers().collect(Collectors.toList()))
        .containsExactlyInAnyOrder(peers.get(1), peers.get(2));

    // Peer should be dropped
    verify(controller, times(1)).dropPeer(any());
    verify(controller, times(1)).dropPeer(disallowed);
  }

  @Test
  public void updatePermissions_relaxPermissionsWithList() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 3);
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    final TestPeerPermissions peerPermissions = spy(new TestPeerPermissions());
    final MockTimerUtil timerUtil = new MockTimerUtil();
    doReturn(true).when(peerPermissions).isPermitted(any(), any(), any());

    final DiscoveryPeer localNode =
        DiscoveryPeer.fromEnode(
            EnodeURLImpl.builder()
                .ipAddress("127.0.0.1")
                .nodeId(Peer.randomId())
                .discoveryAndListeningPorts(30303)
                .build());
    localNode.setNodeRecord(
        NodeRecord.fromValues(IdentitySchemaInterpreter.V4, UInt64.ONE, Collections.emptyList()));

    controller =
        getControllerBuilder()
            .localPeer(localNode)
            .peers(peers)
            .outboundMessageHandler(outboundMessageHandler)
            .peerPermissions(peerPermissions)
            .timerUtil(timerUtil)
            .build();
    controller.start();

    assertThat(controller.streamDiscoveredPeers().collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(peers);

    final Peer firstPeer = peers.get(0);
    peerPermissions.testDispatchUpdate(false, Optional.of(Collections.singletonList(firstPeer)));
    timerUtil.runHandlers();

    assertThat(controller.streamDiscoveredPeers().collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(peers);
    verify(controller, never()).dropPeer(any());
  }

  @Test
  public void updatePermissions_relaxPermissionsWithNoList() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 3);
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    final TestPeerPermissions peerPermissions = spy(new TestPeerPermissions());
    final MockTimerUtil timerUtil = new MockTimerUtil();
    doReturn(true).when(peerPermissions).isPermitted(any(), any(), any());

    final DiscoveryPeer localNode =
        DiscoveryPeer.fromEnode(
            EnodeURLImpl.builder()
                .ipAddress("127.0.0.1")
                .nodeId(Peer.randomId())
                .discoveryAndListeningPorts(30303)
                .build());
    localNode.setNodeRecord(
        NodeRecord.fromValues(IdentitySchemaInterpreter.V4, UInt64.ONE, Collections.emptyList()));

    controller =
        getControllerBuilder()
            .localPeer(localNode)
            .peers(peers)
            .outboundMessageHandler(outboundMessageHandler)
            .peerPermissions(peerPermissions)
            .timerUtil(timerUtil)
            .build();
    controller.start();

    assertThat(controller.streamDiscoveredPeers().collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(peers);

    peerPermissions.testDispatchUpdate(false, Optional.empty());
    timerUtil.runHandlers();

    assertThat(controller.streamDiscoveredPeers().collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(peers);
    verify(controller, never()).dropPeer(any());
  }

  @Test
  public void shouldRespondToENRRequest() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 1);

    // Mock the creation of the PING packet to control hash for PONG.
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(localPeer.getEndpoint()), peers.get(0).getEndpoint(), UInt64.ONE);
    final Packet pingPacket = Packet.create(PacketType.PING, pingPacketData, nodeKeys.get(0));

    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers.get(0))
            .outboundMessageHandler(outboundMessageHandler)
            .build();
    mockPingPacketCreation(pingPacket);
    controller.start();
    verify(outboundMessageHandler, times(1)).send(any(), matchPacketOfType(PacketType.PING));

    final Packet pongPacket =
        MockPacketDataFactory.mockPongPacket(peers.get(0), pingPacket.getHash());
    controller.onMessage(pongPacket, peers.get(0));
    assertThat(controller.streamDiscoveredPeers())
        .filteredOn(p -> p.getStatus() == PeerDiscoveryStatus.BONDED)
        .contains(peers.get(0));

    final ENRRequestPacketData enrRequestPacketData = ENRRequestPacketData.create();
    final Packet enrRequestPacket =
        Packet.create(PacketType.ENR_REQUEST, enrRequestPacketData, nodeKeys.get(0));
    controller.onMessage(enrRequestPacket, peers.get(0));
    verify(outboundMessageHandler, times(1))
        .send(any(), matchPacketOfType(PacketType.FIND_NEIGHBORS));
    verify(outboundMessageHandler, times(1))
        .send(any(), matchPacketOfType(PacketType.ENR_RESPONSE));
  }

  @Test
  public void shouldNotRespondToENRRequestForNonBondedPeer() {
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(1);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(nodeKeys);
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers.get(0))
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final ENRRequestPacketData enrRequestPacketData = ENRRequestPacketData.create();
    final Packet packet =
        Packet.create(PacketType.ENR_REQUEST, enrRequestPacketData, nodeKeys.get(0));

    controller.onMessage(packet, peers.get(0));

    assertThat(controller.streamDiscoveredPeers())
        .filteredOn(p -> p.getStatus() == PeerDiscoveryStatus.BONDED)
        .hasSize(0);
    verify(outboundMessageHandler, times(0))
        .send(eq(peers.get(0)), matchPacketOfType(PacketType.ENR_REQUEST));
  }

  private static Packet mockPingPacket(final DiscoveryPeer from, final DiscoveryPeer to) {
    final Packet packet = mock(Packet.class);

    final PingPacketData pingPacketData =
        PingPacketData.create(
            Optional.ofNullable(from.getEndpoint()), to.getEndpoint(), UInt64.ONE);
    when(packet.getPacketData(any())).thenReturn(Optional.of(pingPacketData));
    final Bytes id = from.getId();
    when(packet.getNodeId()).thenReturn(id);
    when(packet.getType()).thenReturn(PacketType.PING);
    when(packet.getHash()).thenReturn(Bytes32.ZERO);

    return packet;
  }

  private List<DiscoveryPeer> createPeersInLastBucket(final Peer host, final int n) {
    final List<DiscoveryPeer> newPeers = new ArrayList<>(n);

    // Flipping the most significant bit of the keccak256 will place the peer
    // in the last bucket for the corresponding host peer.
    final Bytes32 keccak256 = host.keccak256();
    final MutableBytes template = MutableBytes.create(keccak256.size());
    byte msb = keccak256.get(0);
    msb ^= MOST_SIGNIFICANT_BIT_MASK;
    template.set(0, msb);

    for (int i = 0; i < n; i++) {
      template.setInt(template.size() - 4, i);
      final Bytes32 keccak = Bytes32.leftPad(template.copy());
      final MutableBytes id = MutableBytes.create(64);
      UInt256.valueOf(i).copyTo(id, id.size() - Bytes32.SIZE);
      final DiscoveryPeer peer =
          spy(
              DiscoveryPeer.fromEnode(
                  EnodeURLImpl.builder()
                      .nodeId(id)
                      .ipAddress("127.0.0.1")
                      .discoveryAndListeningPorts(100 + counter.incrementAndGet())
                      .build()));

      doReturn(keccak).when(peer).keccak256();
      newPeers.add(peer);
    }

    return newPeers;
  }

  private void startPeerDiscoveryController(final DiscoveryPeer... bootstrapPeers) {
    startPeerDiscoveryController(PeerDiscoveryControllerTest::longDelayFunction, bootstrapPeers);
  }

  private void startPeerDiscoveryController(
      final RetryDelayFunction retryDelayFunction, final DiscoveryPeer... bootstrapPeers) {
    // Create the controller.
    controller = getControllerBuilder().peers(bootstrapPeers).build();
    controller.setRetryDelayFunction(retryDelayFunction);
    controller.start();
  }

  static class ControllerBuilder {
    private Collection<DiscoveryPeer> discoPeers = Collections.emptyList();
    private MockTimerUtil timerUtil = new MockTimerUtil();
    private NodeKey nodeKey;
    private DiscoveryPeer localPeer;
    private PeerTable peerTable;
    private OutboundMessageHandler outboundMessageHandler = OutboundMessageHandler.NOOP;
    private static final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();
    private final Subscribers<PeerBondedObserver> peerBondedObservers = Subscribers.create();
    private PeerPermissions peerPermissions = PeerPermissions.noop();

    public static ControllerBuilder create() {
      return new ControllerBuilder();
    }

    ControllerBuilder peers(final Collection<DiscoveryPeer> discoPeers) {
      this.discoPeers = discoPeers;
      return this;
    }

    ControllerBuilder peers(final DiscoveryPeer... discoPeers) {
      this.discoPeers = Arrays.asList(discoPeers);
      return this;
    }

    ControllerBuilder peerPermissions(final PeerPermissions peerPermissions) {
      this.peerPermissions = peerPermissions;
      return this;
    }

    ControllerBuilder timerUtil(final MockTimerUtil timerUtil) {
      this.timerUtil = timerUtil;
      return this;
    }

    ControllerBuilder nodeKey(final NodeKey nodeKey) {
      this.nodeKey = nodeKey;
      return this;
    }

    ControllerBuilder localPeer(final DiscoveryPeer localPeer) {
      this.localPeer = localPeer;
      return this;
    }

    ControllerBuilder peerTable(final PeerTable peerTable) {
      this.peerTable = peerTable;
      return this;
    }

    ControllerBuilder outboundMessageHandler(final OutboundMessageHandler outboundMessageHandler) {
      this.outboundMessageHandler = outboundMessageHandler;
      return this;
    }

    PeerDiscoveryController build() {
      checkNotNull(nodeKey);
      if (localPeer == null) {
        localPeer = helper.createDiscoveryPeer(nodeKey);
      }
      if (peerTable == null) {
        peerTable = new PeerTable(localPeer.getId());
      }
      return spy(
          PeerDiscoveryController.builder()
              .nodeKey(nodeKey)
              .localPeer(localPeer)
              .peerTable(peerTable)
              .bootstrapNodes(discoPeers)
              .outboundMessageHandler(outboundMessageHandler)
              .timerUtil(timerUtil)
              .workerExecutor(new BlockingAsyncExecutor())
              .tableRefreshIntervalMs(TABLE_REFRESH_INTERVAL_MS)
              .peerRequirement(PEER_REQUIREMENT)
              .peerPermissions(peerPermissions)
              .peerBondedObservers(peerBondedObservers)
              .metricsSystem(new NoOpMetricsSystem())
              .build());
    }
  }

  private static class TestPeerPermissions extends PeerPermissions {

    @Override
    public boolean isPermitted(final Peer localNode, final Peer remotePeer, final Action action) {
      return true;
    }

    void testDispatchUpdate(
        final boolean permissionsRestricted, final Optional<List<Peer>> affectedPeers) {
      this.dispatchUpdate(permissionsRestricted, affectedPeers);
    }
  }
}
