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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet;

import org.hyperledger.besu.crypto.SECPSignature;

import com.google.common.base.Strings;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class PacketSignatureEncoderTest {

  private PacketSignatureEncoder packetSignatureEncoder;

  @BeforeEach
  public void beforeTest() {
    packetSignatureEncoder = new PacketSignatureEncoder();
  }

  @Test
  @SuppressWarnings("InlineMeInliner")
  public void testEncodeSignature() {
    final SECPSignature signature = Mockito.mock(SECPSignature.class);
    Bytes r = Bytes.repeat((byte) 0x01, 32);
    Bytes s = Bytes.repeat((byte) 0x02, 32);
    byte recId = (byte) 0x03;
    Mockito.when(signature.getR()).thenReturn(r.toBigInteger());
    Mockito.when(signature.getS()).thenReturn(s.toBigInteger());
    Mockito.when(signature.getRecId()).thenReturn(recId);

    Bytes encodedSignature = packetSignatureEncoder.encodeSignature(signature);

    Mockito.verify(signature).getR();
    Mockito.verify(signature).getS();
    Mockito.verify(signature).getRecId();

    Assertions.assertEquals(
        "0x" + Strings.repeat("01", 32) + Strings.repeat("02", 32) + "03",
        encodedSignature.toHexString());
  }
}
