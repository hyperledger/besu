/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.operation.JumpDestOperation;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class CodeCacheTest {

  private final String op = Bytes.of(JumpDestOperation.OPCODE).toUnprefixedHexString();

  @Test
  void testScale() {
    EVM evm = MainnetEVMs.osaka(EvmConfiguration.DEFAULT);
    final Bytes contractBytes =
        Bytes.fromHexString("0xDEAD" + op + "BEEF" + op + "B0B0" + op + "C0DE" + op + "FACE");
    final CodeScale scale = new CodeScale();
    final Code contractCode = evm.getCodeUncached(contractBytes);
    final int weight = scale.weigh(contractCode.getCodeHash(), contractCode);
    assertThat(weight)
        .isEqualTo(contractCode.getCodeHash().size() + (contractBytes.size() * 9 + 7) / 8);
  }
}
