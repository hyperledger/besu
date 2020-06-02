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
import org.junit.Test;

public class PingPacketDataTest {

  @Test
  public void serializeDeserialize() {
    final long currentTimeSec = Instant.now().getEpochSecond();

    final Endpoint from = new Endpoint("127.0.0.1", 30303, OptionalInt.of(30303));
    final Endpoint to = new Endpoint("127.0.0.2", 30303, OptionalInt.empty());
    final PingPacketData packet = PingPacketData.create(from, to);
    final Bytes serialized = RLP.encode(packet::writeTo);
    final PingPacketData deserialized = PingPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getFrom()).isEqualTo(from);
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getExpiration()).isGreaterThan(currentTimeSec);
  }

  @Test
  public void readFrom() {
    final int version = 4;
    final Endpoint from = new Endpoint("127.0.0.1", 30303, OptionalInt.of(30303));
    final Endpoint to = new Endpoint("127.0.0.2", 30303, OptionalInt.empty());
    final long time = System.currentTimeMillis();

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeIntScalar(version);
    from.encodeStandalone(out);
    to.encodeStandalone(out);
    out.writeLongScalar(time);
    out.endList();

    final Bytes serialized = out.encoded();
    final PingPacketData deserialized = PingPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getFrom()).isEqualTo(from);
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
  }

  @Test
  public void readFrom_withExtraFields() {
    final int version = 4;
    final Endpoint from = new Endpoint("127.0.0.1", 30303, OptionalInt.of(30303));
    final Endpoint to = new Endpoint("127.0.0.2", 30303, OptionalInt.empty());
    final long time = System.currentTimeMillis();

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeIntScalar(version);
    from.encodeStandalone(out);
    to.encodeStandalone(out);
    out.writeLongScalar(time);
    // Add extra field
    out.writeLongScalar(11);
    out.endList();

    final Bytes serialized = out.encoded();
    final PingPacketData deserialized = PingPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getFrom()).isEqualTo(from);
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
  }

  @Test
  public void readFrom_unknownVersion() {
    final int version = 99;
    final Endpoint from = new Endpoint("127.0.0.1", 30303, OptionalInt.of(30303));
    final Endpoint to = new Endpoint("127.0.0.2", 30303, OptionalInt.empty());
    final long time = System.currentTimeMillis();

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeIntScalar(version);
    from.encodeStandalone(out);
    to.encodeStandalone(out);
    out.writeLongScalar(time);
    out.endList();

    final Bytes serialized = out.encoded();
    final PingPacketData deserialized = PingPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getFrom()).isEqualTo(from);
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
  }
}
