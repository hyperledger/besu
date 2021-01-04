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
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.Test;

public class PingPacketDataTest {

  @Test
  public void serializeDeserialize() {
    final long currentTimeSec = Instant.now().getEpochSecond();

    final Endpoint from = new Endpoint("127.0.0.1", 30303, Optional.of(30303));
    final Endpoint to = new Endpoint("127.0.0.2", 30303, Optional.empty());
    final UInt64 enrSeq = UInt64.ONE;
    final PingPacketData packet = PingPacketData.create(from, to, enrSeq);
    final Bytes serialized = RLP.encode(packet::writeTo);
    final PingPacketData deserialized = PingPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getFrom()).contains(from);
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getExpiration()).isGreaterThan(currentTimeSec);
    assertThat(deserialized.getEnrSeq().isPresent()).isTrue();
    assertThat(deserialized.getEnrSeq().get()).isEqualTo(enrSeq);
  }

  @Test
  public void readFrom() {
    final int version = 4;
    final Endpoint from = new Endpoint("127.0.0.1", 30303, Optional.of(30303));
    final Endpoint to = new Endpoint("127.0.0.2", 30303, Optional.empty());
    final long time = System.currentTimeMillis();
    final UInt64 enrSeq = UInt64.ONE;

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeIntScalar(version);
    from.encodeStandalone(out);
    to.encodeStandalone(out);
    out.writeLongScalar(time);
    out.writeBytes(enrSeq.toBytes());
    out.endList();

    final Bytes serialized = out.encoded();
    final PingPacketData deserialized = PingPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getFrom()).contains(from);
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
    assertThat(deserialized.getEnrSeq().isPresent()).isTrue();
    assertThat(deserialized.getEnrSeq().get()).isEqualTo(enrSeq);
  }

  @Test
  public void readFrom_withExtraFields() {
    final int version = 4;
    final Endpoint from = new Endpoint("127.0.0.1", 30303, Optional.of(30303));
    final Endpoint to = new Endpoint("127.0.0.2", 30303, Optional.empty());
    final long time = System.currentTimeMillis();
    final UInt64 enrSeq = UInt64.ONE;

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeIntScalar(version);
    from.encodeStandalone(out);
    to.encodeStandalone(out);
    out.writeLongScalar(time);
    out.writeBytes(enrSeq.toBytes());
    // Add extra field
    out.writeLongScalar(11);
    out.endList();

    final Bytes serialized = out.encoded();
    final PingPacketData deserialized = PingPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getFrom()).contains(from);
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
    assertThat(deserialized.getEnrSeq().isPresent()).isTrue();
    assertThat(deserialized.getEnrSeq().get()).isEqualTo(enrSeq);
  }

  @Test
  public void readFrom_unknownVersion() {
    final int version = 99;
    final Endpoint from = new Endpoint("127.0.0.1", 30303, Optional.of(30303));
    final Endpoint to = new Endpoint("127.0.0.2", 30303, Optional.empty());
    final long time = System.currentTimeMillis();
    final UInt64 enrSeq = UInt64.ONE;

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeIntScalar(version);
    from.encodeStandalone(out);
    to.encodeStandalone(out);
    out.writeLongScalar(time);
    out.writeBytes(enrSeq.toBytes());
    out.endList();

    final Bytes serialized = out.encoded();
    final PingPacketData deserialized = PingPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getFrom()).contains(from);
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
    assertThat(deserialized.getEnrSeq().isPresent()).isTrue();
    assertThat(deserialized.getEnrSeq().get()).isEqualTo(enrSeq);
  }

  @Test
  public void readFrom_lowPortValues() {
    final int version = 4;
    final Endpoint from = new Endpoint("0.1.2.1", 1, Optional.of(1));
    final Endpoint to = new Endpoint("127.0.0.2", 30303, Optional.empty());
    final long time = System.currentTimeMillis();
    final UInt64 enrSeq = UInt64.ONE;

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeIntScalar(version);
    from.encodeStandalone(out);
    to.encodeStandalone(out);
    out.writeLongScalar(time);
    out.writeBytes(enrSeq.toBytes());
    out.endList();

    final Bytes serialized = out.encoded();
    final PingPacketData deserialized = PingPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getFrom()).contains(from);
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
    assertThat(deserialized.getEnrSeq().isPresent()).isTrue();
    assertThat(deserialized.getEnrSeq().get()).isEqualTo(enrSeq);
  }
}
