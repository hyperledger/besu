/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.p2p.discovery.internal;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.p2p.discovery.Endpoint;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.OptionalInt;

import org.junit.Test;

public class PingPacketDataTest {

  @Test
  public void serializeDeserialize() {
    final long currentTime = TestClock.fixed().millis();

    final Endpoint from = new Endpoint("127.0.0.1", 30303, OptionalInt.of(30303));
    final Endpoint to = new Endpoint("127.0.0.2", 30303, OptionalInt.empty());
    final PingPacketData packet = PingPacketData.create(from, to, TestClock.fixed());
    final BytesValue serialized = RLP.encode(packet::writeTo);
    final PingPacketData deserialized = PingPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getFrom()).isEqualTo(from);
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getExpiration()).isGreaterThan(currentTime);
  }

  @Test
  public void readFrom() {
    final int version = 4;
    final Endpoint from = new Endpoint("127.0.0.1", 30303, OptionalInt.of(30303));
    final Endpoint to = new Endpoint("127.0.0.2", 30303, OptionalInt.empty());
    final long time = TestClock.fixed().millis();

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeIntScalar(version);
    from.encodeStandalone(out);
    to.encodeStandalone(out);
    out.writeLongScalar(time);
    out.endList();

    final BytesValue serialized = out.encoded();
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
    final long time = TestClock.fixed().millis();

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeIntScalar(version);
    from.encodeStandalone(out);
    to.encodeStandalone(out);
    out.writeLongScalar(time);
    // Add extra field
    out.writeLongScalar(11);
    out.endList();

    final BytesValue serialized = out.encoded();
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
    final long time = TestClock.fixed().millis();

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeIntScalar(version);
    from.encodeStandalone(out);
    to.encodeStandalone(out);
    out.writeLongScalar(time);
    out.endList();

    final BytesValue serialized = out.encoded();
    final PingPacketData deserialized = PingPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getFrom()).isEqualTo(from);
    assertThat(deserialized.getTo()).isEqualTo(to);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
  }
}
