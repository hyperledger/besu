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
package org.hyperledger.besu.ethereum.chain;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

public class BadBlockManagerTest {

  final BlockchainSetupUtil chainUtil = BlockchainSetupUtil.forMainnet();
  final Block block = chainUtil.getBlock(1);
  final Block block2 = chainUtil.getBlock(2);
  final BadBlockManager badBlockManager = new BadBlockManager();

  @Test
  public void addBadBlock_addsBlock() {
    BadBlockManager badBlockManager = new BadBlockManager();
    final BadBlockCause cause = BadBlockCause.fromValidationFailure("failed");
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

  @Test
  public void subscribeToBadBlocks_listenerReceivesBadBlockEvent() {

    final AtomicReference<org.hyperledger.besu.plugin.data.BlockHeader> badBlockResult =
        new AtomicReference<>();
    final AtomicReference<org.hyperledger.besu.plugin.data.BadBlockCause> badBlockCauseResult =
        new AtomicReference<>();

    badBlockManager.subscribeToBadBlocks(
        (badBlock, cause) -> {
          badBlockResult.set(badBlock);
          badBlockCauseResult.set(cause);
        });

    final BadBlockCause cause = BadBlockCause.fromValidationFailure("fail");
    badBlockManager.addBadBlock(block, cause);

    // Check event was emitted
    assertThat(badBlockResult.get()).isEqualTo(block.getHeader());
    assertThat(badBlockCauseResult.get()).isEqualTo(cause);
  }

  @Test
  public void subscribeToBadBlocks_listenerReceivesBadHeaderEvent() {

    final AtomicReference<org.hyperledger.besu.plugin.data.BlockHeader> badBlockResult =
        new AtomicReference<>();
    final AtomicReference<org.hyperledger.besu.plugin.data.BadBlockCause> badBlockCauseResult =
        new AtomicReference<>();

    badBlockManager.subscribeToBadBlocks(
        (badBlock, cause) -> {
          badBlockResult.set(badBlock);
          badBlockCauseResult.set(cause);
        });

    final BadBlockCause cause = BadBlockCause.fromValidationFailure("fail");
    badBlockManager.addBadHeader(block.getHeader(), cause);

    // Check event was emitted
    assertThat(badBlockResult.get()).isEqualTo(block.getHeader());
    assertThat(badBlockCauseResult.get()).isEqualTo(cause);
  }

  @Test
  public void unsubscribeFromBadBlocks_listenerReceivesNoEvents() {

    final AtomicInteger eventCount = new AtomicInteger(0);
    final long subscribeId =
        badBlockManager.subscribeToBadBlocks((block, cause) -> eventCount.incrementAndGet());
    badBlockManager.unsubscribeFromBadBlocks(subscribeId);

    final BadBlockCause cause = BadBlockCause.fromValidationFailure("fail");
    badBlockManager.addBadBlock(block, cause);
    badBlockManager.addBadHeader(block2.getHeader(), cause);

    // Check no events fired
    assertThat(eventCount.get()).isEqualTo(0);
  }
}
