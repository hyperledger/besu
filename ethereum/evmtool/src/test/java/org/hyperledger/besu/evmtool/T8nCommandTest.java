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

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class T8nCommandTest {

  @Test
  void testSingleValidViaInput() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais =
        new ByteArrayInputStream(
            """
            {
                "alloc": {
                    "0x1000000000000000000000000000000000000000": {
                        "nonce": "0x00",
                        "balance": "0x0ba1a9ce0ba1a9ce",
                        "code": "0x6010565b6000828201905092915050565b601a600260016003565b60005560206000f3",
                        "storage": {}
                    },
                    "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b": {
                        "nonce": "0x00",
                        "balance": "0x0ba1a9ce0ba1a9ce",
                        "code": "0x",
                        "storage": {}
                    }
                },
                "txs": [
                    {
                        "type": "0x0",
                        "chainId": "0x0",
                        "nonce": "0x0",
                        "gasPrice": "0xa",
                        "gas": "0x7a120",
                        "value": "0x0",
                        "input": "0x",
                        "to": "0x1000000000000000000000000000000000000000",
                        "protected": false,
                        "secretKey": "0x45a915e4d060149eb4365960e6a7a45f334393093061116b197e3240065ff2d8",
                        "v": "0x0",
                        "r": "0x0",
                        "s": "0x0"
                    }
                ],
                "env": {
                    "currentCoinbase": "0x0000000000000000000000000000000000000000",
                    "currentDifficulty": "0x0",
                    "currentGasLimit": "0x0",
                    "currentNumber": "0",
                    "currentTimestamp": "0"
                }
            }"""
                .getBytes(UTF_8));

    EvmToolCommand evmTool = new EvmToolCommand();
    final T8nSubCommand t8nSubCommand = new T8nSubCommand(evmTool, bais, new PrintStream(baos));
    CommandLine cmdLine = new CommandLine(t8nSubCommand);
    cmdLine.registerConverter(Wei.class, arg -> Wei.of(Long.parseUnsignedLong(arg)));
    cmdLine.execute(
        //        "t8n",
        "--input.alloc=stdin",
        "--input.txs=stdin",
        "--input.env=stdin",
        "--output.result=stdout",
        "--output.alloc=stdout",
        "--output.body=stdout",
        "--output.basedir=/tmp",
        "--state.fork=Berlin",
        "--state.chainid=1",
        "--state.reward=0");
    assertThat(baos.toString(UTF_8))
        .isEqualTo(
            """
             {
              "alloc" : {
               "0x0000000000000000000000000000000000000000" : {
                "balance" : "0x81650"
               },
               "0x1000000000000000000000000000000000000000" : {
                "code" : "0x6010565b6000828201905092915050565b601a600260016003565b60005560206000f3",
                "balance" : "0xba1a9ce0ba1a9ce"
               },
               "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f" : {
                "balance" : "0x0",
                "nonce" : "0x1"
               },
               "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b" : {
                "balance" : "0xba1a9ce0b99937e",
                "nonce" : "0x1"
               }
              },
              "body" : "0xf84ef84c800a8307a1208080801ca01c2568f8f4e444ca10decc73cc903f507b888d4fc642382d050837c59c96181da00f2f3f099c44702e899f678492faa3f28eb6c9706044755fd5decd73ee0cfe8d",
              "result" : {
               "stateRoot" : "0x5999b20b3e7c843e1348e511a6ba3923c268c517824ce2d4de771120fdf60412",
               "txRoot" : "0x53dc971422f3ac44bcea4c90240f6de67d09f6f2c9421f821e153c80dcac7fab",
               "receiptsRoot" : "0x65c4d1b533902562730f0d110bcce51643ce4725ca03d85c33d5812c0ed308bf",
               "logsHash" : "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
               "logsBloom" : "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
               "receipts" : [
                {
                 "root" : "0x",
                 "status" : "0x1",
                 "cumulativeGasUsed" : "0xcf08",
                 "logsBloom" : "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                 "logs" : null,
                 "transactionHash" : "0xe189afb86302d5dde1cb17822138227e3bd907efe5c3f4b4f08e30c9527082e9",
                 "contractAddress" : "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f",
                 "gasUsed" : "0xcf08",
                 "blockHash" : "0x0000000000000000000000000000000000000000000000000000000000000000",
                 "transactionIndex" : "0x0"
                }
               ],
               "currentDifficulty" : "0x0",
               "gasUsed" : "0xcf08"
              }
             }
             """);
  }

  @Test
  void testWithStorage() {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final ByteArrayInputStream bais =
        new ByteArrayInputStream(
            """
                    {
                        "alloc": {
                            "0x1000000000000000000000000000000000000000": {
                                "nonce": "0x00",
                                "balance": "0x0ba1a9ce0ba1a9ce",
                                "code": "0x6010565b6000828201905092915050565b601a600260016003565b60005560206000f3",
                                "storage": {}
                            },
                            "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b": {
                                "nonce": "0x00",
                                "balance": "0x0ba1a9ce0ba1a9ce",
                                "code": "0x",
                                "storage": {}
                            }
                        },
                        "txs": [
                            {
                                "type": "0x0",
                                "chainId": "0x0",
                                "nonce": "0x0",
                                "gasPrice": "0xa",
                                "gas": "0x7a120",
                                "value": "0x0",
                                "input": "0x",
                                "to": "0x1000000000000000000000000000000000000000",
                                "protected": false,
                                "secretKey": "0x45a915e4d060149eb4365960e6a7a45f334393093061116b197e3240065ff2d8",
                                "v": "0x0",
                                "r": "0x0",
                                "s": "0x0"
                            }
                        ],
                        "env": {
                            "currentCoinbase": "0x0000000000000000000000000000000000000000",
                            "currentDifficulty": "0x0",
                            "currentGasLimit": "0x0",
                            "currentNumber": "0",
                            "currentTimestamp": "0"
                        }
                    }"""
                .getBytes(UTF_8));

    EvmToolCommand evmTool = new EvmToolCommand();
    final T8nSubCommand t8nSubCommand = new T8nSubCommand(evmTool, bais, new PrintStream(baos));
    CommandLine cmdLine = new CommandLine(t8nSubCommand);
    cmdLine.registerConverter(Wei.class, arg -> Wei.of(Long.parseUnsignedLong(arg)));
    cmdLine.execute(
        //         "t8n",
        "--input.alloc=stdin",
        "--input.env=stdin",
        "--input.txs=stdin",
        "--output.alloc=stdout",
        "--output.body=stdin",
        "--output.result=stdout",
        "--state.fork=Berlin",
        "--state.chainid=1",
        "--state.reward=2000000000000000000");
    assertThat(baos.toString(UTF_8))
        .isEqualTo(
            """
                     {
                      "alloc" : {
                       "0x0000000000000000000000000000000000000000" : {
                        "balance" : "0x696c2",
                        "storage" : { }
                       },
                       "0x1000000000000000000000000000000000000000" : {
                        "balance" : "0xba1a9ce0ba1a9ce",
                        "code" : "0x6010565b6000828201905092915050565b601a600260016003565b60005560206000f3",
                        "storage" : {
                         "0x0000000000000000000000000000000000000000000000000000000000000000" : "0x0000000000000000000000000000000000000000000000000000000000000003"
                        }
                       },
                       "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b" : {
                        "nonce" : "0x1",
                        "balance" : "0xba1a9ce0b9b130c",
                        "storage" : { }
                       }
                      },
                      "result" : {
                       "stateRoot" : "0xde762e485411037c02119ff1115b422945b37efe91f4135dd442c3346629d0ef",
                       "txRoot" : "0x8e987be72f36f97a98838e03d9e5d1b3d22795606240f16684532b1c994b4ee7",
                       "receiptsRoot" : "0x0812e2d0238699cad1dd4c1274804ed2dde9a35df8ee002d48bbf3ef87857a63",
                       "logsHash" : "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
                       "logsBloom" : "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                       "receipts" : [
                        {
                         "root" : "0x",
                         "status" : "0x1",
                         "cumulativeGasUsed" : "0xa8ad",
                         "logsBloom" : "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                         "logs" : null,
                         "transactionHash" : "0x6c4a22ee7d2d9ae9307b09aac69a34600978e34313088917a7e69fd12e8f959a",
                         "contractAddress" : "0x0000000000000000000000000000000000000000",
                         "gasUsed" : "0xa8ad",
                         "blockHash" : "0x0000000000000000000000000000000000000000000000000000000000000000",
                         "transactionIndex" : "0x0"
                        }
                       ],
                       "currentDifficulty" : "0x0",
                       "gasUsed" : "0xa8ad"
                      }
                     }
                     """);
  }
}
