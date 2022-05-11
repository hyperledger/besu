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
package org.hyperledger.besu.ethereum.p2p.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.FindNeighborsPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.NeighborsPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.Packet;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PacketType;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.List;
import java.util.Random;

import io.vertx.core.buffer.Buffer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.junit.Test;

public class PeerDiscoveryPacketSedesTest {
  private final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();

  @Test
  public void serializeDeserializeEntirePacket() {
    final byte[] r = new byte[64];
    new Random().nextBytes(r);
    final Bytes target = Bytes.wrap(r);
    final NodeKey nodeKey = NodeKeyUtils.generate();

    final FindNeighborsPacketData packetData = FindNeighborsPacketData.create(target);
    final Packet packet = Packet.create(PacketType.FIND_NEIGHBORS, packetData, nodeKey);
    final Buffer encoded = packet.encode();
    assertThat(encoded).isNotNull();

    final Packet decoded = Packet.decode(encoded);
    assertThat(decoded.getType()).isEqualTo(PacketType.FIND_NEIGHBORS);
    assertThat(decoded.getNodeId()).isEqualTo(nodeKey.getPublicKey().getEncodedBytes());
    assertThat(decoded.getPacketData(NeighborsPacketData.class)).isNotPresent();
    assertThat(decoded.getPacketData(FindNeighborsPacketData.class)).isPresent();
  }

  @Test
  public void serializeDeserializeFindNeighborsPacketData() {
    final byte[] r = new byte[64];
    new Random().nextBytes(r);
    final Bytes target = Bytes.wrap(r);

    final FindNeighborsPacketData packet = FindNeighborsPacketData.create(target);
    final Bytes serialized = RLP.encode(packet::writeTo);
    assertThat(serialized).isNotNull();

    final FindNeighborsPacketData deserialized =
        FindNeighborsPacketData.readFrom(RLP.input(serialized));
    assertThat(deserialized.getTarget()).isEqualTo(target);
    // Fuzziness: allow a skew of 2 seconds between the time the message was generated until the
    // assertion.
    assertThat(deserialized.getExpiration()).isCloseTo(PacketData.defaultExpiration(), offset(2L));
  }

  @Test
  public void neighborsPacketData() {
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(5);

    final NeighborsPacketData packet = NeighborsPacketData.create(peers);
    final Bytes serialized = RLP.encode(packet::writeTo);
    assertThat(serialized).isNotNull();

    final NeighborsPacketData deserialized = NeighborsPacketData.readFrom(RLP.input(serialized));
    assertThat(deserialized.getNodes()).isEqualTo(peers);
    // Fuzziness: allow a skew of 2 seconds between the time the message was generated until the
    // assertion.
    assertThat(deserialized.getExpiration()).isCloseTo(PacketData.defaultExpiration(), offset(2L));
  }

  @Test
  public void deserializeDifferentPacketData() {
    final byte[] r = new byte[64];
    new Random().nextBytes(r);
    final Bytes target = Bytes.wrap(r);

    final FindNeighborsPacketData packet = FindNeighborsPacketData.create(target);
    final Bytes serialized = RLP.encode(packet::writeTo);
    assertThat(serialized).isNotNull();

    final RLPInput input = RLP.input(serialized);
    assertThatThrownBy(() -> NeighborsPacketData.readFrom(input)).isInstanceOf(RLPException.class);
  }

  @Test
  public void integrityCheckFailsUnmatchedHash() {
    final byte[] r = new byte[64];
    new Random().nextBytes(r);
    final Bytes target = Bytes.wrap(r);

    final NodeKey nodeKey = NodeKeyUtils.generate();

    final FindNeighborsPacketData data = FindNeighborsPacketData.create(target);
    final Packet packet = Packet.create(PacketType.FIND_NEIGHBORS, data, nodeKey);

    final Bytes encoded = Bytes.wrapBuffer(packet.encode());
    final MutableBytes garbled = encoded.mutableCopy();
    final int i = garbled.size() - 1;
    // Change one bit in the last byte, which belongs to the payload, hence the hash will not match
    // any longer.
    garbled.set(i, (byte) (garbled.get(i) + 0x01));
    final Buffer input = Buffer.buffer(garbled.toArray());
    assertThatThrownBy(() -> Packet.decode(input))
        .isInstanceOf(PeerDiscoveryPacketDecodingException.class);
  }
}
