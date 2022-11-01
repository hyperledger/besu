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

package org.hyperledger.besu.evm;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.fluent.EVMExecutor;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class StandardJsonTracerTest {

  @Test
  void eip3155TestCase() {

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);
    var executor = EVMExecutor.istanbul(EvmConfiguration.DEFAULT);
    StandardJsonTracer tracer = new StandardJsonTracer(out, true);
    executor.tracer(tracer);
    executor.gas(10_000_000_000L);

    var codeBytes = Bytes.fromHexString("0x604080536040604055604060006040600060025afa6040f3");
    executor.execute(codeBytes, Bytes.EMPTY, Wei.ZERO, Address.ZERO);

    // differences from the EIP-3155 test case
    //  (a) the test case was written when EIP-2315 was part of the pending hard fork, so
    //      returnStack was a valid field.  It no longer appears in any traces.
    //  (b) the summary line is omitted
    //  (c) pc:3 is in error, the size of the memory before the first MSTORE8 is zero.
    //  (d) we don't report call requested gas in CALL series operations as a gas cost, just the EVM
    //      consumed gas
    assertThat(baos)
        .hasToString(
            "{\"pc\":0,\"op\":96,\"gas\":\"0x2540be400\",\"gasCost\":\"0x3\",\"memory\":\"0x\",\"memSize\":0,\"stack\":[],\"returnData\":\"0x\",\"depth\":1,\"refund\":0,\"opName\":\"PUSH1\",\"error\":\"\"}\n"
                + "{\"pc\":2,\"op\":128,\"gas\":\"0x2540be3fd\",\"gasCost\":\"0x3\",\"memory\":\"0x\",\"memSize\":0,\"stack\":[\"0x40\"],\"returnData\":\"0x\",\"depth\":1,\"refund\":0,\"opName\":\"DUP1\",\"error\":\"\"}\n"
                + "{\"pc\":3,\"op\":83,\"gas\":\"0x2540be3fa\",\"gasCost\":\"0xc\",\"memory\":\"0x\",\"memSize\":0,\"stack\":[\"0x40\",\"0x40\"],\"returnData\":\"0x\",\"depth\":1,\"refund\":0,\"opName\":\"MSTORE8\",\"error\":\"\"}\n"
                + "{\"pc\":4,\"op\":96,\"gas\":\"0x2540be3ee\",\"gasCost\":\"0x3\",\"memory\":\"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000\",\"memSize\":96,\"stack\":[],\"returnData\":\"0x\",\"depth\":1,\"refund\":0,\"opName\":\"PUSH1\",\"error\":\"\"}\n"
                + "{\"pc\":6,\"op\":96,\"gas\":\"0x2540be3eb\",\"gasCost\":\"0x3\",\"memory\":\"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000\",\"memSize\":96,\"stack\":[\"0x40\"],\"returnData\":\"0x\",\"depth\":1,\"refund\":0,\"opName\":\"PUSH1\",\"error\":\"\"}\n"
                + "{\"pc\":8,\"op\":85,\"gas\":\"0x2540be3e8\",\"gasCost\":\"0x4e20\",\"memory\":\"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000\",\"memSize\":96,\"stack\":[\"0x40\",\"0x40\"],\"returnData\":\"0x\",\"depth\":1,\"refund\":0,\"opName\":\"SSTORE\",\"error\":\"\"}\n"
                + "{\"pc\":9,\"op\":96,\"gas\":\"0x2540b95c8\",\"gasCost\":\"0x3\",\"memory\":\"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000\",\"memSize\":96,\"stack\":[],\"returnData\":\"0x\",\"depth\":1,\"refund\":0,\"opName\":\"PUSH1\",\"error\":\"\"}\n"
                + "{\"pc\":11,\"op\":96,\"gas\":\"0x2540b95c5\",\"gasCost\":\"0x3\",\"memory\":\"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000\",\"memSize\":96,\"stack\":[\"0x40\"],\"returnData\":\"0x\",\"depth\":1,\"refund\":0,\"opName\":\"PUSH1\",\"error\":\"\"}\n"
                + "{\"pc\":13,\"op\":96,\"gas\":\"0x2540b95c2\",\"gasCost\":\"0x3\",\"memory\":\"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000\",\"memSize\":96,\"stack\":[\"0x40\",\"0x0\"],\"returnData\":\"0x\",\"depth\":1,\"refund\":0,\"opName\":\"PUSH1\",\"error\":\"\"}\n"
                + "{\"pc\":15,\"op\":96,\"gas\":\"0x2540b95bf\",\"gasCost\":\"0x3\",\"memory\":\"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000\",\"memSize\":96,\"stack\":[\"0x40\",\"0x0\",\"0x40\"],\"returnData\":\"0x\",\"depth\":1,\"refund\":0,\"opName\":\"PUSH1\",\"error\":\"\"}\n"
                + "{\"pc\":17,\"op\":96,\"gas\":\"0x2540b95bc\",\"gasCost\":\"0x3\",\"memory\":\"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000\",\"memSize\":96,\"stack\":[\"0x40\",\"0x0\",\"0x40\",\"0x0\"],\"returnData\":\"0x\",\"depth\":1,\"refund\":0,\"opName\":\"PUSH1\",\"error\":\"\"}\n"
                + "{\"pc\":19,\"op\":90,\"gas\":\"0x2540b95b9\",\"gasCost\":\"0x2\",\"memory\":\"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000\",\"memSize\":96,\"stack\":[\"0x40\",\"0x0\",\"0x40\",\"0x0\",\"0x2\"],\"returnData\":\"0x\",\"depth\":1,\"refund\":0,\"opName\":\"GAS\",\"error\":\"\"}\n"
                + "{\"pc\":20,\"op\":250,\"gas\":\"0x2540b95b7\",\"gasCost\":\"0x2bc\",\"memory\":\"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000\",\"memSize\":96,\"stack\":[\"0x40\",\"0x0\",\"0x40\",\"0x0\",\"0x2\",\"0x2540b95b7\"],\"returnData\":\"0x\",\"depth\":1,\"refund\":0,\"opName\":\"STATICCALL\",\"error\":\"\"}\n"
                + "{\"pc\":21,\"op\":96,\"gas\":\"0x2540b92a7\",\"gasCost\":\"0x3\",\"memory\":\"0xf5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b00000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000\",\"memSize\":96,\"stack\":[\"0x1\"],\"returnData\":\"0xf5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b\",\"depth\":1,\"refund\":0,\"opName\":\"PUSH1\",\"error\":\"\"}\n"
                + "{\"pc\":23,\"op\":243,\"gas\":\"0x2540b92a4\",\"gasCost\":\"0x0\",\"memory\":\"0xf5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b00000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000\",\"memSize\":96,\"stack\":[\"0x1\",\"0x40\"],\"returnData\":\"0xf5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b\",\"depth\":1,\"refund\":0,\"opName\":\"RETURN\",\"error\":\"\"}\n");
  }
}
