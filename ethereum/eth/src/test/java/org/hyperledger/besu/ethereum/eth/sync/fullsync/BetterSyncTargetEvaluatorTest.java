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
package org.hyperledger.besu.ethereum.eth.sync.fullsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;

import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BetterSyncTargetEvaluatorTest {

  private static final int CURRENT_TARGET_HEIGHT = 10;
  private static final int CURRENT_TARGET_TD = 50;
  private static final int HEIGHT_THRESHOLD = 100;
  private static final int TD_THRESHOLD = 5;
  private final EthPeers ethPeers = mock(EthPeers.class);
  private final EthPeer currentTarget = peer(CURRENT_TARGET_HEIGHT, CURRENT_TARGET_TD);
  private final BetterSyncTargetEvaluator evaluator =
      new BetterSyncTargetEvaluator(
          SynchronizerConfiguration.builder()
              .downloaderChangeTargetThresholdByHeight(HEIGHT_THRESHOLD)
              .downloaderChangeTargetThresholdByTd(UInt256.valueOf(TD_THRESHOLD))
              .build(),
          ethPeers);

  @BeforeEach
  public void setupMocks() {
    when(ethPeers.getBestPeerComparator()).thenReturn(EthPeers.HEAVIEST_CHAIN);
  }

  @Test
  public void shouldNotSwitchTargetsIfNoBestPeerIsAvailable() {
    when(ethPeers.bestPeer()).thenReturn(Optional.empty());

    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isFalse();
  }

  @Test
  public void shouldNotSwitchTargetWhenBestPeerHasLowerHeightAndDifficulty() {
    bestPeerWithDelta(-1, -1);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isFalse();
  }

  @Test
  public void shouldNotSwitchTargetWhenBestPeerHasSameHeightAndLowerDifficulty() {
    bestPeerWithDelta(0, -1);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isFalse();
  }

  @Test
  public void shouldNotSwitchTargetWhenBestPeerHasLowerHeightAndSameDifficulty() {
    bestPeerWithDelta(-1, 0);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isFalse();
  }

  @Test
  public void shouldNotSwitchTargetWhenBestPeerHasGreaterHeightAndLowerDifficulty() {
    bestPeerWithDelta(HEIGHT_THRESHOLD + 1, -1);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isFalse();
  }

  @Test
  public void shouldNotSwitchTargetWhenBestPeerHasEqualHeightAndDifficulty() {
    bestPeerWithDelta(0, 0);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isFalse();
  }

  @Test
  public void shouldNotSwitchWhenHeightAndTdHigherWithinThreshold() {
    bestPeerWithDelta(HEIGHT_THRESHOLD - 1, TD_THRESHOLD - 1);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isFalse();
  }

  @Test
  public void shouldNotSwitchWhenHeightAndTdHigherEqualToThreshold() {
    bestPeerWithDelta(HEIGHT_THRESHOLD, TD_THRESHOLD);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isFalse();
  }

  @Test
  public void shouldSwitchWhenHeightExceedsThresholdAndDifficultyEqual() {
    bestPeerWithDelta(HEIGHT_THRESHOLD + 1, 0);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isTrue();
  }

  @Test
  public void shouldSwitchWhenHeightExceedsThresholdAndDifficultyWithinThreshold() {
    bestPeerWithDelta(HEIGHT_THRESHOLD + 1, TD_THRESHOLD - 1);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isTrue();
  }

  @Test
  public void shouldSwitchWhenHeightAndDifficultyExceedThreshold() {
    bestPeerWithDelta(HEIGHT_THRESHOLD + 1, TD_THRESHOLD + 1);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isTrue();
  }

  @Test
  public void shouldNotSwitchWhenHeightExceedsThresholdButDifficultyIsLower() {
    bestPeerWithDelta(HEIGHT_THRESHOLD + 1, -1);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isFalse();
  }

  @Test
  public void shouldSwitchWhenDifficultyExceedsThresholdAndHeightIsEqual() {
    bestPeerWithDelta(0, TD_THRESHOLD + 1);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isTrue();
  }

  @Test
  public void shouldSwitchWhenDifficultyExceedsThresholdAndHeightIsLower() {
    bestPeerWithDelta(-1, TD_THRESHOLD + 1);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isTrue();
  }

  @Test
  public void shouldSwitchWhenDifficultyExceedsThresholdAndHeightIsWithinThreshold() {
    bestPeerWithDelta(HEIGHT_THRESHOLD - 1, TD_THRESHOLD + 1);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isTrue();
  }

  @Test
  public void shouldSwitchWhenHeightAndDifficultyExceedsThreshold() {
    bestPeerWithDelta(HEIGHT_THRESHOLD + 1, TD_THRESHOLD + 1);
    assertThat(evaluator.shouldSwitchSyncTarget(currentTarget)).isTrue();
  }

  private void bestPeerWithDelta(final long height, final long totalDifficulty) {
    final EthPeer bestPeer =
        peer(CURRENT_TARGET_HEIGHT + height, CURRENT_TARGET_TD + totalDifficulty);
    when(ethPeers.bestPeer()).thenReturn(Optional.of(bestPeer));
  }

  private EthPeer peer(final long chainHeight, final long totalDifficulty) {
    final EthPeer peer = mock(EthPeer.class);
    final ChainState chainState = new ChainState();
    chainState.updateHeightEstimate(chainHeight);
    chainState.statusReceived(Hash.EMPTY, Difficulty.of(totalDifficulty));
    when(peer.chainState()).thenReturn(chainState);
    return peer;
  }
}
