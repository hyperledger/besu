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
package org.hyperledger.besu.ethereum.eth.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.ChainHead;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;

import org.junit.jupiter.api.Test;

public class ChainStateTest {

  private static final Difficulty INITIAL_TOTAL_DIFFICULTY = Difficulty.of(256);
  private final ChainState chainState = new ChainState();

  @Test
  public void statusReceivedUpdatesBestBlock() {
    final BlockHeader bestBlockHeader = new BlockHeaderTestFixture().number(12).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void updateHeightEstimate_toZero() {
    chainState.updateHeightEstimate(0L);
    assertThat(chainState.hasEstimatedHeight()).isFalse();
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
  }

  @Test
  public void updateHeightEstimate_toNonZeroValue() {
    chainState.updateHeightEstimate(1L);
    assertThat(chainState.hasEstimatedHeight()).isTrue();
    assertThat(chainState.getEstimatedHeight()).isEqualTo(1L);
  }

  @Test
  public void updateBestBlockAndHeightFromHashAndHeight() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    chainState.update(bestBlockHeader.getHash(), blockNumber);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void updateHeightFromHashAndHeight() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    chainState.update(gen.hash(), blockNumber);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void doesNotUpdateFromOldHashAndHeight() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    chainState.update(gen.hash(), blockNumber);
    chainState.update(gen.hash(), blockNumber - 1);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void updateBestBlockAndHeightFromHeader() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    chainState.update(bestBlockHeader);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void updateHeightFromHeader() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    final long newHeaderNumber = blockNumber + 1;
    chainState.update(new BlockHeaderTestFixture().number(newHeaderNumber).buildHeader());
    assertThat(chainState.getEstimatedHeight()).isEqualTo(newHeaderNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void doesNotUpdateFromOldHeader() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    chainState.update(bestBlockHeader);
    chainState.update(new BlockHeaderTestFixture().number(blockNumber - 5).buildHeader());
    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(blockNumber);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void updateBestBlockAndHeightFromBestBlockHeaderAndTd() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    chainState.updateForAnnouncedBlock(bestBlockHeader, INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockNumber - 1);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(blockNumber - 1);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getParentHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void updateBestBlockAndHeightFromBetterBlockHeaderAndTd() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    final long betterBlockNumber = blockNumber + 2;
    final Difficulty betterTd = INITIAL_TOTAL_DIFFICULTY.add(100L);
    final BlockHeader betterBlock =
        new BlockHeaderTestFixture().number(betterBlockNumber).buildHeader();
    chainState.updateForAnnouncedBlock(betterBlock, betterTd);

    assertThat(chainState.getEstimatedHeight()).isEqualTo(betterBlockNumber - 1);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(betterBlockNumber - 1);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(betterBlock.getParentHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(betterTd);
  }

  @Test
  public void updateHeightFromBlockHeaderAndTd() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    final long otherBlockNumber = blockNumber + 2;
    final Difficulty otherTd = INITIAL_TOTAL_DIFFICULTY.subtract(100L);
    final BlockHeader otherBlock =
        new BlockHeaderTestFixture().number(otherBlockNumber).buildHeader();
    chainState.updateForAnnouncedBlock(otherBlock, otherTd);

    assertThat(chainState.getEstimatedHeight()).isEqualTo(otherBlockNumber - 1);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void doNotUpdateFromOldBlockHeaderAndTd() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    assertThat(chainState.getEstimatedHeight()).isEqualTo(0L);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(0L);

    chainState.updateForAnnouncedBlock(bestBlockHeader, INITIAL_TOTAL_DIFFICULTY);

    final long otherBlockNumber = blockNumber - 2;
    final Difficulty otherTd = INITIAL_TOTAL_DIFFICULTY.subtract(100L);
    final BlockHeader otherBlock =
        new BlockHeaderTestFixture().number(otherBlockNumber).buildHeader();
    chainState.updateForAnnouncedBlock(otherBlock, otherTd);

    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockNumber - 1);
    assertThat(chainState.getBestBlock().getNumber()).isEqualTo(blockNumber - 1);
    assertThat(chainState.getBestBlock().getHash()).isEqualTo(bestBlockHeader.getParentHash());
    assertThat(chainState.getBestBlock().getTotalDifficulty()).isEqualTo(INITIAL_TOTAL_DIFFICULTY);
  }

  @Test
  public void shouldOnlyHaveHeightEstimateWhenHeightHasBeenSet() {
    chainState.statusReceived(Hash.EMPTY_LIST_HASH, Difficulty.ONE);
    assertThat(chainState.hasEstimatedHeight()).isFalse();

    chainState.update(new BlockHeaderTestFixture().number(12).buildHeader());

    assertThat(chainState.hasEstimatedHeight()).isTrue();
  }

  @Test
  public void observersInformedWhenHeightUpdatedViaHashAndNumber() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    final ChainState.EstimatedHeightListener listener =
        mock(ChainState.EstimatedHeightListener.class);
    chainState.addEstimatedHeightListener(listener);
    chainState.update(bestBlockHeader.getHash(), blockNumber);
    verify(listener).onEstimatedHeightChanged(blockNumber);
  }

  @Test
  public void observersInformedWhenHeightUpdatedViaHeader() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    final ChainState.EstimatedHeightListener listener =
        mock(ChainState.EstimatedHeightListener.class);
    chainState.addEstimatedHeightListener(listener);
    chainState.update(bestBlockHeader);
    verify(listener).onEstimatedHeightChanged(blockNumber);
  }

  @Test
  public void observersInformedWhenHeightUpdatedViaHeaderAndTD() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    final ChainState.EstimatedHeightListener listener =
        mock(ChainState.EstimatedHeightListener.class);
    chainState.addEstimatedHeightListener(listener);
    chainState.updateForAnnouncedBlock(bestBlockHeader, INITIAL_TOTAL_DIFFICULTY);
    verify(listener).onEstimatedHeightChanged(blockNumber - 1);
  }

  @Test
  public void observersNotInformedWhenHeightLowers() {
    final long blockNumber = 12;
    final BlockHeader bestBlockHeader =
        new BlockHeaderTestFixture().number(blockNumber).buildHeader();
    chainState.statusReceived(bestBlockHeader.getHash(), INITIAL_TOTAL_DIFFICULTY);
    final ChainState.EstimatedHeightListener listener =
        mock(ChainState.EstimatedHeightListener.class);
    chainState.addEstimatedHeightListener(listener);
    chainState.update(bestBlockHeader);
    verify(listener).onEstimatedHeightChanged(blockNumber);

    final long lowerBlockNumber = 12;
    final BlockHeader lowerBlockHeader =
        new BlockHeaderTestFixture().number(lowerBlockNumber).buildHeader();
    chainState.update(lowerBlockHeader);

    verifyNoMoreInteractions(listener);
  }

  @Test
  public void chainIsBetterThan_chainStateIsLighterAndShorter() {
    final ChainState chainState = new ChainState();
    updateChainState(chainState, Difficulty.of(50), 50);
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getHash()).thenReturn(Hash.ZERO);
    final ChainHead chainHead = new ChainHead(blockHeader, Difficulty.of(100), 100);

    assertThat(chainState.chainIsBetterThan(chainHead)).isFalse();
  }

  @Test
  public void chainIsBetterThan_chainStateIsHeavierAndShorter() {
    final ChainState chainState = new ChainState();
    updateChainState(chainState, Difficulty.of(100), 50);
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getHash()).thenReturn(Hash.ZERO);
    final ChainHead chainHead = new ChainHead(blockHeader, Difficulty.of(50), 100);

    assertThat(chainState.chainIsBetterThan(chainHead)).isTrue();
  }

  @Test
  public void chainIsBetterThan_chainStateIsLighterAndTaller() {
    final ChainState chainState = new ChainState();
    updateChainState(chainState, Difficulty.of(50), 100);
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getHash()).thenReturn(Hash.ZERO);
    final ChainHead chainHead = new ChainHead(blockHeader, Difficulty.of(100), 50);

    assertThat(chainState.chainIsBetterThan(chainHead)).isTrue();
  }

  @Test
  public void chainIsBetterThan_chainStateIsHeavierAndTaller() {
    final ChainState chainState = new ChainState();
    updateChainState(chainState, Difficulty.of(100), 100);
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getHash()).thenReturn(Hash.ZERO);
    final ChainHead chainHead = new ChainHead(blockHeader, Difficulty.of(50), 50);

    assertThat(chainState.chainIsBetterThan(chainHead)).isTrue();
  }

  /**
   * Updates the chain state, such that the peer will end up with an estimated height of {@code
   * blockHeight} and an estimated total difficulty of {@code totalDifficulty}
   *
   * @param chainState The chain state to update
   * @param totalDifficulty The total difficulty
   * @param blockHeight The target estimated block height
   */
  private void updateChainState(
      final ChainState chainState, final Difficulty totalDifficulty, final long blockHeight) {
    // Chain state is updated based on the parent of the announced block
    // So, increment block number by 1 and set block difficulty to zero
    // in order to update to the values we want
    final BlockHeader header =
        new BlockHeaderTestFixture()
            .number(blockHeight + 1L)
            .difficulty(Difficulty.ZERO)
            .buildHeader();
    chainState.updateForAnnouncedBlock(header, totalDifficulty);

    // Sanity check this logic still holds
    assertThat(chainState.getEstimatedHeight()).isEqualTo(blockHeight);
    assertThat(chainState.getEstimatedTotalDifficulty()).isEqualTo(totalDifficulty);
  }
}
