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

import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import org.junit.Test;

public class FindNeighborsPacketDataTest {
  @Test
  public void serializeDeserialize() {
    final long time = TestClock.fixed().millis();
    final BytesValue target = Peer.randomId();

    final FindNeighborsPacketData packet =
        FindNeighborsPacketData.create(target, TestClock.fixed());
    final BytesValue serialized = RLP.encode(packet::writeTo);
    final FindNeighborsPacketData deserialized =
        FindNeighborsPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getTarget()).isEqualTo(target);
    assertThat(deserialized.getExpiration()).isGreaterThan(time);
  }

  @Test
  public void readFrom() {
    final long time = TestClock.fixed().millis();
    final BytesValue target = Peer.randomId();

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeBytesValue(target);
    out.writeLongScalar(time);
    out.endList();
    final BytesValue encoded = out.encoded();

    final FindNeighborsPacketData deserialized =
        FindNeighborsPacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getTarget()).isEqualTo(target);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
  }

  @Test
  public void readFrom_withExtraFields() {
    final long time = TestClock.fixed().millis();
    final BytesValue target = Peer.randomId();

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeBytesValue(target);
    out.writeLongScalar(time);
    // Add extra list elements
    out.writeLong(123L);
    out.endList();
    final BytesValue encoded = out.encoded();

    final FindNeighborsPacketData deserialized =
        FindNeighborsPacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getTarget()).isEqualTo(target);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
  }
}
