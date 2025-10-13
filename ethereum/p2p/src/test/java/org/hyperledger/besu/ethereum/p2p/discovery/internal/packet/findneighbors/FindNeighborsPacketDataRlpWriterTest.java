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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.findneighbors;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FindNeighborsPacketDataRlpWriterTest {

  private FindNeighborsPacketDataRlpWriter writer;

  @BeforeEach
  public void beforeTest() {
    writer = new FindNeighborsPacketDataRlpWriter();
  }

  @Test
  public void testWriteTo() {
    final Bytes target = Bytes.fromHexString("0xdeadbeef");
    final long expiry = 123;
    final FindNeighborsPacketData findNeighborsPacketData =
        new FindNeighborsPacketData(target, expiry);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();

    writer.writeTo(findNeighborsPacketData, out);

    Assertions.assertEquals("0xc684deadbeef7b", out.encoded().toHexString());
  }
}
