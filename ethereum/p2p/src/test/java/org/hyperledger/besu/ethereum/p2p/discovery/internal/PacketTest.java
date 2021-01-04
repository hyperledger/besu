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

import org.hyperledger.besu.ethereum.p2p.discovery.Endpoint;

import java.util.Optional;

import io.vertx.core.buffer.Buffer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public class PacketTest {

  private static final String VALID_PONG_PACKET =
      "53cec0d27af44bdc0471d34c4eb631f74b502df7b5513a80a054f0d619f0417d6ba4fd4d6fb83994b95c6d0ae8b175b068a6bffc397e2b408e797069b9370ce47b153dd884b60108e686546a775ed5f85e71059a9c5791e266bd949d0dcfba380102f83bcb84b4b57a1a82040182765fa046896547d3b4259aa1a67bd26e7ec58ab4be650c5552ef0360caf9dae489d53b845b872dc8880000000000000003";

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
    assertThat(packetData.getExpiration()).isEqualTo(1535585736);
    assertThat(packetData.getEnrSeq().isPresent()).isTrue();
    assertThat(packetData.getEnrSeq().get()).isEqualTo(UInt64.valueOf(3L));
    assertThat(packet.getNodeId())
        .isEqualTo(
            Bytes.fromHexString(
                "0xfbe12329d5d99e3d46cba2d1f9d8d397a4f2955253396f6e0459f3f14bb29c0e4f37d8bac890ff9bfb412879257ba2378a0b48bed6b81647c6972d323212d051"));
    assertThat(packet.getHash())
        .isEqualTo(
            Bytes.fromHexString(
                "0x53cec0d27af44bdc0471d34c4eb631f74b502df7b5513a80a054f0d619f0417d"));
  }

  @Test
  public void shouldRoundTripPacket() {
    final Packet packet = decode(VALID_PONG_PACKET);
    assertThat(Hex.toHexString(packet.encode().getBytes())).isEqualTo(VALID_PONG_PACKET);
  }

  private Packet decode(final String hexData) {
    return Packet.decode(Buffer.buffer(Hex.decode(hexData)));
  }
}
