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
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.Test;

public class PongPacketDataTest {

  @Test
  public void serializeDeserialize() {
    final long currentTimeSec = Instant.now().getEpochSecond();
    final Endpoint to = new Endpoint("127.0.0.2", 30303, Optional.empty());
    final Bytes32 hash = Bytes32.fromHexStringLenient("0x1234");
    final UInt64 enrSeq = UInt64.ONE;

    final PongPacketData packet = PongPacketData.create(to, hash, enrSeq);
    final Bytes serialized = RLP.encode(packet::writeTo);
    final PongPacketData deserialized = PongPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getPingHash()).isEqualTo(hash);
    assertThat(deserialized.getExpiration()).isGreaterThan(currentTimeSec);
    assertThat(deserialized.getEnrSeq().isPresent()).isTrue();
    assertThat(deserialized.getEnrSeq().get()).isEqualTo(enrSeq);
  }

  @Test
  public void readFrom() {
    final long time = System.currentTimeMillis();
    final Endpoint to = new Endpoint("127.0.0.2", 30303, Optional.empty());
    final Bytes32 hash = Bytes32.fromHexStringLenient("0x1234");
    final UInt64 enrSeq = UInt64.ONE;

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    to.encodeStandalone(out);
    out.writeBytes(hash);
    out.writeLongScalar(time);
    out.writeLongScalar(enrSeq.toLong());
    out.endList();
    final Bytes encoded = out.encoded();

    final PongPacketData deserialized = PongPacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getPingHash()).isEqualTo(hash);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
    assertThat(deserialized.getEnrSeq().isPresent()).isTrue();
    assertThat(deserialized.getEnrSeq().get()).isEqualTo(enrSeq);
  }

  @Test
  public void handlesLegacyENREncode() {
    final long time = System.currentTimeMillis();
    final Endpoint to = new Endpoint("127.0.0.2", 30303, Optional.empty());
    final Bytes32 hash = Bytes32.fromHexStringLenient("0x1234");
    final UInt64 enrSeq = UInt64.ONE;

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    to.encodeStandalone(out);
    out.writeBytes(hash);
    out.writeLongScalar(time);
    out.writeBytes(enrSeq.toBytes());
    out.endList();
    final Bytes encoded = out.encoded();

    final PongPacketData deserialized = PongPacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getPingHash()).isEqualTo(hash);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
    assertThat(deserialized.getEnrSeq().isPresent()).isTrue();
    assertThat(deserialized.getEnrSeq().get()).isEqualTo(enrSeq);
  }

  @Test
  public void legacyHandlesScalar() {

    final Endpoint to = new Endpoint("127.0.0.2", 30303, Optional.empty());
    final UInt64 enrSeq = UInt64.MAX_VALUE;
    final Bytes32 hash = Bytes32.fromHexStringLenient("0x1234");
    final PongPacketData pong = PongPacketData.create(to, hash, enrSeq);

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    pong.writeTo(out);

    final PongPacketData legacyPong = PongPacketData.legacyReadFrom(RLP.input(out.encoded()));

    assertThat(legacyPong.getTo()).isEqualTo(to);
    assertThat(legacyPong.getPingHash()).isEqualTo(hash);
    assertThat(legacyPong.getEnrSeq().isPresent()).isTrue();
    assertThat(legacyPong.getEnrSeq().get()).isEqualTo(enrSeq);
  }

  @Test
  public void readFrom_withExtraFields() {
    final long time = System.currentTimeMillis();
    final Endpoint to = new Endpoint("127.0.0.2", 30303, Optional.empty());
    final Bytes32 hash = Bytes32.fromHexStringLenient("0x1234");
    final UInt64 enrSeq = UInt64.ONE;

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    to.encodeStandalone(out);
    out.writeBytes(hash);
    out.writeLongScalar(time);
    out.writeLongScalar(enrSeq.toLong());
    // Add random fields
    out.writeLong(1234L);
    out.endList();
    final Bytes encoded = out.encoded();

    final PongPacketData deserialized = PongPacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getPingHash()).isEqualTo(hash);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
    assertThat(deserialized.getEnrSeq().isPresent()).isTrue();
    assertThat(deserialized.getEnrSeq().get()).isEqualTo(enrSeq);
  }

  @Test
  public void readFrom_fixedWidthSeq() {
    final long time = System.currentTimeMillis();
    final Endpoint to = new Endpoint("127.0.0.2", 30303, Optional.empty());
    final Bytes32 hash = Bytes32.fromHexStringLenient("0x1234");
    final UInt64 enrSeq = UInt64.ONE;

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    to.encodeStandalone(out);
    out.writeBytes(hash);
    out.writeLongScalar(time);
    out.writeLongScalar(enrSeq.toLong());
    // Add random fields
    out.writeLong(1234L);
    out.endList();
    final Bytes encoded = out.encoded();

    final PongPacketData deserialized = PongPacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getPingHash()).isEqualTo(hash);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
    assertThat(deserialized.getEnrSeq().isPresent()).isTrue();
    assertThat(deserialized.getEnrSeq().get()).isEqualTo(enrSeq);
  }
}
