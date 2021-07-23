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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryStatus;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryTestHelper;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.util.Subscribers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class PeerDiscoveryTableRefreshTest {
  private final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();

  @Test
  public void tableRefreshSingleNode() {
    final List<NodeKey> nodeKeys = PeerDiscoveryTestHelper.generateNodeKeys(2);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(nodeKeys);
    final DiscoveryPeer localPeer = peers.get(0);
    final DiscoveryPeer remotePeer = peers.get(1);
    final NodeKey localKeyPair = nodeKeys.get(0);
    final NodeKey remoteKeyPair = nodeKeys.get(1);

    // Create and start the PeerDiscoveryController
    final OutboundMessageHandler outboundMessageHandler = mock(OutboundMessageHandler.class);
    final MockTimerUtil timer = new MockTimerUtil();

    final PeerDiscoveryController controller =
        spy(
            PeerDiscoveryController.builder()
                .nodeKey(localKeyPair)
                .localPeer(localPeer)
                .peerTable(new PeerTable(localPeer.getId()))
                .outboundMessageHandler(outboundMessageHandler)
                .timerUtil(timer)
                .workerExecutor(new BlockingAsyncExecutor())
                .tableRefreshIntervalMs(0)
                .peerBondedObservers(Subscribers.create())
                .metricsSystem(new NoOpMetricsSystem())
                .build());
    controller.start();

    final PingPacketData mockPing =
        PingPacketData.create(
            Optional.ofNullable(localPeer.getEndpoint()), remotePeer.getEndpoint(), UInt64.ONE);
    final Packet mockPingPacket = Packet.create(PacketType.PING, mockPing, localKeyPair);

    doAnswer(
            invocation -> {
              final Consumer<Packet> handler = invocation.getArgument(2);
              handler.accept(mockPingPacket);
              return null;
            })
        .when(controller)
        .createPacket(eq(PacketType.PING), any(), any());

    // Send a PING, so as to add a Peer in the controller.
    final PingPacketData ping =
        PingPacketData.create(
            Optional.ofNullable(remotePeer.getEndpoint()), localPeer.getEndpoint(), UInt64.ONE);
    final Packet pingPacket = Packet.create(PacketType.PING, ping, remoteKeyPair);
    controller.onMessage(pingPacket, remotePeer);

    // Answer localPeer PING to complete bonding
    final PongPacketData pong =
        PongPacketData.create(localPeer.getEndpoint(), mockPingPacket.getHash(), UInt64.ONE);
    final Packet pongPacket = Packet.create(PacketType.PONG, pong, remoteKeyPair);
    controller.onMessage(pongPacket, remotePeer);

    // Wait until the controller has added the newly found peer.
    assertThat(controller.streamDiscoveredPeers()).hasSize(1);

    final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
    for (int i = 0; i < 5; i++) {
      controller.getRecursivePeerRefreshState().cancel();
      timer.runPeriodicHandlers();
      controller.streamDiscoveredPeers().forEach(p -> p.setStatus(PeerDiscoveryStatus.KNOWN));
      controller.onMessage(pingPacket, remotePeer);
      controller.onMessage(pongPacket, remotePeer);
    }

    verify(outboundMessageHandler, atLeast(5)).send(eq(remotePeer), captor.capture());
    final List<Packet> capturedFindNeighborsPackets =
        captor.getAllValues().stream()
            .filter(p -> p.getType().equals(PacketType.FIND_NEIGHBORS))
            .collect(Collectors.toList());
    assertThat(capturedFindNeighborsPackets.size()).isEqualTo(5);

    // Collect targets from find neighbors packets
    final List<Bytes> targets = new ArrayList<>();
    for (final Packet captured : capturedFindNeighborsPackets) {
      final Optional<FindNeighborsPacketData> maybeData =
          captured.getPacketData(FindNeighborsPacketData.class);
      Assertions.assertThat(maybeData).isPresent();
      final FindNeighborsPacketData neighborsData = maybeData.get();
      targets.add(neighborsData.getTarget());
    }

    // All targets are unique.
    assertThat(targets.size()).isEqualTo(new HashSet<>(targets).size());
  }
}
