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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.enrrequest;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EnrRequestPacketDataRlpWriterTest {
  private EnrRequestPacketDataRlpWriter writer;

  @BeforeEach
  public void beforeTest() {
    writer = new EnrRequestPacketDataRlpWriter();
  }

  @Test
  public void testWriteTo() {
    final long expiration = 123;
    final EnrRequestPacketData enrRequestPacketData = new EnrRequestPacketData(expiration);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();

    writer.writeTo(enrRequestPacketData, out);

    Assertions.assertEquals("0xc17b", out.encoded().toHexString());
  }
}
