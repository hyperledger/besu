/*
 * Copyright Hyperledger Besu Contributors
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

package org.hyperledger.besu.evm.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.operation.JumpDestOperation;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class JumpDestCacheTest {

  private final String op = Bytes.of(JumpDestOperation.OPCODE).toUnprefixedHexString();

  @Test
  public void testScale() {
    Bytes contractBytes =
        Bytes.fromHexString("0xDEAD" + op + "BEEF" + op + "B0B0" + op + "C0DE" + op + "FACE");
    // 3rd bit, 6th bit, 9th bit, 12th bit
    long[] jumpDests = {4 + 32 + 256 + 2048};
    CodeScale scale = new CodeScale();
    Code contractCode = new Code(contractBytes, Hash.hash(contractBytes), jumpDests);
    int weight = scale.weigh(contractCode.getCodeHash(), contractCode.getValidJumpDestinations());
    assertThat(weight).isEqualTo(contractCode.getCodeHash().size() + jumpDests.length * 8);
  }
}
