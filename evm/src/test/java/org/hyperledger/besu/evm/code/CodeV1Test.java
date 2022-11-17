/*
 * Copyright contributors to Hyperledger Besu
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
 *
 */

package org.hyperledger.besu.evm.code;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class CodeV1Test {

  @Test
  void calculatesJumpDestMap() {
    String codeHex = "0xEF000101000F006001600055600D5660026000555B00";
    final EOFLayout layout = EOFLayout.parseEOF(Bytes.fromHexString(codeHex));

    long[] jumpDest = OpcodesV1.validateAndCalculateJumpDests(layout.getSections()[1]);

    assertThat(jumpDest).containsExactly(0x2000);
  }
}
