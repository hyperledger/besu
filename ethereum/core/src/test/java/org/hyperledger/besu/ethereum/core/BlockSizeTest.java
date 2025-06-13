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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.HashMap;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class BlockSizeTest {

  static final Map<Block, Bytes> blocksAndRlp = new HashMap<>();

  @BeforeAll
  public static void before() {
    BlockchainSetupUtil.forMainnet()
        .getBlocks()
        .forEach(
            b -> {
              final BytesValueRLPOutput out = new BytesValueRLPOutput();
              b.writeTo(out);
              blocksAndRlp.put(b, out.encoded());
            });
  }

  @Test
  public void blocksReadFromRlpShouldReturnCorrectSize() {
    blocksAndRlp.forEach(
        (b, r) -> {
          final BytesValueRLPInput rlp = new BytesValueRLPInput(r, false);
          final Block block = Block.readFrom(rlp, new MainnetBlockHeaderFunctions());
          assertThat(block.getSize()).isEqualTo(r.size());
        });
  }

  @Test
  public void blocksInstantiatedWithoutLengthShouldReturnCorrectSize() {
    blocksAndRlp.forEach(
        (b, r) -> {
          final Block block = new Block(b.getHeader(), b.getBody());
          assertThat(block.getSize()).isEqualTo(r.size());
        });
  }
}
