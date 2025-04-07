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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.proof;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

class StorageEntryProofTest {

  @Test
  public void testKey() {
    // zero
    testKey(UInt256.ZERO, "0x0");
    testKey(UInt256.fromHexString("0x00"), "0x0");

    // no leading zeros
    testKey(UInt256.fromHexString("0x10"), "0x10");

    // single leading zero, expect it to remain
    testKey(UInt256.fromHexString("0x0aa0"), "0x0aa0");
    testKey(UInt256.fromHexString("0x01"), "0x01");
    testKey(UInt256.ONE, "0x01");

    // multiple leading zeros, they should not
    testKey(UInt256.fromHexString("0x00a2"), "0xa2");
    testKey(UInt256.fromHexString("0x00a200"), "0xa200");
  }

  void testKey(final UInt256 key, final String expected) {
    StorageEntryProof s = new StorageEntryProof(key, UInt256.ZERO, List.of());
    assertEquals(expected, s.getKey());
  }
}
