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
package org.hyperledger.besu.ethereum.chain;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class BadBlockManagerTest {

  final BlockchainSetupUtil chainUtil = BlockchainSetupUtil.forMainnet();
  final Block block = chainUtil.getBlock(1);
  final BadBlockManager badBlockManager = new BadBlockManager();

  public static Stream<Arguments> getInternalExceptions() {
    return Stream.of(
        Arguments.of("StorageException", new StorageException("oops")),
        Arguments.of("MerkleTrieException", new MerkleTrieException("fail")));
  }

  @Test
  public void addBadBlock_addsBlock() {
    BadBlockManager badBlockManager = new BadBlockManager();
    final BadBlockCause cause = BadBlockCause.fromValidationFailure("failed");
    badBlockManager.addBadBlock(block, cause);

    assertThat(badBlockManager.getBadBlocks()).containsExactly(block);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("getInternalExceptions")
  public void addBadBlock_ignoresInternalError(final String caseName, final Exception err) {
    BadBlockManager badBlockManager = new BadBlockManager();
    final BadBlockCause cause = BadBlockCause.fromProcessingError(err);
    badBlockManager.addBadBlock(block, cause);

    assertThat(badBlockManager.getBadBlocks()).isEmpty();
  }

  @Test
  public void addBadBlock_doesNotIgnoreRuntimeException() {
    final Exception err = new RuntimeException("oops");
    final BadBlockCause cause = BadBlockCause.fromProcessingError(err);
    badBlockManager.addBadBlock(block, cause);

    assertThat(badBlockManager.getBadBlocks()).containsExactly(block);
  }

  @Test
  public void reset_clearsBadBlocks() {
    BadBlockManager badBlockManager = new BadBlockManager();
    final BadBlockCause cause = BadBlockCause.fromValidationFailure("");
    badBlockManager.addBadBlock(block, cause);
    assertThat(badBlockManager.getBadBlocks()).containsExactly(block);

    badBlockManager.reset();

    assertThat(badBlockManager.getBadBlocks()).isEmpty();
  }

  @Test
  public void isBadBlock_falseWhenEmpty() {
    assertThat(badBlockManager.isBadBlock(block.getHash())).isFalse();
  }

  @Test
  public void isBadBlock_trueForBadHeader() {
    badBlockManager.addBadHeader(block.getHeader(), BadBlockCause.fromValidationFailure("failed"));
    assertThat(badBlockManager.isBadBlock(block.getHash())).isTrue();
  }

  @Test
  public void isBadBlock_trueForBadBlock() {
    badBlockManager.addBadBlock(block, BadBlockCause.fromValidationFailure("failed"));

    assertThat(badBlockManager.isBadBlock(block.getHash())).isTrue();
  }
}
