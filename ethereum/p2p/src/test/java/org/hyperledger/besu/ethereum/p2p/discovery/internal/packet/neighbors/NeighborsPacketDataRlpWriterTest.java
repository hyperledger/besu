/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.neighbors;

import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.Endpoint;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NeighborsPacketDataRlpWriterTest {
  private NeighborsPacketDataRlpWriter writer;

  @BeforeEach
  public void beforeTest() {
    writer = new NeighborsPacketDataRlpWriter();
  }

  @Test
  public void testWriteTo() {
    String idHex =
        "0x0fcd214b55ac7ad8f1d179b7a8ea637271226ab8f5ee3ec6e12d2e27b90e0ed25e1dc9d2dc847141ee7cda64c4c7d937fe37d977bef14f277e7a4273920dcc20";
    final List<DiscoveryPeer> peers =
        List.of(
            DiscoveryPeer.fromIdAndEndpoint(
                Bytes.fromHexString(idHex), new Endpoint("10.0.0.1", 30303, Optional.of(123))));
    final long expiration = 456;
    final NeighborsPacketData neighborsPacketData = new NeighborsPacketData(peers, expiration);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();

    writer.writeTo(neighborsPacketData, out);

    String expectedHex =
        "0xf852f84df84b840a00000182765f7bb8400fcd214b55ac7ad8f1d179b7a8ea637271226ab8f5ee3ec6e12d2e27b90e0ed25e1dc9d2dc847141ee7cda64c4c7d937fe37d977bef14f277e7a4273920dcc208201c8";
    Assertions.assertEquals(expectedHex, out.encoded().toHexString());
  }
}
