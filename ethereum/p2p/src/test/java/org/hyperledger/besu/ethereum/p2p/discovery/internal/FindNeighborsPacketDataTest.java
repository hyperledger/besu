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

import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.time.Instant;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class FindNeighborsPacketDataTest {
  @Test
  public void serializeDeserialize() {
    final long timeSec = Instant.now().getEpochSecond();
    final Bytes target = Peer.randomId();

    final FindNeighborsPacketData packet = FindNeighborsPacketData.create(target);
    final Bytes serialized = RLP.encode(packet::writeTo);
    final FindNeighborsPacketData deserialized =
        FindNeighborsPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getTarget()).isEqualTo(target);
    assertThat(deserialized.getExpiration()).isGreaterThan(timeSec);
  }

  @Test
  public void readFrom() {
    final long time = System.currentTimeMillis();
    final Bytes target = Peer.randomId();

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeBytes(target);
    out.writeLongScalar(time);
    out.endList();
    final Bytes encoded = out.encoded();

    final FindNeighborsPacketData deserialized =
        FindNeighborsPacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getTarget()).isEqualTo(target);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
  }

  @Test
  public void readFrom_withExtraFields() {
    final long time = System.currentTimeMillis();
    final Bytes target = Peer.randomId();

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeBytes(target);
    out.writeLongScalar(time);
    // Add extra list elements
    out.writeLong(123L);
    out.endList();
    final Bytes encoded = out.encoded();

    final FindNeighborsPacketData deserialized =
        FindNeighborsPacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getTarget()).isEqualTo(target);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
  }
}
