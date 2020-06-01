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
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.time.Instant;
import java.util.OptionalInt;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Test;

public class PongPacketDataTest {

  @Test
  public void serializeDeserialize() {
    final long currentTimeSec = Instant.now().getEpochSecond();
    final Endpoint to = new Endpoint("127.0.0.2", 30303, OptionalInt.empty());
    final Bytes32 hash = Bytes32.fromHexStringLenient("0x1234");

    final PongPacketData packet = PongPacketData.create(to, hash);
    final Bytes serialized = RLP.encode(packet::writeTo);
    final PongPacketData deserialized = PongPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getPingHash()).isEqualTo(hash);
    assertThat(deserialized.getExpiration()).isGreaterThan(currentTimeSec);
  }

  @Test
  public void readFrom() {
    final long time = System.currentTimeMillis();
    final Endpoint to = new Endpoint("127.0.0.2", 30303, OptionalInt.empty());
    final Bytes32 hash = Bytes32.fromHexStringLenient("0x1234");

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    to.encodeStandalone(out);
    out.writeBytes(hash);
    out.writeLongScalar(time);
    out.endList();
    final Bytes encoded = out.encoded();

    final PongPacketData deserialized = PongPacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getPingHash()).isEqualTo(hash);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
  }

  @Test
  public void readFrom_withExtraFields() {
    final long time = System.currentTimeMillis();
    final Endpoint to = new Endpoint("127.0.0.2", 30303, OptionalInt.empty());
    final Bytes32 hash = Bytes32.fromHexStringLenient("0x1234");

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    to.encodeStandalone(out);
    out.writeBytes(hash);
    out.writeLongScalar(time);
    // Add random fields
    out.writeLong(1234L);
    out.endList();
    final Bytes encoded = out.encoded();

    final PongPacketData deserialized = PongPacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getPingHash()).isEqualTo(hash);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
  }
}
