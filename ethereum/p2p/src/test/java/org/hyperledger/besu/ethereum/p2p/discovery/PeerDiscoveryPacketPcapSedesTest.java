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

import org.hyperledger.besu.ethereum.p2p.discovery.internal.ENRRequestPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.ENRResponsePacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.FindNeighborsPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.NeighborsPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.Packet;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PacketType;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PingPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PongPacketData;
import org.hyperledger.besu.util.NetworkUtility;

import java.time.Instant;

import com.google.common.net.InetAddresses;
import io.vertx.core.buffer.Buffer;
import org.apache.tuweni.units.bigints.UInt64;
import org.assertj.core.api.Condition;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

public class PeerDiscoveryPacketPcapSedesTest {
  private static final String pingHexData =
      "dd49244b390b8ee964c7320742acfbb893ab47f051a323c0d908bd360ae83bd92d1e6dd370af2e5d21c4ce2b054baa1be66fe879d"
          + "76ca851c12803a26a382d6e56e33071ba98ae22374fd37be02aa8573aac89f955ae21e96f154d82d0f6b2e20101df05cb"
          + "84b4b57a1982040182765fcb84b4b57a1a82040182765f8460e5f20603";
  private static final String pongHexData =
      "3b3d56e4fcdcf714d6c29b0d521e9f4ec0dd50c73c0bbb9b44758a0a7e416ff30a3ea99de3e78a5cff82ad0a0b82030f78c6eff5a"
          + "1b2a030276277b6e9b6ad7c35db1a6d5f83586a203b4537d82edc6b2820849a485e55aa06cfc680dc63c22d0002f3cb84"
          + "b4b57a1a82040182765fa046896547d3b4259aa1a67bd26e7ec58ab4be650c5552ef0360caf9dae489d53b8460e5e7b603";
  private static final String findNeighborsHexData =
      "3b4c3be981427a8e9739dcd4ea3cf29fe1faa104b8381cb7c26053c4b711015b3"
          + "919213819e30284bb82ec90081098ff4af02e8d9aa12692d4a0511fe92a3c137c3b65dddc309a0384ddb60074be46735c798710f04b95a"
          + "868a1fdbac9328bc70003f847b840986165a2febf6b2b69383bfe10bfeafe1e0d63eac2387d340e51f402bf98860323dd8603800b661ce"
          + "df5823e1a478f4f78e6661c957ed1db1b146d521cf60675845fda14be";
  private static final String neighborsHexData =
      "fa4484fd625113e9bf1d38218d98ce8c28f31d722f38b0bb1bc8296c82c741e8490ac"
          + "82ea9afcb582f393cd5b7ad7fc72990015d3cc58f7f1527b6a60f671767458bc4cd4c00a08ab0eb56c85b5ab739bfda68b7cf24cdbb99d"
          + "3dddbd4e0c6840004f8a5f89ef84d847f00000182765f82765fb840233590850744c0d95e3fd325a2b694de5d3a0f1e0c7e304358253f5"
          + "725d25734a2e08bb1c2ac4ccccd527660b8f1a265c0dae4ef6adda8b5f07a742239bbd1fff84d847f00000182765f82765fb840841d92a"
          + "de4223b36e213e03197fecc1250f34c52e1e1ec8cdff5b9cbe005f95567daa9fd96a64c0e3e3a8d55157bf9d87f1c4666cdae79b37bfa5"
          + "c1835353475845fda165c";
  private static final String enrResquestHexData =
      "fed7cfd0a60b51d027d14d5bd1d4c5bc4ea289940c0d38f2ff8e72522e33e39be040ef1acbe25e2c40523821c0c536e17e0f7204a08260b842dc"
          + "a830513a2f9e5169a3f711ecb5a512fdd56e5edfd7d8fdaa0e6982020dbd2f76949ef84d1a840005c58460146486";
  private static final String enrResponseHexData =
      "9c85a6d16e5222dc48df51654cc36fb372b5429646ca8fd85a7f79ea420dba326f2cc84456b6c7fa1e6dd4ed6e3e89934e6f4415d58b40899996"
          + "ee461a8e147f62ff33177680cffe061d048183091b4254dd1edf05f7e92d1117b23035d94f7d0106f8a7a0fed7cfd0a60b51d027d14d5b"
          + "d1d4c5bc4ea289940c0d38f2ff8e72522e33e39bf884b8407098ad865b00a582051940cb9cf36836572411a47278783077011599ed5cd1"
          + "6b76f2635f4e234738f30813a89eb9137e3e3df5266e3a1f11df72ecf1145ccb9c01826964827634826970847f00000189736563703235"
          + "366b31a103ca634cae0d49acb401d8a4c6b6fe8c55b70d115bf400769cc1400f3258cd31388375647082765f";

  @Test
  public void testUDPPingSerializeDeserialize() {
    final byte[] data = Hex.decode(pingHexData);
    final Packet packet = Packet.decode(Buffer.buffer(data));
    assertThat(packet.getType()).isNotNull();
    assertThat(packet.getNodeId()).isNotNull();
    assertThat(packet.getNodeId().toArray()).hasSize(64);

    assertThat(packet.getType()).isEqualTo(PacketType.PING);
    assertThat(packet.getPacketData(PingPacketData.class)).isPresent();
    final PingPacketData pingPacketData = packet.getPacketData(PingPacketData.class).orElse(null);

    assertThat(pingPacketData).isNotNull();
    assertThat(pingPacketData.getTo()).isNotNull();
    assertThat(pingPacketData.getFrom()).isNotNull();
    assertThat(pingPacketData.getTo().getHost()).satisfies(validInetAddressCondition);
    assertThat(pingPacketData.getFrom().map(Endpoint::getHost))
        .hasValueSatisfying(validInetAddressCondition);
    assertThat(pingPacketData.getTo().getUdpPort()).isPositive();
    assertThat(pingPacketData.getFrom().get().getUdpPort()).isPositive();
    pingPacketData.getTo().getTcpPort().ifPresent(p -> assertThat(p).isPositive());
    pingPacketData.getFrom().get().getTcpPort().ifPresent(p -> assertThat(p).isPositive());
    assertThat(pingPacketData.getExpiration()).isPositive();
    assertThat(pingPacketData.getEnrSeq().isPresent()).isTrue();
    assertThat(pingPacketData.getEnrSeq().get()).isGreaterThan(UInt64.ZERO);

    final byte[] encoded = packet.encode().getBytes();
    assertThat(encoded).isEqualTo(data);
  }

  @Test
  public void testUDPPongSerializeDeserialize() {
    final byte[] data = Hex.decode(pongHexData);
    final Packet packet = Packet.decode(Buffer.buffer(data));
    assertThat(packet.getType()).isNotNull();
    assertThat(packet.getNodeId()).isNotNull();
    assertThat(packet.getNodeId().toArray()).hasSize(64);
    assertThat(packet.getType()).isEqualTo(PacketType.PONG);
    assertThat(packet.getPacketData(PongPacketData.class)).isPresent();

    final PongPacketData pongPacketData = packet.getPacketData(PongPacketData.class).orElse(null);
    assertThat(pongPacketData).isNotNull();
    assertThat(pongPacketData.getTo()).isNotNull();
    assertThat(pongPacketData.getTo().getHost()).satisfies(validInetAddressCondition);
    assertThat(pongPacketData.getTo().getUdpPort()).isPositive();
    pongPacketData.getTo().getTcpPort().ifPresent(p -> assertThat(p).isPositive());
    assertThat(pongPacketData.getPingHash().toArray()).hasSize(32);
    assertThat(pongPacketData.getExpiration()).isPositive();
    assertThat(pongPacketData.getEnrSeq().isPresent()).isTrue();
    assertThat(pongPacketData.getEnrSeq().get()).isGreaterThan(UInt64.ZERO);

    final byte[] encoded = packet.encode().getBytes();
    assertThat(encoded).isEqualTo(data);
  }

  @Test
  public void testUDPFindNeighborsSerializeDeserialize() {
    final byte[] data = Hex.decode(findNeighborsHexData);
    final Packet packet = Packet.decode(Buffer.buffer(data));
    final Instant timestamp = Instant.ofEpochSecond(1608127678L);
    assertThat(packet.getType()).isNotNull();
    assertThat(packet.getNodeId()).isNotNull();
    assertThat(packet.getNodeId().toArray()).hasSize(64);
    assertThat(packet.getType()).isEqualTo(PacketType.FIND_NEIGHBORS);
    assertThat(packet.getPacketData(FindNeighborsPacketData.class)).isPresent();

    final FindNeighborsPacketData findNeighborsPacketData =
        packet.getPacketData(FindNeighborsPacketData.class).orElse(null);
    assertThat(findNeighborsPacketData).isNotNull();
    assertThat(findNeighborsPacketData.getExpiration())
        .isBetween(timestamp.getEpochSecond() - 10000, timestamp.getEpochSecond() + 10000);
    assertThat(findNeighborsPacketData.getTarget().toArray()).hasSize(64);
    assertThat(packet.getNodeId().toArray()).hasSize(64);

    final byte[] encoded = packet.encode().getBytes();
    assertThat(encoded).isEqualTo(data);
  }

  @Test
  public void testUDPNeighborsSerializeDeserialize() {
    final byte[] data = Hex.decode(neighborsHexData);
    final Packet packet = Packet.decode(Buffer.buffer(data));
    assertThat(packet.getType()).isNotNull();
    assertThat(packet.getNodeId()).isNotNull();
    assertThat(packet.getNodeId().toArray()).hasSize(64);
    assertThat(packet.getType()).isEqualTo(PacketType.NEIGHBORS);
    assertThat(packet.getPacketData(NeighborsPacketData.class)).isPresent();

    final NeighborsPacketData neighborsPacketData =
        packet.getPacketData(NeighborsPacketData.class).orElse(null);
    assertThat(neighborsPacketData).isNotNull();
    assertThat(neighborsPacketData.getExpiration()).isPositive();
    assertThat(neighborsPacketData.getNodes()).isNotEmpty();

    for (final DiscoveryPeer p : neighborsPacketData.getNodes()) {
      assertThat(NetworkUtility.isValidPort(p.getEndpoint().getUdpPort())).isTrue();
      assertThat(p.getEndpoint().getHost()).satisfies(validInetAddressCondition);
      assertThat(p.getId().toArray()).hasSize(64);
    }

    final byte[] encoded = packet.encode().getBytes();
    assertThat(encoded).isEqualTo(data);
  }

  @Test
  public void testUDPENRRequestSerializeDeserialize() {
    final byte[] data = Hex.decode(enrResquestHexData);
    final Packet packet = Packet.decode(Buffer.buffer(data));
    assertThat(packet.getType()).isNotNull();
    assertThat(packet.getNodeId()).isNotNull();
    assertThat(packet.getNodeId().toArray()).hasSize(64);
    assertThat(packet.getType()).isEqualTo(PacketType.ENR_REQUEST);

    final ENRRequestPacketData enrRequestPacketData =
        packet.getPacketData(ENRRequestPacketData.class).orElse(null);
    assertThat(enrRequestPacketData).isNotNull();
    assertThat(enrRequestPacketData.getExpiration()).isPositive();

    final byte[] encoded = packet.encode().getBytes();
    assertThat(encoded).isEqualTo(data);
  }

  @Test
  public void testUDPENRResponseSerializeDeserialize() {
    final byte[] data = Hex.decode(enrResponseHexData);
    final Packet packet = Packet.decode(Buffer.buffer(data));
    assertThat(packet.getType()).isNotNull();
    assertThat(packet.getNodeId()).isNotNull();
    assertThat(packet.getNodeId().toArray()).hasSize(64);
    assertThat(packet.getType()).isEqualTo(PacketType.ENR_RESPONSE);

    final ENRResponsePacketData enrResponsePacketData =
        packet.getPacketData(ENRResponsePacketData.class).orElse(null);
    assertThat(enrResponsePacketData).isNotNull();
    assertThat(enrResponsePacketData.getEnr()).isNotNull();
    assertThat(enrResponsePacketData.getEnr().getSeq()).isGreaterThan(UInt64.ZERO);
    assertThat(enrResponsePacketData.getEnr().getSignature()).isNotNull();
    assertThat(enrResponsePacketData.getRequestHash()).isNotNull();
    assertThat(enrResponsePacketData.getRequestHash().toArray()).hasSize(32);

    final byte[] encoded = packet.encode().getBytes();
    assertThat(encoded).isEqualTo(data);
  }

  private final Condition<String> validInetAddressCondition =
      new Condition<>(InetAddresses::isInetAddress, "checks for valid InetAddresses");
}
