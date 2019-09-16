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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.p2p.discovery.Endpoint;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.OptionalInt;

import io.vertx.core.buffer.Buffer;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public class PacketTest {

  private static final String VALID_PONG_PACKET =
      "a1581c1705e744976d0341011c4490b3ab0b48283407ae5cf7526b948717489613ad897c4cf167117196d21352c15bcbaec23227b22eb92a15f5cd4b0a4ef98124a679935c16bd334fbd26be55ba4344843ac4710a3f3e3684d719d48c4980660002f2cb84b4b57a1a82040182765fa046896547d3b4259aa1a67bd26e7ec58ab4be650c5552ef0360caf9dae489d53b845b872dc8";

  @Test
  public void shouldDecodeValidPongPacket() {
    final Packet packet = decode(VALID_PONG_PACKET);
    final PongPacketData packetData = packet.getPacketData(PongPacketData.class).get();

    assertThat(packet.getType()).isSameAs(PacketType.PONG);
    assertThat(packetData.getTo())
        .isEqualTo(new Endpoint("180.181.122.26", 1025, OptionalInt.of(30303)));
    assertThat(packetData.getPingHash())
        .isEqualTo(
            BytesValue.fromHexString(
                "0x46896547d3b4259aa1a67bd26e7ec58ab4be650c5552ef0360caf9dae489d53b"));
    assertThat(packetData.getExpiration()).isEqualTo(1535585736);
    assertThat(packet.getNodeId())
        .isEqualTo(
            BytesValue.fromHexString(
                "0x669f45b66acf3b804c26ce13cfdd1f7e3d0ff4ed85060841b9af3af6dbfbacd05181e1c9363161446a307f3ca24e707856a01e4bf1eed5e1aefc14011a5c1c1c"));
    assertThat(packet.getHash())
        .isEqualTo(
            BytesValue.fromHexString(
                "0xa1581c1705e744976d0341011c4490b3ab0b48283407ae5cf7526b9487174896"));
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
