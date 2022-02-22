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

public class CodeCacheTest {

  private final String op = Bytes.of(JumpDestOperation.OPCODE).toUnprefixedHexString();

  @Test
  public void testScale() {
    final Bytes contractBytes =
        Bytes.fromHexString("0xDEAD" + op + "BEEF" + op + "B0B0" + op + "C0DE" + op + "FACE");
    final CodeScale scale = new CodeScale();
    final Code contractCode = Code.createLegacyCode(contractBytes, Hash.hash(contractBytes));
    final int weight = scale.weigh(contractCode.getCodeHash(), contractCode);
    assertThat(weight)
        .isEqualTo(contractCode.getCodeHash().size() + (contractBytes.size() * 9 + 7) / 8);
  }
}
