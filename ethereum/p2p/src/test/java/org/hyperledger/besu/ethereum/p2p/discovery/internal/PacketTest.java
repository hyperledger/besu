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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.ethereum.p2p.discovery.Endpoint;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryPacketDecodingException;

import java.util.Optional;

import io.vertx.core.buffer.Buffer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public class PacketTest {

  private static final String VALID_PONG_PACKET =
      "3b3d56e4fcdcf714d6c29b0d521e9f4ec0dd50c73c0bbb9b44758a0a7e416ff30a3ea99de3e78a5cff82ad0a0b82030f78c6eff5a1b2a030276277b6e9b6ad7c35db1a6d5f83586a203b4537d82edc6b2820849a485e55aa06cfc680dc63c22d0002f3cb84b4b57a1a82040182765fa046896547d3b4259aa1a67bd26e7ec58ab4be650c5552ef0360caf9dae489d53b8460e5e7b603";
  private static final String INVALID_SIGNATURE_PACKET =
      "43f91d11b3338b4dbdf16db4f9fa25d7b4e2db81e6fd63f8f6884dfaea851e106f8f692c77169b387bde7c38832cf2d37a9b97b1553d07587ebe251ee21ee36e0ed54fd9218e3feea3bd13ca6982b25c204d5186e7ec5373ea664c91d42467b30102f3cb842f3ee37b82040e82765fa04139782abaccbc8fd290a7fde1ff138943fa9659f7bd67f97c97b09893d1ee8a84607806e108";

  @Test
  public void shouldDecodeValidPongPacket() {
    final Packet packet = decode(VALID_PONG_PACKET);
    final PongPacketData packetData = packet.getPacketData(PongPacketData.class).get();

    assertThat(packet.getType()).isSameAs(PacketType.PONG);
    assertThat(packetData.getTo())
        .isEqualTo(new Endpoint("180.181.122.26", 1025, Optional.of(30303)));
    assertThat(packetData.getPingHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x46896547d3b4259aa1a67bd26e7ec58ab4be650c5552ef0360caf9dae489d53b"));
    assertThat(packetData.getExpiration()).isEqualTo(1625679798);
    assertThat(packetData.getEnrSeq().isPresent()).isTrue();
    assertThat(packetData.getEnrSeq().get()).isEqualTo(UInt64.valueOf(3L));
    assertThat(packet.getNodeId())
        .isEqualTo(
            Bytes.fromHexString(
                "0x8cee393bcb969168690845905292da56f5eed661a2f332632c61be3d9171763825f8d520041d6dbf41b4d749a78ff73b6da286a5d7e88c52ac4dae26b9df4602"));
    assertThat(packet.getHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x3b3d56e4fcdcf714d6c29b0d521e9f4ec0dd50c73c0bbb9b44758a0a7e416ff3"));
  }

  @Test
  public void shouldRoundTripPacket() {
    final Packet packet = decode(VALID_PONG_PACKET);
    assertThat(Hex.toHexString(packet.encode().getBytes())).isEqualTo(VALID_PONG_PACKET);
  }

  @Test
  public void invalidSignatureShouldThrowPeerDiscoveryPacketDecodingException() {
    assertThatThrownBy(() -> decode(INVALID_SIGNATURE_PACKET))
        .isInstanceOf(PeerDiscoveryPacketDecodingException.class);
  }

  private Packet decode(final String hexData) {
    return Packet.decode(Buffer.buffer(Hex.decode(hexData)));
  }
}
