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
package org.hyperledger.besu.util.ssz;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MerkleizerTest {

  @Test
  public void testMerkleizeChunks() {
    Merkleizer merkleizer = new Merkleizer();
    List<Bytes32> chunks =
        List.of(
            Bytes32.leftPad(Bytes.fromHexString("01"), (byte) 0x00),
            Bytes32.leftPad(Bytes.fromHexString("02"), (byte) 0x00),
            Bytes32.leftPad(Bytes.fromHexString("03"), (byte) 0x00),
            Bytes32.leftPad(Bytes.fromHexString("04"), (byte) 0x00));

    Bytes32 result = merkleizer.merkleizeChunks(chunks);
    Assertions.assertEquals(
        "0xd7351286df93d1e31e51c21378fba9f9c7c14c3a8f621065069809b6e635ae0a", result.toHexString());
  }

  @Test
  public void testMerkleizeChunksPadToSize() {
    Merkleizer merkleizer = new Merkleizer();
    List<Bytes32> chunks =
        List.of(
            Bytes32.leftPad(Bytes.fromHexString("01"), (byte) 0x00),
            Bytes32.leftPad(Bytes.fromHexString("02"), (byte) 0x00),
            Bytes32.leftPad(Bytes.fromHexString("03"), (byte) 0x00));

    Bytes32 result = merkleizer.merkleizeChunks(chunks, 4);
    Assertions.assertEquals(
        "0xdfea42101f94476e3b2e26f2a3e23505a696a6fd3d3ded213cece4adb19cb9ac", result.toHexString());
  }

  @Test
  public void testMixinLength() {
    Merkleizer merkleizer = new Merkleizer();
    Bytes32 rootHash = Bytes32.leftPad(Bytes.fromHexString("01"), (byte) 0x00);

    Bytes32 result = merkleizer.mixinLength(rootHash, UInt256.ONE);
    Assertions.assertEquals(
        "0x9460060257cf1b1c720d01aab3842bd30abb9ce86d8a5221043c7eb7961d1364", result.toHexString());
  }
}
