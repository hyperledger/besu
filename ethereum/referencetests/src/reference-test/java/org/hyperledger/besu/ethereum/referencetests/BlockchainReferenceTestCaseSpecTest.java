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
package org.hyperledger.besu.ethereum.referencetests;

import org.hyperledger.besu.testutil.JsonTestParameters;

import java.io.IOException;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BlockchainReferenceTestCaseSpecTest {

  String jsonValid =
      """
            {
            "Call1MB1024Calldepth_d0g0v0_London" : {
                  "_info" : {
                      "comment" : "",
                      "filling-rpc-server" : "evm version 1.13.5-unstable-cd295356-20231019",
                      "filling-tool-version" : "retesteth-0.3.1-cancun+commit.1e18e0b3.Linux.g++",
                      "generatedTestHash" : "faf65f5956de8021ec3bddb63cb503e48fb2c89c8596bbc2ad793e6f3b39e1dc",
                      "lllcversion" : "Version: 0.5.14-develop.2023.7.11+commit.c58ab2c6.mod.Linux.g++",
                      "solidity" : "Version: 0.8.21+commit.d9974bed.Linux.g++",
                      "source" : "src/GeneralStateTestsFiller/stQuadraticComplexityTest/Call1MB1024CalldepthFiller.json",
                      "sourceHash" : "d88ac245b033cfc6159f6201b10b65796ba183dfe25c5f5e6d19d6f50a31daec"
                  },
                  "blocks" : [
                      {
                          "blockHeader" : {
                              "baseFeePerGas" : "0x0a",
                              "bloom" : "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                              "coinbase" : "0xb94f5374fce5edbc8e2a8697c15331677e6ebf0b",
                              "difficulty" : "0x020000",
                              "extraData" : "0x00",
                              "gasLimit" : "0xcd79195900",
                              "gasUsed" : "0x0249f0",
                              "hash" : "0xfa70e84a600031d217fae4106b80155ff4af8970a97c8f9bf35efaf6929390a5",
                              "mixHash" : "0x0000000000000000000000000000000000000000000000000000000000000000",
                              "nonce" : "0x0000000000000000",
                              "number" : "0x01",
                              "parentHash" : "0xefe355079be4a8cb49404abe2162ac9513c33d657d8f3cefe1ebadb91b1cd0cc",
                              "receiptTrie" : "0xa0e10907f175886de9bd8cd4ac2c21d1db4109a3a9fecf60f54015ee102803fd",
                              "stateRoot" : "0x33a1c7342e4b786c8fd736fc7d5c1b2fb3563abab428b356600e6f526de5518a",
                              "timestamp" : "0x03e8",
                              "transactionsTrie" : "0x09612e25490525613f1024d5f9a39a220234a3865309a68dee5f2f13eaac5fc1",
                              "uncleHash" : "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347"
                          },
                          "rlp" : "0xf90263f901fba0efe355079be4a8cb49404abe2162ac9513c33d657d8f3cefe1ebadb91b1cd0cca01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d4934794b94f5374fce5edbc8e2a8697c15331677e6ebf0ba033a1c7342e4b786c8fd736fc7d5c1b2fb3563abab428b356600e6f526de5518aa009612e25490525613f1024d5f9a39a220234a3865309a68dee5f2f13eaac5fc1a0a0e10907f175886de9bd8cd4ac2c21d1db4109a3a9fecf60f54015ee102803fdb9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000830200000185cd79195900830249f08203e800a000000000000000000000000000000000000000000000000000000000000000008800000000000000000af862f860800a830249f094bbbf5374fce5edbc8e2a8697c15331677e6ebf0b0a801ba06843bbbe573744c46cf47f1c126c11a53cffa1bcc3eeb0e0d328e6a73ecc5447a02062c2fbb34bfea606f3cf268b5a94a81d68e748d4d896c514061c03e8acb774c0",
                          "transactions" : [
                              {
                                  "data" : "0x",
                                  "gasLimit" : "0x0249f0",
                                  "gasPrice" : "0x0a",
                                  "nonce" : "0x00",
                                  "r" : "0x6843bbbe573744c46cf47f1c126c11a53cffa1bcc3eeb0e0d328e6a73ecc5447",
                                  "s" : "0x2062c2fbb34bfea606f3cf268b5a94a81d68e748d4d896c514061c03e8acb774",
                                  "sender" : "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b",
                                  "to" : "0xbbbf5374fce5edbc8e2a8697c15331677e6ebf0b",
                                  "v" : "0x1b",
                                  "value" : "0x0a"
                              }
                          ],
                          "uncleHeaders" : [
                          ]
                      }
                  ],
                  "genesisBlockHeader" : {
                      "baseFeePerGas" : "0x0b",
                      "bloom" : "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                      "coinbase" : "0xb94f5374fce5edbc8e2a8697c15331677e6ebf0b",
                      "difficulty" : "0x020000",
                      "extraData" : "0x00",
                      "gasLimit" : "0xcd79195900",
                      "gasUsed" : "0x00",
                      "hash" : "0xefe355079be4a8cb49404abe2162ac9513c33d657d8f3cefe1ebadb91b1cd0cc",
                      "mixHash" : "0x0000000000000000000000000000000000000000000000000000000000000000",
                      "nonce" : "0x0000000000000000",
                      "number" : "0x00",
                      "parentHash" : "0x0000000000000000000000000000000000000000000000000000000000000000",
                      "receiptTrie" : "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
                      "stateRoot" : "0x16d6ea0e1b8d4eb9d1bfd2a1031ea72a255d9af13a88b4cfb8253d2f756d57e1",
                      "timestamp" : "0x00",
                      "transactionsTrie" : "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
                      "uncleHash" : "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347"
                  },
                  "genesisRLP" : "0xf901fbf901f6a00000000000000000000000000000000000000000000000000000000000000000a01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d4934794b94f5374fce5edbc8e2a8697c15331677e6ebf0ba016d6ea0e1b8d4eb9d1bfd2a1031ea72a255d9af13a88b4cfb8253d2f756d57e1a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000830200008085cd79195900808000a000000000000000000000000000000000000000000000000000000000000000008800000000000000000bc0c0",
                  "lastblockhash" : "0xfa70e84a600031d217fae4106b80155ff4af8970a97c8f9bf35efaf6929390a5",
                  "network" : "London",
                  "postState" : {
                      "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b" : {
                          "balance" : "0xffffffffffffffffffffffffffe91c9f",
                          "code" : "0x",
                          "nonce" : "0x01",
                          "storage" : {
                          }
                      },
                      "0xaaa50000fce5edbc8e2a8697c15331677e6ebf0b" : {
                          "balance" : "0x0fffffffffffff",
                          "code" : "0x",
                          "nonce" : "0x00",
                          "storage" : {
                          }
                      },
                      "0xb94f5374fce5edbc8e2a8697c15331677e6ebf0b" : {
                          "balance" : "0x1bc16d674ec80000",
                          "code" : "0x",
                          "nonce" : "0x00",
                          "storage" : {
                          }
                      },
                      "0xbbbf5374fce5edbc8e2a8697c15331677e6ebf0b" : {
                          "balance" : "0x0fffffffffffff",
                          "code" : "0x60016000540160005561040060005410601b5760016002556047565b60006000620f42406000600073bbbf5374fce5edbc8e2a8697c15331677e6ebf0b620f55c85a03f16001555b00",
                          "nonce" : "0x00",
                          "storage" : {
                          }
                      }
                  },
                  "pre" : {
                      "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b" : {
                          "balance" : "0xffffffffffffffffffffffffffffffff",
                          "code" : "0x",
                          "nonce" : "0x00",
                          "storage" : {
                          }
                      },
                      "0xaaa50000fce5edbc8e2a8697c15331677e6ebf0b" : {
                          "balance" : "0x0fffffffffffff",
                          "code" : "0x",
                          "nonce" : "0x00",
                          "storage" : {
                          }
                      },
                      "0xbbbf5374fce5edbc8e2a8697c15331677e6ebf0b" : {
                          "balance" : "0x0fffffffffffff",
                          "code" : "0x60016000540160005561040060005410601b5760016002556047565b60006000620f42406000600073bbbf5374fce5edbc8e2a8697c15331677e6ebf0b620f55c85a03f16001555b00",
                          "nonce" : "0x00",
                          "storage" : {
                          }
                      }
                  },
                  "sealEngine" : "NoProof"
              }
              }
          """;

  String jsonInvalid =
      """
        { "ValueOverflow_d0g0v0_EIP150" : {
                    "_info" : {
                        "comment" : "",
                        "filling-rpc-server" : "evm version 1.13.5-unstable-cd295356-20231019",
                        "filling-tool-version" : "retesteth-0.3.1-cancun+commit.1e18e0b3.Linux.g++",
                        "generatedTestHash" : "3e97036f37b30c4b22f7816fb9f8321623ec4dd8646b0ab9c76fcf87371a36e3",
                        "lllcversion" : "Version: 0.5.14-develop.2023.7.11+commit.c58ab2c6.mod.Linux.g++",
                        "solidity" : "Version: 0.8.21+commit.d9974bed.Linux.g++",
                        "source" : "src/GeneralStateTestsFiller/stTransactionTest/ValueOverflowFiller.yml",
                        "sourceHash" : "fa6acc202e029dc360fd6d105ac578d91f184f692359decbfe4f9f50af7fbb67"
                    },
                    "blocks" : [
                        {
                            "expectException" : "TR_RLP_WRONGVALUE",
                            "rlp" : "0xf9027ef901f6a0a96bbebc0b60fd343eead12143896f9331f436013c0f28cfd13698f0348f4b03a01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347942adc25665018aa1fe0e6bc666dac8fc2697ff9baa0fd1aa19de23712bad2af7f5e2a01afd6f92273527d501b20bb35e1e42b52f1d0a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421b901000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000083020000018405500000808203e800a00000000000000000000000000000000000000000000000000000000000000000880000000000000000f882f880806482520894d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0a1010000000000000000000000000000000000000000000000000000000000000001801ba0c16787a8e25e941d67691954642876c08f00996163ae7dfadbbfd6cd436f549da06180e5626cae31590f40641fe8f63734316c4bfeb4cdfab6714198c1044d2e28c0",
                            "transactionSequence" : [
                                {
                                    "exception" : "TR_RLP_WRONGVALUE",
                                    "rawBytes" : "0xf880806482520894d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0a1010000000000000000000000000000000000000000000000000000000000000001801ba0c16787a8e25e941d67691954642876c08f00996163ae7dfadbbfd6cd436f549da06180e5626cae31590f40641fe8f63734316c4bfeb4cdfab6714198c1044d2e28",
                                    "valid" : "false"
                                }
                            ]
                        }
                    ],
                    "genesisBlockHeader" : {
                        "bloom" : "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                        "coinbase" : "0x2adc25665018aa1fe0e6bc666dac8fc2697ff9ba",
                        "difficulty" : "0x020000",
                        "extraData" : "0x00",
                        "gasLimit" : "0x05500000",
                        "gasUsed" : "0x00",
                        "hash" : "0xa96bbebc0b60fd343eead12143896f9331f436013c0f28cfd13698f0348f4b03",
                        "mixHash" : "0x0000000000000000000000000000000000000000000000000000000000000000",
                        "nonce" : "0x0000000000000000",
                        "number" : "0x00",
                        "parentHash" : "0x0000000000000000000000000000000000000000000000000000000000000000",
                        "receiptTrie" : "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
                        "stateRoot" : "0x1751725d1aad5298768fbcf64069b2c1b85aeaffcc561146067d6beedd08052a",
                        "timestamp" : "0x00",
                        "transactionsTrie" : "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
                        "uncleHash" : "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347"
                    },
                    "genesisRLP" : "0xf901f9f901f4a00000000000000000000000000000000000000000000000000000000000000000a01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347942adc25665018aa1fe0e6bc666dac8fc2697ff9baa01751725d1aad5298768fbcf64069b2c1b85aeaffcc561146067d6beedd08052aa056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421b901000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000083020000808405500000808000a00000000000000000000000000000000000000000000000000000000000000000880000000000000000c0c0",
                    "lastblockhash" : "0xa96bbebc0b60fd343eead12143896f9331f436013c0f28cfd13698f0348f4b03",
                    "network" : "EIP150",
                    "postState" : {
                        "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b" : {
                            "balance" : "0x3b9aca00",
                            "code" : "0x",
                            "nonce" : "0x00",
                            "storage" : {
                            }
                        },
                        "0xd0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0" : {
                            "balance" : "0x00",
                            "code" : "0x",
                            "nonce" : "0x00",
                            "storage" : {
                            }
                        }
                    },
                    "pre" : {
                        "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b" : {
                            "balance" : "0x3b9aca00",
                            "code" : "0x",
                            "nonce" : "0x00",
                            "storage" : {
                            }
                        },
                        "0xd0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0" : {
                            "balance" : "0x00",
                            "code" : "0x",
                            "nonce" : "0x00",
                            "storage" : {
                            }
                        }
                    },
                    "sealEngine" : "NoProof"
                }
              }""";

  @Test
  void getValidTxs() {
    ObjectMapper objectMapper = new ObjectMapper();
    final JavaType javaType =
        objectMapper
            .getTypeFactory()
            .constructParametricType(
                JsonTestParameters.JsonTestCaseReader.class, BlockchainReferenceTestCaseSpec.class);
    JsonTestParameters.JsonTestCaseReader<BlockchainReferenceTestCaseSpec>
        blockchainReferenceTestCaseSpec;
    try {
      blockchainReferenceTestCaseSpec = objectMapper.readValue(jsonValid, javaType);
    } catch (final IOException e) {
      throw new RuntimeException("Error parsing test case json.", e);
    }

    Assertions.assertEquals(1, blockchainReferenceTestCaseSpec.testCaseSpecs.size());
    Assertions.assertEquals(
        1,
        blockchainReferenceTestCaseSpec
            .testCaseSpecs
            .get("Call1MB1024Calldepth_d0g0v0_London")
            .getCandidateBlocks()
            .length);
    Assertions.assertEquals(
        true,
        blockchainReferenceTestCaseSpec
            .testCaseSpecs
            .get("Call1MB1024Calldepth_d0g0v0_London")
            .getCandidateBlocks()[0]
            .areAllTransactionsValid());
  }

  @Test
  void getInValidTxs() {
    ObjectMapper objectMapper = new ObjectMapper();
    final JavaType javaType =
        objectMapper
            .getTypeFactory()
            .constructParametricType(
                JsonTestParameters.JsonTestCaseReader.class, BlockchainReferenceTestCaseSpec.class);
    JsonTestParameters.JsonTestCaseReader<BlockchainReferenceTestCaseSpec>
        blockchainReferenceTestCaseSpec;
    try {
      blockchainReferenceTestCaseSpec = objectMapper.readValue(jsonInvalid, javaType);
    } catch (final IOException e) {
      throw new RuntimeException("Error parsing test case json.", e);
    }

    Assertions.assertEquals(1, blockchainReferenceTestCaseSpec.testCaseSpecs.size());
    Assertions.assertEquals(
        1,
        blockchainReferenceTestCaseSpec
            .testCaseSpecs
            .get("ValueOverflow_d0g0v0_EIP150")
            .getCandidateBlocks()
            .length);
    Assertions.assertEquals(
        false,
        blockchainReferenceTestCaseSpec
            .testCaseSpecs
            .get("ValueOverflow_d0g0v0_EIP150")
            .getCandidateBlocks()[0]
            .areAllTransactionsValid());
  }
}
