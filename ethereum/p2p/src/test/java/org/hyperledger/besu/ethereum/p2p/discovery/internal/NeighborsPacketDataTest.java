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
import static org.hyperledger.besu.ethereum.p2p.peers.PeerTestHelper.enode;

import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class NeighborsPacketDataTest {

  @Test
  public void serializeDeserialize() {
    final long timeSec = Instant.now().getEpochSecond();
    final List<DiscoveryPeer> peers =
        Arrays.asList(DiscoveryPeer.fromEnode(enode()), DiscoveryPeer.fromEnode(enode()));

    final NeighborsPacketData packet = NeighborsPacketData.create(peers);
    final Bytes serialized = RLP.encode(packet::writeTo);
    final NeighborsPacketData deserialized = NeighborsPacketData.readFrom(RLP.input(serialized));

    assertThat(deserialized.getNodes()).isEqualTo(peers);
    assertThat(deserialized.getExpiration()).isGreaterThan(timeSec);
  }

  @Test
  public void readFrom() {
    final long timeSec = Instant.now().getEpochSecond();
    final List<DiscoveryPeer> peers =
        Arrays.asList(DiscoveryPeer.fromEnode(enode()), DiscoveryPeer.fromEnode(enode()));

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeList(peers, DiscoveryPeer::writeTo);
    out.writeLongScalar(timeSec);
    out.endList();
    Bytes encoded = out.encoded();

    final NeighborsPacketData deserialized = NeighborsPacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getNodes()).isEqualTo(peers);
    assertThat(deserialized.getExpiration()).isEqualTo(timeSec);
  }

  @Test
  public void readFrom_extraFields() {
    final long time = System.currentTimeMillis();
    final List<DiscoveryPeer> peers =
        Arrays.asList(DiscoveryPeer.fromEnode(enode()), DiscoveryPeer.fromEnode(enode()));

    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeList(peers, DiscoveryPeer::writeTo);
    out.writeLongScalar(time);
    out.endList();
    Bytes encoded = out.encoded();

    final NeighborsPacketData deserialized = NeighborsPacketData.readFrom(RLP.input(encoded));
    assertThat(deserialized.getNodes()).isEqualTo(peers);
    assertThat(deserialized.getExpiration()).isEqualTo(time);
  }
}
