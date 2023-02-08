/*
 * Copyright Hyperledger Besu Contributors.
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

package org.hyperledger.besu.evmtool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Wei;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Test;
import picocli.CommandLine;

public class B11rCommandTest {

  @Test
  public void testSingleValidViaInput() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais =
        new ByteArrayInputStream(
            """
            {
                 "header": {
                     "parentHash": "0x0000000000000000000000000000000000000000000000000000000000000000",
                     "sha3Uncles": "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
                     "miner": "0x0000000000000000000000000000000000000000",
                     "stateRoot": "0x369ba8b2e5b32d675d07933d6fb851d97d3ca66c60a829f7356163d92ae0439a",
                     "transactionsRoot": "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
                     "receiptsRoot": "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
                     "logsBloom": "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                     "difficulty": "0x20000",
                     "number": "0x0",
                     "gasLimit": "0x16345785d8a0000",
                     "gasUsed": "0xb1a2bc2ec50000",
                     "timestamp": "0x0",
                     "extraData": "0x00",
                     "mixHash": "0x0000000000000000000000000000000000000000000000000000000000000000",
                     "nonce": "0x0000000000000000",
                     "baseFeePerGas": "0x7"
                 },
                 "txs": "",
                 "uncles": [],
                 "clique": null
             }"""
                .getBytes(UTF_8));

    final B11rSubCommand b11rSubCommand = new B11rSubCommand(bais, new PrintStream(baos));
    CommandLine cmdLine = new CommandLine(b11rSubCommand);
    cmdLine.registerConverter(Wei.class, arg -> Wei.of(Long.parseUnsignedLong(arg)));
    cmdLine.execute(
        // "b11r",
        "--input.header=stdin",
        "--input.txs=stdin",
        "--input.ommers=stdin",
        "--seal.clique=stdin",
        "--output.block=stdout");
    assertThat(baos.toString(UTF_8))
        .isEqualTo(
            """
             {
              "rlp" : "0xf90205f90200a00000000000000000000000000000000000000000000000000000000000000000a01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347940000000000000000000000000000000000000000a0369ba8b2e5b32d675d07933d6fb851d97d3ca66c60a829f7356163d92ae0439aa056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000830200008088016345785d8a000087b1a2bc2ec500008000a0000000000000000000000000000000000000000000000000000000000000000088000000000000000007c0c0",
              "hash" : "0xe3c84688fa32c20955c535c7d25b5b4b196079e40a9c47c9cb1edb5e58b3dce5"
             }
             """);
  }
}
