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
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TimestampSchedule;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TransitionProtocolScheduleTest {

  @Mock ProtocolContext protocolContext;
  @Mock MutableBlockchain blockchain;
  @Mock MergeContext mergeContext;
  @Mock ProtocolSchedule preMergeProtocolSchedule;
  @Mock ProtocolSchedule postMergeProtocolSchedule;

  @Mock TimestampSchedule timestampSchedule;
  @Mock BlockHeader blockHeader;

  private static final Difficulty TTD = Difficulty.of(100L);
  private static final long BLOCK_NUMBER = 29L;
  private static final long TIMESTAMP = 1L;
  private TransitionProtocolSchedule transitionProtocolSchedule;

  @Before
  public void setUp() {
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(protocolContext.getConsensusContext(MergeContext.class)).thenReturn(mergeContext);
    when(mergeContext.getTerminalTotalDifficulty()).thenReturn(TTD);
    when(blockHeader.getTimestamp()).thenReturn(TIMESTAMP);
    when(timestampSchedule.getByTimestamp(TIMESTAMP)).thenReturn(Optional.empty());

    transitionProtocolSchedule =
        new TransitionProtocolSchedule(
            preMergeProtocolSchedule, postMergeProtocolSchedule, mergeContext, timestampSchedule);
    transitionProtocolSchedule.setProtocolContext(protocolContext);
  }

  @Test
  public void returnPostMergeIfFinalizedExists() {
    when(mergeContext.getFinalized()).thenReturn(Optional.of(mock(BlockHeader.class)));

    transitionProtocolSchedule.getByBlockHeaderForBws(blockHeader);

    verifyPostMergeProtocolScheduleReturned();
  }

  @Test
  public void returnPreMergeIfBeforeMerge() {
    when(mergeContext.getFinalized()).thenReturn(Optional.empty());
    when(mergeContext.isPostMerge()).thenReturn(false);

    transitionProtocolSchedule.getByBlockHeaderForBws(blockHeader);

    verifyPreMergeProtocolScheduleReturned();
  }

  @Test
  public void returnPreMergeIfTerminalPoWBlock() {

    when(mergeContext.getFinalized()).thenReturn(Optional.empty());
    when(mergeContext.isPostMerge()).thenReturn(true);

    final Hash parentHash = Hash.fromHexStringLenient("0xabc123");

    when(blockHeader.getParentHash()).thenReturn(parentHash);
    when(blockHeader.getDifficulty()).thenReturn(Difficulty.of(10L));
    when(blockchain.getTotalDifficultyByHash(parentHash))
        .thenReturn(Optional.of(Difficulty.of(95L)));

    transitionProtocolSchedule.getByBlockHeaderForBws(blockHeader);

    verifyPreMergeProtocolScheduleReturned();
  }

  @Test
  public void returnPreMergeIfAfterMergeButReorgPreTTD() {

    when(mergeContext.getFinalized()).thenReturn(Optional.empty());
    when(mergeContext.isPostMerge()).thenReturn(true);

    final Hash parentHash = Hash.fromHexStringLenient("0xabc123");

    when(blockHeader.getParentHash()).thenReturn(parentHash);
    when(blockHeader.getDifficulty()).thenReturn(Difficulty.of(2L));
    when(blockchain.getTotalDifficultyByHash(parentHash))
        .thenReturn(Optional.of(Difficulty.of(95L)));

    transitionProtocolSchedule.getByBlockHeaderForBws(blockHeader);

    verifyPreMergeProtocolScheduleReturned();
  }

  @Test
  public void returnPostMergeIfAfterMergeButReorgPostTTD() {

    when(mergeContext.getFinalized()).thenReturn(Optional.empty());
    when(mergeContext.isPostMerge()).thenReturn(true);

    final Hash parentHash = Hash.fromHexStringLenient("0xabc123");

    when(blockHeader.getNumber()).thenReturn(BLOCK_NUMBER);
    when(blockHeader.getParentHash()).thenReturn(parentHash);
    when(blockHeader.getDifficulty()).thenReturn(Difficulty.ZERO);
    when(blockchain.getTotalDifficultyByHash(parentHash))
        .thenReturn(Optional.of(Difficulty.of(105L)));

    transitionProtocolSchedule.getByBlockHeaderForBws(blockHeader);

    verifyPostMergeProtocolScheduleReturned();
  }

  @Test
  public void getByBlockHeader_returnsTimestampScheduleIfPresent() {
    when(timestampSchedule.getByTimestamp(TIMESTAMP))
        .thenReturn(Optional.of(mock(ProtocolSpec.class)));

    assertThat(transitionProtocolSchedule.getByBlockHeader(blockHeader)).isNotNull();

    verify(timestampSchedule).getByTimestamp(TIMESTAMP);
    verifyNoMergeScheduleInteractions();
  }

  @Test
  public void getByBlockHeader_delegatesToPreMergeScheduleWhenBlockNotFound() {
    when(mergeContext.isPostMerge()).thenReturn(false);

    transitionProtocolSchedule.getByBlockHeaderForBws(blockHeader);

    verifyPreMergeProtocolScheduleReturned();
  }

  @Test
  public void getByBlockHeader_delegatesToPostMergeScheduleWhenBlockNotFound() {
    when(blockHeader.getNumber()).thenReturn(BLOCK_NUMBER);
    when(blockHeader.getDifficulty()).thenReturn(Difficulty.ZERO);
    when(blockchain.getTotalDifficultyByHash(any())).thenReturn(Optional.of(TTD));
    when(mergeContext.isPostMerge()).thenReturn(true);

    transitionProtocolSchedule.getByBlockHeaderForBws(blockHeader);

    verifyPostMergeProtocolScheduleReturned();
  }

  @Test
  public void getByBlockHeader_delegatesToPostMergeScheduleWhenTimestampScheduleDoesNotExist() {
    when(mergeContext.isPostMerge()).thenReturn(true);
    when(blockHeader.getDifficulty()).thenReturn(Difficulty.ZERO);
    when(blockHeader.getNumber()).thenReturn(BLOCK_NUMBER);
    when(blockchain.getTotalDifficultyByHash(any())).thenReturn(Optional.of(TTD));

    transitionProtocolSchedule.getByBlockHeaderForBws(blockHeader);

    verifyPostMergeProtocolScheduleReturned();
  }

  private void verifyPreMergeProtocolScheduleReturned() {
    verify(preMergeProtocolSchedule).getByBlockHeader(any());
    verifyNoInteractions(postMergeProtocolSchedule);
  }

  private void verifyPostMergeProtocolScheduleReturned() {
    verify(postMergeProtocolSchedule).getByBlockHeader(any());
    verifyNoInteractions(preMergeProtocolSchedule);
  }

  private void verifyNoMergeScheduleInteractions() {
    verifyNoInteractions(preMergeProtocolSchedule);
    verifyNoInteractions(postMergeProtocolSchedule);
  }
}
