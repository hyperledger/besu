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
package tech.pegasys.pantheon.ethereum.p2p.discovery.internal;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryStatus;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryTestHelper;
import tech.pegasys.pantheon.ethereum.p2p.peers.Endpoint;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import tech.pegasys.pantheon.ethereum.p2p.permissioning.NodeWhitelistController;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.MutableBytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;
import tech.pegasys.pantheon.util.uint.UInt256Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class PeerDiscoveryControllerTest {

  private static final byte MOST_SIGNFICANT_BIT_MASK = -128;
  private static final RetryDelayFunction LONG_DELAY_FUNCTION = (prev) -> 999999999L;
  private static final RetryDelayFunction SHORT_DELAY_FUNCTION = (prev) -> Math.max(100, prev * 2);
  private static final PeerRequirement PEER_REQUIREMENT = () -> true;
  private static final long TABLE_REFRESH_INTERVAL_MS = TimeUnit.HOURS.toMillis(1);
  private PeerDiscoveryController controller;
  private DiscoveryPeer localPeer;
  private PeerTable peerTable;
  private KeyPair localKeyPair;
  private final AtomicInteger counter = new AtomicInteger(1);
  private final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();

  @Before
  public void initializeMocks() {
    final List<KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    localKeyPair = keyPairs.get(0);
    localPeer = helper.createDiscoveryPeer(localKeyPair);
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
    int peerCount = 3;
    final List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(peerCount);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(keyPairs);

    MockTimerUtil timer = spy(new MockTimerUtil());
    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers)
            .timerUtil(timer)
            .outboundMessageHandler(outboundMessageHandler)
            .build();
    controller.setRetryDelayFunction(SHORT_DELAY_FUNCTION);

    // Mock the creation of the PING packet, so that we can control the hash,
    // which gets validated when receiving the PONG.
    final PingPacketData mockPing =
        PingPacketData.create(localPeer.getEndpoint(), peers.get(0).getEndpoint());
    final Packet mockPacket = Packet.create(PacketType.PING, mockPing, keyPairs.get(0));
    doReturn(mockPacket).when(controller).createPacket(eq(PacketType.PING), any());

    controller.start();

    int timeouts = 4;
    for (int i = 0; i < timeouts; i++) {
      timer.runTimerHandlers();
    }
    int expectedTimerEvents = (timeouts + 1) * peerCount;
    verify(timer, times(expectedTimerEvents)).setTimer(anyLong(), any());

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
        .getPeers()
        .forEach(p -> assertThat(p.getStatus()).isEqualTo(PeerDiscoveryStatus.BONDING));
  }

  @Test
  public void bootstrapPeersRetriesStoppedUponResponse() {
    // Create peers.
    final List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(3);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(keyPairs);

    MockTimerUtil timer = new MockTimerUtil();
    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers)
            .timerUtil(timer)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    // Mock the creation of the PING packet, so that we can control the hash,
    // which gets validated when receiving the PONG.
    final PingPacketData mockPing =
        PingPacketData.create(localPeer.getEndpoint(), peers.get(0).getEndpoint());
    final Packet mockPacket = Packet.create(PacketType.PING, mockPing, keyPairs.get(0));
    doReturn(mockPacket).when(controller).createPacket(eq(PacketType.PING), any());

    controller.start();

    // Invoke timers several times so that ping to peers should be resent
    for (int i = 0; i < 3; i++) {
      timer.runTimerHandlers();
    }

    // Assert PING packet was sent for peer[0] 4 times.
    for (DiscoveryPeer peer : peers) {
      verify(outboundMessageHandler, times(4)).send(eq(peer), matchPacketOfType(PacketType.PING));
    }

    // Simulate a PONG message from peer 0.
    final PongPacketData packetData =
        PongPacketData.create(localPeer.getEndpoint(), mockPacket.getHash());
    final Packet packet = Packet.create(PacketType.PONG, packetData, keyPairs.get(0));
    controller.onMessage(packet, peers.get(0));

    // Invoke timers again
    for (int i = 0; i < 4; i++) {
      timer.runTimerHandlers();
    }

    // Ensure we receive no more PING packets for peer[0].
    // Assert PING packet was sent for peer[0] 4 times.
    for (DiscoveryPeer peer : peers) {
      int expectedCount = peer.equals(peers.get(0)) ? 4 : 8;
      verify(outboundMessageHandler, times(expectedCount))
          .send(eq(peer), matchPacketOfType(PacketType.PING));
    }
  }

  @Test
  public void bootstrapPeersPongReceived_HashMatched() {
    // Create peers.
    final List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(3);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(keyPairs);

    MockTimerUtil timer = new MockTimerUtil();
    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers)
            .timerUtil(timer)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    // Mock the creation of the PING packet, so that we can control the hash, which gets validated
    // when receiving the PONG.
    final PingPacketData mockPing =
        PingPacketData.create(localPeer.getEndpoint(), peers.get(0).getEndpoint());
    final Packet mockPacket = Packet.create(PacketType.PING, mockPing, keyPairs.get(0));
    doReturn(mockPacket).when(controller).createPacket(eq(PacketType.PING), any());

    controller.start();

    assertThat(
            controller
                .getPeers()
                .stream()
                .filter(p -> p.getStatus() == PeerDiscoveryStatus.BONDING))
        .hasSize(3);

    // Simulate a PONG message from peer 0.
    final PongPacketData packetData =
        PongPacketData.create(localPeer.getEndpoint(), mockPacket.getHash());
    final Packet packet = Packet.create(PacketType.PONG, packetData, keyPairs.get(0));
    controller.onMessage(packet, peers.get(0));

    // Ensure that the peer controller is now sending FIND_NEIGHBORS messages for this peer.
    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(0)), matchPacketOfType(PacketType.FIND_NEIGHBORS));
    // Invoke timeouts and check that we resent our neighbors request
    timer.runTimerHandlers();
    verify(outboundMessageHandler, times(2))
        .send(eq(peers.get(0)), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    assertThat(
            controller
                .getPeers()
                .stream()
                .filter(p -> p.getStatus() == PeerDiscoveryStatus.BONDING))
        .hasSize(2);
    assertThat(
            controller.getPeers().stream().filter(p -> p.getStatus() == PeerDiscoveryStatus.BONDED))
        .hasSize(1);
  }

  @Test
  public void bootstrapPeersPongReceived_HashUnmatched() {
    // Create peers.
    final List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(3);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(keyPairs);

    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder().peers(peers).outboundMessageHandler(outboundMessageHandler).build();
    controller.setRetryDelayFunction(LONG_DELAY_FUNCTION);

    // Mock the creation of the PING packet, so that we can control the hash, which gets validated
    // when
    // processing the PONG.
    final PingPacketData mockPing =
        PingPacketData.create(localPeer.getEndpoint(), peers.get(0).getEndpoint());
    final Packet mockPacket = Packet.create(PacketType.PING, mockPing, keyPairs.get(0));
    doReturn(mockPacket).when(controller).createPacket(eq(PacketType.PING), any());

    controller.start();

    assertThat(
            controller
                .getPeers()
                .stream()
                .filter(p -> p.getStatus() == PeerDiscoveryStatus.BONDING))
        .hasSize(3);

    // Send a PONG packet from peer 1, with an incorrect hash.
    final PongPacketData packetData =
        PongPacketData.create(localPeer.getEndpoint(), BytesValue.fromHexString("1212"));
    final Packet packet = Packet.create(PacketType.PONG, packetData, keyPairs.get(1));
    controller.onMessage(packet, peers.get(1));

    // No FIND_NEIGHBORS packet was sent for peer 1.
    verify(outboundMessageHandler, never())
        .send(eq(peers.get(1)), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    assertThat(
            controller
                .getPeers()
                .stream()
                .filter(p -> p.getStatus() == PeerDiscoveryStatus.BONDING))
        .hasSize(3);
  }

  @Test
  public void findNeighborsSentAfterBondingFinished() {
    // Create three peers, out of which the first two are bootstrap peers.
    final List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(keyPairs);

    // Initialize the peer controller, setting a high controller refresh interval and a high timeout
    // threshold,
    // to avoid retries getting in the way of this test.
    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers.get(0))
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    // Mock the creation of the PING packet, so that we can control the hash, which gets validated
    // when
    // processing the PONG.
    final PingPacketData mockPing =
        PingPacketData.create(localPeer.getEndpoint(), peers.get(0).getEndpoint());
    final Packet mockPacket = Packet.create(PacketType.PING, mockPing, keyPairs.get(0));
    doReturn(mockPacket).when(controller).createPacket(eq(PacketType.PING), any());
    controller.setRetryDelayFunction((prev) -> 999999999L);
    controller.start();

    // Verify that the PING was sent.
    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(0)), matchPacketOfType(PacketType.PING));

    // Simulate a PONG message from peer[0].
    final PongPacketData packetData =
        PongPacketData.create(localPeer.getEndpoint(), mockPacket.getHash());
    final Packet pongPacket = Packet.create(PacketType.PONG, packetData, keyPairs.get(0));
    controller.onMessage(pongPacket, peers.get(0));

    // Verify that the FIND_NEIGHBORS packet was sent with target == localPeer.
    final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
    verify(outboundMessageHandler, atLeast(1)).send(eq(peers.get(0)), captor.capture());
    List<Packet> neighborsPackets =
        captor
            .getAllValues()
            .stream()
            .filter(p -> p.getType().equals(PacketType.FIND_NEIGHBORS))
            .collect(Collectors.toList());
    assertThat(neighborsPackets.size()).isEqualTo(1);
    Packet nieghborsPacket = neighborsPackets.get(0);
    final Optional<FindNeighborsPacketData> maybeData =
        nieghborsPacket.getPacketData(FindNeighborsPacketData.class);
    assertThat(maybeData).isPresent();
    final FindNeighborsPacketData data = maybeData.get();
    assertThat(data.getTarget()).isEqualTo(localPeer.getId());

    assertThat(controller.getPeers()).hasSize(1);
    assertThat(controller.getPeers().stream().findFirst().get().getStatus())
        .isEqualTo(PeerDiscoveryStatus.BONDED);
  }

  private ControllerBuilder getControllerBuilder() {
    return ControllerBuilder.create()
        .keyPair(localKeyPair)
        .localPeer(localPeer)
        .peerTable(peerTable);
  }

  @Test
  public void peerSeenTwice() throws InterruptedException {
    // Create three peers, out of which the first two are bootstrap peers.
    final List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(3);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(keyPairs);

    // Mock the creation of the PING packet, so that we can control the hash, which gets validated
    // when
    // processing the PONG.
    final PingPacketData mockPing =
        PingPacketData.create(localPeer.getEndpoint(), peers.get(0).getEndpoint());
    final Packet mockPacket = Packet.create(PacketType.PING, mockPing, keyPairs.get(0));

    // Initialize the peer controller
    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers.get(0), peers.get(1))
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    doReturn(mockPacket).when(controller).createPacket(eq(PacketType.PING), any());

    controller.setRetryDelayFunction((prev) -> 999999999L);
    controller.start();

    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(0)), matchPacketOfType(PacketType.PING));
    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(1)), matchPacketOfType(PacketType.PING));

    // Simulate a PONG message from peer[0].
    final PongPacketData packetData =
        PongPacketData.create(localPeer.getEndpoint(), mockPacket.getHash());
    Packet pongPacket = Packet.create(PacketType.PONG, packetData, keyPairs.get(0));
    controller.onMessage(pongPacket, peers.get(0));

    // Simulate a NEIGHBORS message from peer[0] listing peer[2].
    final NeighborsPacketData neighbors =
        NeighborsPacketData.create(Collections.singletonList(peers.get(2)));
    Packet neighborsPacket = Packet.create(PacketType.NEIGHBORS, neighbors, keyPairs.get(0));
    controller.onMessage(neighborsPacket, peers.get(0));

    // Assert that we're bonding with the third peer.
    assertThat(controller.getPeers()).hasSize(2);
    assertThat(controller.getPeers())
        .filteredOn(p -> p.getStatus() == PeerDiscoveryStatus.BONDING)
        .hasSize(1);
    assertThat(controller.getPeers())
        .filteredOn(p -> p.getStatus() == PeerDiscoveryStatus.BONDED)
        .hasSize(1);

    // Send a PONG packet from peer[2], to transition it to the BONDED state.
    pongPacket = Packet.create(PacketType.PONG, packetData, keyPairs.get(2));
    controller.onMessage(pongPacket, peers.get(2));

    // Assert we're now bonded with peer[2].
    assertThat(controller.getPeers())
        .filteredOn(p -> p.equals(peers.get(2)) && p.getStatus() == PeerDiscoveryStatus.BONDED)
        .hasSize(1);

    // Simulate bonding and neighbors packet from the second boostrap peer, with peer[2] reported in
    // the peer list.
    pongPacket = Packet.create(PacketType.PONG, packetData, keyPairs.get(1));
    controller.onMessage(pongPacket, peers.get(1));
    neighborsPacket = Packet.create(PacketType.NEIGHBORS, neighbors, keyPairs.get(1));
    controller.onMessage(neighborsPacket, peers.get(1));

    // Wait for 1 second and ensure that only 1 PING was ever sent to peer[2].
    Thread.sleep(1000);
    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(2)), matchPacketOfType(PacketType.PING));
  }

  @Test(expected = IllegalStateException.class)
  public void startTwice() {
    startPeerDiscoveryController();
    controller.start();
  }

  @Test
  public void stopTwice() {
    startPeerDiscoveryController();
    controller.stop();
    controller.stop();
    // no exception
  }

  @Test
  public void shouldAddNewPeerWhenReceivedPingAndPeerTableBucketIsNotFull() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 1);
    startPeerDiscoveryController();

    final Packet pingPacket = mockPingPacket(peers.get(0), localPeer);
    controller.onMessage(pingPacket, peers.get(0));
    assertThat(controller.getPeers()).contains(peers.get(0));
  }

  @Test
  public void shouldNotAddSelfWhenReceivedPingFromSelf() {
    startPeerDiscoveryController();
    final DiscoveryPeer localPeer =
        new DiscoveryPeer(this.localPeer.getId(), this.localPeer.getEndpoint());

    final Packet pingPacket = mockPingPacket(this.localPeer, this.localPeer);
    controller.onMessage(pingPacket, localPeer);

    assertThat(controller.getPeers()).doesNotContain(localPeer);
  }

  @Test
  public void shouldAddNewPeerWhenReceivedPingAndPeerTableBucketIsFull() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 17);
    startPeerDiscoveryController();
    // Fill the last bucket.
    for (int i = 0; i < 16; i++) {
      peerTable.tryAdd(peers.get(i));
    }

    final Packet pingPacket = mockPingPacket(peers.get(16), localPeer);
    controller.onMessage(pingPacket, peers.get(16));

    assertThat(controller.getPeers()).contains(peers.get(16));
    // The first peer added should have been evicted.
    assertThat(controller.getPeers()).doesNotContain(peers.get(0));
  }

  @Test
  public void shouldNotRemoveExistingPeerWhenReceivedPing() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 1);
    startPeerDiscoveryController();

    peerTable.tryAdd(peers.get(0));
    assertThat(controller.getPeers()).contains(peers.get(0));

    final Packet pingPacket = mockPingPacket(peers.get(0), localPeer);
    controller.onMessage(pingPacket, peers.get(0));

    assertThat(controller.getPeers()).contains(peers.get(0));
  }

  @Test
  public void shouldNotAddNewPeerWhenReceivedPongFromBlacklistedPeer() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 3);
    final DiscoveryPeer discoPeer = peers.get(0);
    final DiscoveryPeer otherPeer = peers.get(1);
    final DiscoveryPeer otherPeer2 = peers.get(2);

    final PeerBlacklist blacklist = new PeerBlacklist();
    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .blacklist(blacklist)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final Endpoint localEndpoint = localPeer.getEndpoint();

    // Setup ping to be sent to discoPeer
    List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    PingPacketData pingPacketData = PingPacketData.create(localEndpoint, discoPeer.getEndpoint());
    final Packet discoPeerPing = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));
    doReturn(discoPeerPing)
        .when(controller)
        .createPacket(eq(PacketType.PING), matchPingDataForPeer(discoPeer));

    controller.start();
    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(0)), matchPacketOfType(PacketType.PING));

    final Packet pongFromDiscoPeer =
        MockPacketDataFactory.mockPongPacket(discoPeer, discoPeerPing.getHash());
    controller.onMessage(pongFromDiscoPeer, discoPeer);

    verify(outboundMessageHandler, times(1))
        .send(eq(discoPeer), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    // Setup ping to be sent to otherPeer after neighbors packet is received
    keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    pingPacketData = PingPacketData.create(localEndpoint, otherPeer.getEndpoint());
    final Packet pingPacket = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));
    doReturn(pingPacket)
        .when(controller)
        .createPacket(eq(PacketType.PING), matchPingDataForPeer(otherPeer));

    // Setup ping to be sent to otherPeer2 after neighbors packet is received
    keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    pingPacketData = PingPacketData.create(localEndpoint, otherPeer2.getEndpoint());
    final Packet pingPacket2 = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));
    doReturn(pingPacket2)
        .when(controller)
        .createPacket(eq(PacketType.PING), matchPingDataForPeer(otherPeer2));

    final Packet neighborsPacket =
        MockPacketDataFactory.mockNeighborsPacket(discoPeer, otherPeer, otherPeer2);
    controller.onMessage(neighborsPacket, discoPeer);

    verify(outboundMessageHandler, times(peers.size()))
        .send(any(), matchPacketOfType(PacketType.PING));

    final Packet pongPacket = MockPacketDataFactory.mockPongPacket(otherPeer, pingPacket.getHash());
    controller.onMessage(pongPacket, otherPeer);

    // Blacklist otherPeer2 before sending return pong
    blacklist.add(otherPeer2);
    final Packet pongPacket2 =
        MockPacketDataFactory.mockPongPacket(otherPeer2, pingPacket2.getHash());
    controller.onMessage(pongPacket2, otherPeer2);

    assertThat(controller.getPeers()).hasSize(2);
    assertThat(controller.getPeers()).contains(discoPeer);
    assertThat(controller.getPeers()).contains(otherPeer);
    assertThat(controller.getPeers()).doesNotContain(otherPeer2);
  }

  private PacketData matchPingDataForPeer(final DiscoveryPeer peer) {
    return argThat((PacketData data) -> ((PingPacketData) data).getTo().equals(peer.getEndpoint()));
  }

  private Packet matchPacketOfType(final PacketType type) {
    return argThat((Packet packet) -> packet.getType().equals(type));
  }

  @Test
  public void shouldNotBondWithBlacklistedPeer() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 3);
    final DiscoveryPeer discoPeer = peers.get(0);
    final DiscoveryPeer otherPeer = peers.get(1);
    final DiscoveryPeer otherPeer2 = peers.get(2);

    final PeerBlacklist blacklist = new PeerBlacklist();
    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .blacklist(blacklist)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final Endpoint localEndpoint = localPeer.getEndpoint();

    // Setup ping to be sent to discoPeer
    List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    PingPacketData pingPacketData = PingPacketData.create(localEndpoint, discoPeer.getEndpoint());
    final Packet discoPeerPing = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));
    doReturn(discoPeerPing)
        .when(controller)
        .createPacket(eq(PacketType.PING), matchPingDataForPeer(discoPeer));

    controller.start();
    verify(outboundMessageHandler, times(1)).send(any(), matchPacketOfType(PacketType.PING));

    final Packet pongFromDiscoPeer =
        MockPacketDataFactory.mockPongPacket(discoPeer, discoPeerPing.getHash());
    controller.onMessage(pongFromDiscoPeer, discoPeer);

    verify(outboundMessageHandler, times(1))
        .send(eq(discoPeer), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    // Setup ping to be sent to otherPeer after neighbors packet is received
    keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    pingPacketData = PingPacketData.create(localEndpoint, otherPeer.getEndpoint());
    final Packet pingPacket = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));
    doReturn(pingPacket)
        .when(controller)
        .createPacket(eq(PacketType.PING), matchPingDataForPeer(otherPeer));

    // Setup ping to be sent to otherPeer2 after neighbors packet is received
    keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    pingPacketData = PingPacketData.create(localEndpoint, otherPeer2.getEndpoint());
    final Packet pingPacket2 = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));
    doReturn(pingPacket2)
        .when(controller)
        .createPacket(eq(PacketType.PING), matchPingDataForPeer(otherPeer2));

    // Blacklist peer
    blacklist.add(otherPeer);

    final Packet neighborsPacket =
        MockPacketDataFactory.mockNeighborsPacket(discoPeer, otherPeer, otherPeer2);
    controller.onMessage(neighborsPacket, discoPeer);

    verify(controller, times(0)).bond(otherPeer, false);
    verify(controller, times(1)).bond(otherPeer2, false);
  }

  @Test
  public void shouldRespondToNeighborsRequestFromKnownPeer()
      throws InterruptedException, ExecutionException, TimeoutException {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 1);
    final DiscoveryPeer discoPeer = peers.get(0);

    final PeerBlacklist blacklist = new PeerBlacklist();
    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .blacklist(blacklist)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final Endpoint localEndpoint = localPeer.getEndpoint();

    // Setup ping to be sent to discoPeer
    final List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(localEndpoint, discoPeer.getEndpoint());
    final Packet discoPeerPing = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));
    doReturn(discoPeerPing)
        .when(controller)
        .createPacket(eq(PacketType.PING), matchPingDataForPeer(discoPeer));

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
  public void shouldNotRespondToNeighborsRequestFromUnknownPeer()
      throws InterruptedException, ExecutionException, TimeoutException {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 2);
    final DiscoveryPeer discoPeer = peers.get(0);
    final DiscoveryPeer otherPeer = peers.get(1);

    final PeerBlacklist blacklist = new PeerBlacklist();
    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .blacklist(blacklist)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final Endpoint localEndpoint = localPeer.getEndpoint();

    // Setup ping to be sent to discoPeer
    final List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(localEndpoint, discoPeer.getEndpoint());
    final Packet discoPeerPing = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));
    doReturn(discoPeerPing)
        .when(controller)
        .createPacket(eq(PacketType.PING), matchPingDataForPeer(discoPeer));

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
  public void shouldNotRespondToNeighborsRequestFromBlacklistedPeer() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 1);
    final DiscoveryPeer discoPeer = peers.get(0);

    final PeerBlacklist blacklist = new PeerBlacklist();
    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .blacklist(blacklist)
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final Endpoint localEndpoint = localPeer.getEndpoint();

    // Setup ping to be sent to discoPeer
    final List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(localEndpoint, discoPeer.getEndpoint());
    final Packet discoPeerPing = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));
    doReturn(discoPeerPing)
        .when(controller)
        .createPacket(eq(PacketType.PING), matchPingDataForPeer(discoPeer));

    controller.start();
    verify(outboundMessageHandler, times(1)).send(any(), matchPacketOfType(PacketType.PING));

    final Packet pongFromDiscoPeer =
        MockPacketDataFactory.mockPongPacket(discoPeer, discoPeerPing.getHash());
    controller.onMessage(pongFromDiscoPeer, discoPeer);

    verify(outboundMessageHandler, times(1))
        .send(eq(discoPeer), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    blacklist.add(discoPeer);
    final Packet findNeighborsPacket = MockPacketDataFactory.mockFindNeighborsPacket(discoPeer);
    controller.onMessage(findNeighborsPacket, discoPeer);

    verify(outboundMessageHandler, times(0))
        .send(eq(discoPeer), matchPacketOfType(PacketType.NEIGHBORS));
  }

  @Test
  public void shouldAddNewPeerWhenReceivedPongAndPeerTableBucketIsNotFull() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 1);

    // Mock the creation of the PING packet to control hash for PONG.
    final List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(localPeer.getEndpoint(), peers.get(0).getEndpoint());
    final Packet pingPacket = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));

    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers.get(0))
            .outboundMessageHandler(outboundMessageHandler)
            .build();
    doReturn(pingPacket).when(controller).createPacket(eq(PacketType.PING), any());

    controller.setRetryDelayFunction((prev) -> 999999999L);
    controller.start();

    verify(outboundMessageHandler, times(1)).send(any(), matchPacketOfType(PacketType.PING));

    final Packet pongPacket =
        MockPacketDataFactory.mockPongPacket(peers.get(0), pingPacket.getHash());
    controller.onMessage(pongPacket, peers.get(0));

    assertThat(controller.getPeers()).contains(peers.get(0));
  }

  @Test
  public void shouldAddNewPeerWhenReceivedPongAndPeerTableBucketIsFull() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 17);

    final List<DiscoveryPeer> bootstrapPeers = peers.subList(0, 16);
    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(bootstrapPeers)
            .outboundMessageHandler(outboundMessageHandler)
            .build();
    controller.setRetryDelayFunction(LONG_DELAY_FUNCTION);

    // Mock the creation of PING packets to control hash PONG packets.
    final List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(localPeer.getEndpoint(), peers.get(0).getEndpoint());
    final Packet pingPacket = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));
    doReturn(pingPacket).when(controller).createPacket(eq(PacketType.PING), any());

    controller.start();

    verify(outboundMessageHandler, times(16)).send(any(), matchPacketOfType(PacketType.PING));

    final Packet pongPacket =
        MockPacketDataFactory.mockPongPacket(peers.get(0), pingPacket.getHash());
    controller.onMessage(pongPacket, peers.get(0));

    final Packet neighborsPacket =
        MockPacketDataFactory.mockNeighborsPacket(peers.get(0), peers.get(16));
    controller.onMessage(neighborsPacket, peers.get(0));

    final Packet pongPacket2 =
        MockPacketDataFactory.mockPongPacket(peers.get(16), pingPacket.getHash());
    controller.onMessage(pongPacket2, peers.get(16));

    assertThat(controller.getPeers()).contains(peers.get(16));
    // Explain
    assertThat(controller.getPeers()).doesNotContain(peers.get(1));
  }

  @Test
  public void shouldNotAddPeerInNeighborsPacketWithoutBonding() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 2);

    // Mock the creation of the PING packet to control hash for PONG.
    final List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    final PingPacketData pingPacketData =
        PingPacketData.create(localPeer.getEndpoint(), peers.get(0).getEndpoint());
    final Packet pingPacket = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));

    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(peers.get(0))
            .outboundMessageHandler(outboundMessageHandler)
            .build();
    doReturn(pingPacket).when(controller).createPacket(eq(PacketType.PING), any());
    controller.start();

    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(0)), matchPacketOfType(PacketType.PING));

    final Packet pongPacket =
        MockPacketDataFactory.mockPongPacket(peers.get(0), pingPacket.getHash());
    controller.onMessage(pongPacket, peers.get(0));

    verify(outboundMessageHandler, times(1))
        .send(eq(peers.get(0)), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    assertThat(controller.getPeers()).doesNotContain(peers.get(1));
  }

  @Test
  public void shouldNotBondWithNonWhitelistedPeer() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 3);
    final DiscoveryPeer discoPeer = peers.get(0);
    final DiscoveryPeer otherPeer = peers.get(1);
    final DiscoveryPeer otherPeer2 = peers.get(2);

    final PeerBlacklist blacklist = new PeerBlacklist();
    final PermissioningConfiguration config = new PermissioningConfiguration();
    NodeWhitelistController nodeWhitelistController = new NodeWhitelistController(config);

    // Whitelist peers
    nodeWhitelistController.addNode(discoPeer);
    nodeWhitelistController.addNode(otherPeer2);

    OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .blacklist(blacklist)
            .whitelist(Optional.of(nodeWhitelistController))
            .outboundMessageHandler(outboundMessageHandler)
            .build();

    final Endpoint localEndpoint = localPeer.getEndpoint();

    // Setup ping to be sent to discoPeer
    List<SECP256K1.KeyPair> keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    PingPacketData pingPacketData = PingPacketData.create(localEndpoint, discoPeer.getEndpoint());
    final Packet discoPeerPing = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));
    doReturn(discoPeerPing)
        .when(controller)
        .createPacket(eq(PacketType.PING), matchPingDataForPeer(discoPeer));

    controller.start();
    verify(outboundMessageHandler, times(1)).send(any(), matchPacketOfType(PacketType.PING));

    final Packet pongFromDiscoPeer =
        MockPacketDataFactory.mockPongPacket(discoPeer, discoPeerPing.getHash());
    controller.onMessage(pongFromDiscoPeer, discoPeer);

    verify(outboundMessageHandler, times(1))
        .send(eq(discoPeer), matchPacketOfType(PacketType.FIND_NEIGHBORS));

    // Setup ping to be sent to otherPeer after neighbors packet is received
    keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    pingPacketData = PingPacketData.create(localEndpoint, otherPeer.getEndpoint());
    final Packet pingPacket = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));
    doReturn(pingPacket)
        .when(controller)
        .createPacket(eq(PacketType.PING), matchPingDataForPeer(otherPeer));

    // Setup ping to be sent to otherPeer2 after neighbors packet is received
    keyPairs = PeerDiscoveryTestHelper.generateKeyPairs(1);
    pingPacketData = PingPacketData.create(localEndpoint, otherPeer2.getEndpoint());
    final Packet pingPacket2 = Packet.create(PacketType.PING, pingPacketData, keyPairs.get(0));
    doReturn(pingPacket2)
        .when(controller)
        .createPacket(eq(PacketType.PING), matchPingDataForPeer(otherPeer2));

    final Packet neighborsPacket =
        MockPacketDataFactory.mockNeighborsPacket(discoPeer, otherPeer, otherPeer2);
    controller.onMessage(neighborsPacket, discoPeer);

    verify(controller, times(0)).bond(otherPeer, false);
    verify(controller, times(1)).bond(otherPeer2, false);
  }

  @Test
  public void shouldNotRespondToPingFromNonWhitelistedDiscoveryPeer() {
    final List<DiscoveryPeer> peers = createPeersInLastBucket(localPeer, 3);
    final DiscoveryPeer discoPeer = peers.get(0);

    final PeerBlacklist blacklist = new PeerBlacklist();

    // don't add disco peer to whitelist
    PermissioningConfiguration config = PermissioningConfiguration.createDefault();
    config.setNodeWhitelist(new ArrayList<>());
    Optional<NodeWhitelistController> nodeWhitelistController =
        Optional.of(new NodeWhitelistController(config));

    controller =
        getControllerBuilder()
            .peers(discoPeer)
            .blacklist(blacklist)
            .whitelist(nodeWhitelistController)
            .build();

    final Packet pingPacket = mockPingPacket(peers.get(0), localPeer);
    controller.onMessage(pingPacket, peers.get(0));
    assertThat(controller.getPeers()).doesNotContain(peers.get(0));
  }

  private static Packet mockPingPacket(final Peer from, final Peer to) {
    final Packet packet = mock(Packet.class);

    final PingPacketData pingPacketData =
        PingPacketData.create(from.getEndpoint(), to.getEndpoint());
    when(packet.getPacketData(any())).thenReturn(Optional.of(pingPacketData));
    final BytesValue id = from.getId();
    when(packet.getNodeId()).thenReturn(id);
    when(packet.getType()).thenReturn(PacketType.PING);
    when(packet.getHash()).thenReturn(Bytes32.ZERO);

    return packet;
  }

  private List<DiscoveryPeer> createPeersInLastBucket(final Peer host, final int n) {
    final List<DiscoveryPeer> newPeers = new ArrayList<DiscoveryPeer>(n);

    // Flipping the most significant bit of the keccak256 will place the peer
    // in the last bucket for the corresponding host peer.
    final Bytes32 keccak256 = host.keccak256();
    final MutableBytesValue template = MutableBytesValue.create(keccak256.size());
    byte msb = keccak256.get(0);
    msb ^= MOST_SIGNFICANT_BIT_MASK;
    template.set(0, msb);

    for (int i = 0; i < n; i++) {
      template.setInt(template.size() - 4, i);
      final Bytes32 newKeccak256 = Bytes32.leftPad(template.copy());
      final DiscoveryPeer newPeer = mock(DiscoveryPeer.class);
      when(newPeer.keccak256()).thenReturn(newKeccak256);
      final MutableBytesValue newId = MutableBytesValue.create(64);
      UInt256.of(i).getBytes().copyTo(newId, newId.size() - UInt256Value.SIZE);
      when(newPeer.getId()).thenReturn(newId);
      when(newPeer.getEndpoint())
          .thenReturn(
              new Endpoint(
                  host.getEndpoint().getHost(),
                  100 + counter.incrementAndGet(),
                  OptionalInt.empty()));
      newPeers.add(newPeer);
    }

    return newPeers;
  }

  private PeerDiscoveryController startPeerDiscoveryController(
      final DiscoveryPeer... bootstrapPeers) {
    return startPeerDiscoveryController(LONG_DELAY_FUNCTION, bootstrapPeers);
  }

  private PeerDiscoveryController startPeerDiscoveryController(
      final RetryDelayFunction retryDelayFunction, final DiscoveryPeer... bootstrapPeers) {
    // Create the controller.
    controller = getControllerBuilder().peers(bootstrapPeers).build();
    controller.setRetryDelayFunction(retryDelayFunction);
    controller.start();
    return controller;
  }

  static class ControllerBuilder {
    private Collection<DiscoveryPeer> discoPeers = Collections.emptyList();
    private PeerBlacklist blacklist = new PeerBlacklist();
    private Optional<NodeWhitelistController> whitelist = Optional.empty();
    private MockTimerUtil timerUtil = new MockTimerUtil();
    private KeyPair keypair;
    private DiscoveryPeer localPeer;
    private PeerTable peerTable;
    private OutboundMessageHandler outboundMessageHandler = OutboundMessageHandler.NOOP;
    private static final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();

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

    ControllerBuilder blacklist(final PeerBlacklist blacklist) {
      this.blacklist = blacklist;
      return this;
    }

    ControllerBuilder whitelist(final Optional<NodeWhitelistController> whitelist) {
      this.whitelist = whitelist;
      return this;
    }

    ControllerBuilder timerUtil(final MockTimerUtil timerUtil) {
      this.timerUtil = timerUtil;
      return this;
    }

    ControllerBuilder keyPair(final KeyPair keypair) {
      this.keypair = keypair;
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
      checkNotNull(keypair);
      if (localPeer == null) {
        localPeer = helper.createDiscoveryPeer(keypair);
      }
      if (peerTable == null) {
        peerTable = new PeerTable(localPeer.getId());
      }
      return spy(
          new PeerDiscoveryController(
              keypair,
              localPeer,
              peerTable,
              discoPeers,
              outboundMessageHandler,
              timerUtil,
              TABLE_REFRESH_INTERVAL_MS,
              PEER_REQUIREMENT,
              blacklist,
              whitelist,
              new Subscribers<>()));
    }
  }
}
