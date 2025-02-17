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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.pong;

import org.hyperledger.besu.ethereum.p2p.discovery.Endpoint;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PongPacketDataRlpWriterTest {

  private PongPacketDataRlpWriter writer;

  @BeforeEach
  public void beforeTest() {
    writer = new PongPacketDataRlpWriter();
  }

  @Test
  public void testWriteTo() {
    final Endpoint to = new Endpoint("10.0.0.1", 30303, Optional.of(1234));
    final Bytes pingHash = Bytes.fromHexString("0xdeadbeef");
    final long expiration = 567;
    final UInt64 enrSeq = UInt64.valueOf(8910);
    final PongPacketData pongPacketData = new PongPacketData(to, pingHash, expiration, enrSeq);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();

    writer.writeTo(pongPacketData, out);

    Assertions.assertEquals(
        "0xd7cb840a00000182765f8204d284deadbeef8202378222ce", out.encoded().toHexString());
  }
}
