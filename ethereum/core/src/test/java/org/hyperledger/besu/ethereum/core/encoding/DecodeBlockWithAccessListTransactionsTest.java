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

package org.hyperledger.besu.ethereum.core.encoding;

import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DecodeBlockWithAccessListTransactionsTest {

  private RLPInput rlpInput;
  private Block expectedBlock;

  public DecodeBlockWithAccessListTransactionsTest(
      final RLPInput rlpInput, final Block expectedBlock) {
    this.rlpInput = rlpInput;
    this.expectedBlock = expectedBlock;
  }

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    final ObjectMapper objectMapper = new ObjectMapper();
    return IntStream.rangeClosed(0, 9)
        .mapToObj(index -> String.format("acl_block_%d.json", index))
        .map(
            filename -> {
              try {
                return objectMapper.readTree(
                    DecodeBlockWithAccessListTransactionsTest.class.getResource(filename));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .map(
            rootNode -> {
              try {
                final JsonNode jsonNode = rootNode.get("json");
                return new Object[] {
                  new BytesValueRLPInput(
                      Bytes.fromHexString("0x" + jsonNode.get("rlp").textValue()), false),
                  new Block(
                      objectMapper.treeToValue(jsonNode.get("Header"), BlockHeader.class),
                      new BlockBody(
                          Arrays.asList(
                              objectMapper.treeToValue(jsonNode.get("Txs"), Transaction[].class)),
                          Arrays.asList(
                              objectMapper.treeToValue(
                                  jsonNode.get("Uncles"), BlockHeader[].class))))
                };
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(toUnmodifiableList());
  }

  @Test
  public void decodesBlockWithAccessListTransactionsCorrectly() {
    assertThat(Block.readFrom(rlpInput, new MainnetBlockHeaderFunctions()))
        .isEqualTo(expectedBlock);
  }
}
