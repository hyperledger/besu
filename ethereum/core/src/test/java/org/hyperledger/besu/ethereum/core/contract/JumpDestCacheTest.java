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

package org.hyperledger.besu.ethereum.core.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.operations.JumpDestOperation;

import java.util.BitSet;

import org.apache.tuweni.bytes.Bytes;
import org.junit.BeforeClass;
import org.junit.Test;

public class JumpDestCacheTest {

  private final String op = Bytes.of(JumpDestOperation.OPCODE).toUnprefixedHexString();

  @BeforeClass
  public static void resetConfig() {
    JumpDestCache.destroy(); // tests run in parallel, often with defaults
  }

  @Test
  public void testScale() {
    Bytes contractBytes =
        Bytes.fromHexString("0xDEAD" + op + "BEEF" + op + "B0B0" + op + "C0DE" + op + "FACE");
    BitSet jumpDests = new BitSet(contractBytes.size());
    jumpDests.set(2);
    jumpDests.set(4);
    jumpDests.set(6);
    jumpDests.set(8);
    CodeScale scale = new CodeScale();
    Code contractCode = new Code(contractBytes, Hash.hash(contractBytes), jumpDests);
    int weight = scale.weigh(contractCode.getCodeHash(), contractCode.getValidJumpDestinations());
    assertThat(weight).isEqualTo(contractCode.getCodeHash().size() + jumpDests.size() / 8);
  }
}
