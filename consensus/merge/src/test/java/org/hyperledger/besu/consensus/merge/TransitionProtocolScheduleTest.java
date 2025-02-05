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
package org.hyperledger.besu.consensus.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TransitionProtocolScheduleTest {

  @Mock ProtocolContext protocolContext;
  @Mock MutableBlockchain blockchain;
  @Mock MergeContext mergeContext;
  @Mock ProtocolSchedule preMergeProtocolSchedule;
  @Mock ProtocolSchedule postMergeProtocolSchedule;
  @Mock BlockHeader blockHeader;
  private static final Difficulty TTD = Difficulty.of(100L);
  private static final long BLOCK_NUMBER = 29L;
  private TransitionProtocolSchedule transitionProtocolSchedule;

  @BeforeEach
  public void setUp() {

    transitionProtocolSchedule =
        new TransitionProtocolSchedule(
            preMergeProtocolSchedule, postMergeProtocolSchedule, mergeContext);
    transitionProtocolSchedule.setProtocolContext(protocolContext);
  }

  @Test
  public void returnPostMergeIfFinalizedExists() {
    when(mergeContext.getFinalized()).thenReturn(Optional.of(mock(BlockHeader.class)));

    transitionProtocolSchedule.getByBlockHeaderWithTransitionReorgHandling(blockHeader);

    verifyPostMergeProtocolScheduleReturnedUsingBlockHeader();
  }

  @Test
  public void returnPreMergeIfBeforeMerge() {
    when(mergeContext.getFinalized()).thenReturn(Optional.empty());
    when(mergeContext.isPostMerge()).thenReturn(false);

    transitionProtocolSchedule.getByBlockHeaderWithTransitionReorgHandling(blockHeader);

    verifyPreMergeProtocolScheduleReturnedUsingBlockHeader();
  }

  @Test
  public void returnPreMergeIfTerminalPoWBlock() {

    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(protocolContext.getConsensusContext(MergeContext.class)).thenReturn(mergeContext);
    when(mergeContext.getTerminalTotalDifficulty()).thenReturn(TTD);
    when(mergeContext.getFinalized()).thenReturn(Optional.empty());
    when(mergeContext.isPostMerge()).thenReturn(true);

    final Hash parentHash = Hash.fromHexStringLenient("0xabc123");

    when(blockHeader.getParentHash()).thenReturn(parentHash);
    when(blockHeader.getDifficulty()).thenReturn(Difficulty.of(10L));
    when(blockchain.getTotalDifficultyByHash(parentHash))
        .thenReturn(Optional.of(Difficulty.of(95L)));

    transitionProtocolSchedule.getByBlockHeaderWithTransitionReorgHandling(blockHeader);

    verifyPreMergeProtocolScheduleReturnedUsingBlockHeader();
  }

  @Test
  public void returnPreMergeIfAfterMergeButReorgPreTTD() {
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(mergeContext.getTerminalTotalDifficulty()).thenReturn(TTD);

    when(mergeContext.getFinalized()).thenReturn(Optional.empty());
    when(mergeContext.isPostMerge()).thenReturn(true);

    final Hash parentHash = Hash.fromHexStringLenient("0xabc123");

    when(blockHeader.getParentHash()).thenReturn(parentHash);
    when(blockHeader.getDifficulty()).thenReturn(Difficulty.of(2L));
    when(blockchain.getTotalDifficultyByHash(parentHash))
        .thenReturn(Optional.of(Difficulty.of(95L)));

    transitionProtocolSchedule.getByBlockHeaderWithTransitionReorgHandling(blockHeader);

    verifyPreMergeProtocolScheduleReturnedUsingBlockHeader();
  }

  @Test
  public void returnPostMergeIfAfterMergeButReorgPostTTD() {

    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(protocolContext.getConsensusContext(MergeContext.class)).thenReturn(mergeContext);
    when(mergeContext.getTerminalTotalDifficulty()).thenReturn(TTD);
    when(mergeContext.getFinalized()).thenReturn(Optional.empty());
    when(mergeContext.isPostMerge()).thenReturn(true);

    final Hash parentHash = Hash.fromHexStringLenient("0xabc123");

    when(blockHeader.getNumber()).thenReturn(BLOCK_NUMBER);
    when(blockHeader.getParentHash()).thenReturn(parentHash);
    when(blockHeader.getDifficulty()).thenReturn(Difficulty.ZERO);
    when(blockchain.getTotalDifficultyByHash(parentHash))
        .thenReturn(Optional.of(Difficulty.of(105L)));

    transitionProtocolSchedule.getByBlockHeaderWithTransitionReorgHandling(blockHeader);

    verifyPostMergeProtocolScheduleReturnedUsingBlockHeader();
  }

  @Test
  public void getByBlockHeader_delegatesToPreMergeSchedule() {
    when(mergeContext.isPostMerge()).thenReturn(false);

    transitionProtocolSchedule.getByBlockHeader(blockHeader);

    verifyPreMergeProtocolScheduleReturnedUsingBlockHeader();
  }

  @Test
  public void getByBlockHeader_delegatesToPostMergeSchedule() {
    when(mergeContext.isPostMerge()).thenReturn(true);

    transitionProtocolSchedule.getByBlockHeader(blockHeader);

    verifyPostMergeProtocolScheduleReturnedUsingBlockHeader();
  }

  @Test
  public void anyMatch_delegatesToPreMergeSchedule() {
    when(mergeContext.isPostMerge()).thenReturn(false);

    when(preMergeProtocolSchedule.anyMatch(any())).thenReturn(true);

    assertThat(transitionProtocolSchedule.anyMatch(__ -> true)).isTrue();
    verifyNoInteractions(postMergeProtocolSchedule);
  }

  @Test
  public void anyMatch_delegatesToPostMergeSchedule() {
    when(mergeContext.isPostMerge()).thenReturn(true);

    when(postMergeProtocolSchedule.anyMatch(any())).thenReturn(true);

    assertThat(transitionProtocolSchedule.anyMatch(__ -> true)).isTrue();
    verifyNoInteractions(preMergeProtocolSchedule);
  }

  @Test
  public void isOnMilestoneBoundary_delegatesToPreMergeSchedule() {
    when(mergeContext.isPostMerge()).thenReturn(false);

    when(preMergeProtocolSchedule.isOnMilestoneBoundary(any(BlockHeader.class))).thenReturn(true);

    assertThat(transitionProtocolSchedule.isOnMilestoneBoundary(blockHeader)).isTrue();
    verifyNoInteractions(postMergeProtocolSchedule);
  }

  @Test
  public void isOnMilestoneBoundary_delegatesToPostMergeSchedule() {
    when(mergeContext.isPostMerge()).thenReturn(true);

    when(postMergeProtocolSchedule.isOnMilestoneBoundary(any(BlockHeader.class))).thenReturn(true);

    assertThat(transitionProtocolSchedule.isOnMilestoneBoundary(blockHeader)).isTrue();
    verifyNoInteractions(preMergeProtocolSchedule);
  }

  private void verifyPreMergeProtocolScheduleReturnedUsingBlockHeader() {
    verify(preMergeProtocolSchedule).getByBlockHeader(any());
    verifyNoInteractions(postMergeProtocolSchedule);
  }

  private void verifyPostMergeProtocolScheduleReturnedUsingBlockHeader() {
    verify(postMergeProtocolSchedule).getByBlockHeader(any());
    verifyNoInteractions(preMergeProtocolSchedule);
  }
}
