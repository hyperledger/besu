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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Arrays;
import java.util.Optional;

public class MockPacketDataFactory {

  public static Packet mockNeighborsPacket(
      final DiscoveryPeer from, final DiscoveryPeer... neighbors) {
    final Packet packet = mock(Packet.class);

    final NeighborsPacketData packetData = NeighborsPacketData.create(Arrays.asList(neighbors));

    when(packet.getPacketData(any())).thenReturn(Optional.of(packetData));
    final BytesValue id = from.getId();
    when(packet.getNodeId()).thenReturn(id);
    when(packet.getType()).thenReturn(PacketType.NEIGHBORS);
    when(packet.getHash()).thenReturn(Bytes32.ZERO);

    return packet;
  }

  public static Packet mockPongPacket(final DiscoveryPeer from, final BytesValue pingHash) {
    final Packet packet = mock(Packet.class);

    final PongPacketData pongPacketData = PongPacketData.create(from.getEndpoint(), pingHash);
    when(packet.getPacketData(any())).thenReturn(Optional.of(pongPacketData));
    final BytesValue id = from.getId();
    when(packet.getNodeId()).thenReturn(id);
    when(packet.getType()).thenReturn(PacketType.PONG);
    when(packet.getHash()).thenReturn(Bytes32.ZERO);

    return packet;
  }

  public static Packet mockFindNeighborsPacket(final Peer from) {
    final Packet packet = mock(Packet.class);
    final BytesValue target =
        BytesValue.fromHexString(
            "0x0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f40");

    final FindNeighborsPacketData packetData = FindNeighborsPacketData.create(target);
    when(packet.getPacketData(any())).thenReturn(Optional.of(packetData));
    final BytesValue id = from.getId();
    when(packet.getNodeId()).thenReturn(id);
    when(packet.getType()).thenReturn(PacketType.FIND_NEIGHBORS);
    when(packet.getHash()).thenReturn(Bytes32.ZERO);

    return packet;
  }
}
