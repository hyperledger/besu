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
package org.hyperledger.besu.evm;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.fluent.EVMExecutor;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class StandardJsonTracerTest {

  @Test
  void eip3155ModifiedTestCase() {

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);
    var executor = EVMExecutor.evm(EvmSpecVersion.ISTANBUL);
    StandardJsonTracer tracer = new StandardJsonTracer(out, true, true, true, false);
    executor.tracer(tracer);
    executor.gas(10_000_000_000L);

    var codeBytes = Bytes.fromHexString("0x604080536040604055604060006040600060025afa6040f3");
    executor.execute(codeBytes, Bytes.EMPTY, Wei.ZERO, Address.ZERO);

    // differences from the EIP-3155 test case
    //  (a) the test case was written when EIP-2315 was part of the pending hard fork, so
    //      returnStack was a valid field.  It no longer appears in any traces.
    //  (b) the summary line is omitted
    //  (c) pc:3 is in error, the size of the memory before the first MSTORE8 is zero.
    //  (d) if memory is zero length, it is not included even if `showMemory` is true
    //  (e) if return data is zero length or null, it is not included even if `showReturnData` is
    // true
    //  (f) if error is zero length or null it is not included.
    assertThat(baos)
        .hasToString(
            """
                        {"pc":0,"op":96,"gas":"0x2540be400","gasCost":"0x3","memSize":0,"stack":[],"depth":1,"refund":0,"opName":"PUSH1"}
                        {"pc":2,"op":128,"gas":"0x2540be3fd","gasCost":"0x3","memSize":0,"stack":["0x40"],"depth":1,"refund":0,"opName":"DUP1"}
                        {"pc":3,"op":83,"gas":"0x2540be3fa","gasCost":"0xc","memSize":0,"stack":["0x40","0x40"],"depth":1,"refund":0,"opName":"MSTORE8"}
                        {"pc":4,"op":96,"gas":"0x2540be3ee","gasCost":"0x3","memory":"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000","memSize":96,"stack":[],"depth":1,"refund":0,"opName":"PUSH1"}
                        {"pc":6,"op":96,"gas":"0x2540be3eb","gasCost":"0x3","memory":"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000","memSize":96,"stack":["0x40"],"depth":1,"refund":0,"opName":"PUSH1"}
                        {"pc":8,"op":85,"gas":"0x2540be3e8","gasCost":"0x4e20","memory":"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000","memSize":96,"stack":["0x40","0x40"],"depth":1,"refund":0,"opName":"SSTORE"}
                        {"pc":9,"op":96,"gas":"0x2540b95c8","gasCost":"0x3","memory":"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000","memSize":96,"stack":[],"depth":1,"refund":0,"opName":"PUSH1"}
                        {"pc":11,"op":96,"gas":"0x2540b95c5","gasCost":"0x3","memory":"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000","memSize":96,"stack":["0x40"],"depth":1,"refund":0,"opName":"PUSH1"}
                        {"pc":13,"op":96,"gas":"0x2540b95c2","gasCost":"0x3","memory":"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000","memSize":96,"stack":["0x40","0x0"],"depth":1,"refund":0,"opName":"PUSH1"}
                        {"pc":15,"op":96,"gas":"0x2540b95bf","gasCost":"0x3","memory":"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000","memSize":96,"stack":["0x40","0x0","0x40"],"depth":1,"refund":0,"opName":"PUSH1"}
                        {"pc":17,"op":96,"gas":"0x2540b95bc","gasCost":"0x3","memory":"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000","memSize":96,"stack":["0x40","0x0","0x40","0x0"],"depth":1,"refund":0,"opName":"PUSH1"}
                        {"pc":19,"op":90,"gas":"0x2540b95b9","gasCost":"0x2","memory":"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000","memSize":96,"stack":["0x40","0x0","0x40","0x0","0x2"],"depth":1,"refund":0,"opName":"GAS"}
                        {"pc":20,"op":250,"gas":"0x2540b95b7","gasCost":"0x24abb676c","memory":"0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000","memSize":96,"stack":["0x40","0x0","0x40","0x0","0x2","0x2540b95b7"],"depth":1,"refund":0,"opName":"STATICCALL"}
                        {"pc":21,"op":96,"gas":"0x2540b92a7","gasCost":"0x3","memory":"0xf5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b00000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000","memSize":96,"stack":["0x1"],"returnData":"0xf5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b","depth":1,"refund":0,"opName":"PUSH1"}
                        {"pc":23,"op":243,"gas":"0x2540b92a4","gasCost":"0x0","memory":"0xf5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b00000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000","memSize":96,"stack":["0x1","0x40"],"returnData":"0xf5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b","depth":1,"refund":0,"opName":"RETURN"}
                        """);
  }

  @Test
  void updatedStorageTestCase() {

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);
    var executor = EVMExecutor.evm(EvmSpecVersion.ISTANBUL);
    StandardJsonTracer tracer = new StandardJsonTracer(out, false, false, false, true);
    executor.tracer(tracer);
    executor.gas(10_000_000_000L);

    var codeBytes = Bytes.fromHexString("0x604080536040604055604060006040600060025afa6040f3");
    executor.execute(codeBytes, Bytes.EMPTY, Wei.ZERO, Address.ZERO);

    assertThat(baos)
        .hasToString(
            """
                        {"pc":0,"op":96,"gas":"0x2540be400","gasCost":"0x3","memSize":0,"depth":1,"refund":0,"opName":"PUSH1"}
                        {"pc":2,"op":128,"gas":"0x2540be3fd","gasCost":"0x3","memSize":0,"depth":1,"refund":0,"opName":"DUP1"}
                        {"pc":3,"op":83,"gas":"0x2540be3fa","gasCost":"0xc","memSize":0,"depth":1,"refund":0,"opName":"MSTORE8"}
                        {"pc":4,"op":96,"gas":"0x2540be3ee","gasCost":"0x3","memSize":96,"depth":1,"refund":0,"opName":"PUSH1"}
                        {"pc":6,"op":96,"gas":"0x2540be3eb","gasCost":"0x3","memSize":96,"depth":1,"refund":0,"opName":"PUSH1"}
                        {"pc":8,"op":85,"gas":"0x2540be3e8","gasCost":"0x4e20","memSize":96,"depth":1,"refund":0,"opName":"SSTORE"}
                        {"pc":9,"op":96,"gas":"0x2540b95c8","gasCost":"0x3","memSize":96,"depth":1,"refund":0,"opName":"PUSH1","storage":{"0x40":"0x40"}}
                        {"pc":11,"op":96,"gas":"0x2540b95c5","gasCost":"0x3","memSize":96,"depth":1,"refund":0,"opName":"PUSH1","storage":{"0x40":"0x40"}}
                        {"pc":13,"op":96,"gas":"0x2540b95c2","gasCost":"0x3","memSize":96,"depth":1,"refund":0,"opName":"PUSH1","storage":{"0x40":"0x40"}}
                        {"pc":15,"op":96,"gas":"0x2540b95bf","gasCost":"0x3","memSize":96,"depth":1,"refund":0,"opName":"PUSH1","storage":{"0x40":"0x40"}}
                        {"pc":17,"op":96,"gas":"0x2540b95bc","gasCost":"0x3","memSize":96,"depth":1,"refund":0,"opName":"PUSH1","storage":{"0x40":"0x40"}}
                        {"pc":19,"op":90,"gas":"0x2540b95b9","gasCost":"0x2","memSize":96,"depth":1,"refund":0,"opName":"GAS","storage":{"0x40":"0x40"}}
                        {"pc":20,"op":250,"gas":"0x2540b95b7","gasCost":"0x24abb676c","memSize":96,"depth":1,"refund":0,"opName":"STATICCALL","storage":{"0x40":"0x40"}}
                        {"pc":21,"op":96,"gas":"0x2540b92a7","gasCost":"0x3","memSize":96,"depth":1,"refund":0,"opName":"PUSH1","storage":{"0x40":"0x40"}}
                        {"pc":23,"op":243,"gas":"0x2540b92a4","gasCost":"0x0","memSize":96,"depth":1,"refund":0,"opName":"RETURN","storage":{"0x40":"0x40"}}
                        """);
  }
}
