/*
 * Copyright contributors to Hyperledger Besu
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PostMergeContextTest {

  @Mock private SyncState mockSyncState;

  private PostMergeContext postMergeContext;

  private MergeStateChangeCollector mergeStateChangeCollector;

  @Before
  public void setUp() {
    mergeStateChangeCollector = new MergeStateChangeCollector();
    postMergeContext = new PostMergeContext();
    postMergeContext.observeNewIsPostMergeState(mergeStateChangeCollector);
    postMergeContext.setSyncState(mockSyncState);
    postMergeContext.setTerminalTotalDifficulty(Difficulty.of(10L));
  }

  @Test
  public void switchFromPoWToPoSStopSyncAndCallsSubscribers() {
    when(mockSyncState.hasReachedTerminalDifficulty()).thenReturn(Optional.of(Boolean.TRUE));

    postMergeContext.setIsPostMerge(Difficulty.of(10L));

    verify(mockSyncState).setReachedTerminalDifficulty(true);
    assertThat(postMergeContext.isPostMerge()).isTrue();
    assertThat(postMergeContext.isSyncing()).isFalse();
    assertThat(mergeStateChangeCollector.stateChanges).containsExactly(true);
  }

  @Test
  public void setPrePoSStateNotStopSync() {
    when(mockSyncState.hasReachedTerminalDifficulty()).thenReturn(Optional.of(Boolean.FALSE));

    postMergeContext.setIsPostMerge(Difficulty.of(9L));

    verify(mockSyncState, never()).setReachedTerminalDifficulty(false);
    assertThat(postMergeContext.isPostMerge()).isFalse();
    assertThat(postMergeContext.isSyncing()).isTrue();
    assertThat(mergeStateChangeCollector.stateChanges).containsExactly(false);
  }

  @Test
  public void setPostPoWDifficultyAfterFinalizedBlockDoesNothing() {
    // first simulate a switch to PoS, and reset mocks and collectors
    postMergeContext.setIsPostMerge(Difficulty.of(11L));
    reset(mockSyncState);
    mergeStateChangeCollector.reset();

    // then perform the actual test
    BlockHeader mockFinalizedHeader = mock(BlockHeader.class);
    postMergeContext.setFinalized(mockFinalizedHeader);

    postMergeContext.setIsPostMerge(Difficulty.of(19L));

    verifyNoInteractions(mockSyncState);
    assertThat(postMergeContext.isPostMerge()).isTrue();
    assertThat(mergeStateChangeCollector.stateChanges).isEmpty();
  }

  @Test
  public void candidateHeadIsValidBeforeAnyFinalizedBlock() {
    assertThat(postMergeContext.getFinalized()).isEmpty();

    BlockHeader mockHeader = mock(BlockHeader.class);
    assertThat(postMergeContext.validateCandidateHead(mockHeader)).isTrue();
  }

  @Test
  public void candidateHeadIsValidIfAfterFinalizedBlock() {
    BlockHeader mockFinalizedHeader = mock(BlockHeader.class);
    when(mockFinalizedHeader.getNumber()).thenReturn(2L);
    postMergeContext.setFinalized(mockFinalizedHeader);

    BlockHeader mockHeader = mock(BlockHeader.class);
    when(mockHeader.getNumber()).thenReturn(3L);
    assertThat(postMergeContext.validateCandidateHead(mockHeader)).isTrue();
  }

  @Test
  public void candidateHeadIsInvalidIfBeforeFinalizedBlock() {
    BlockHeader mockFinalizedHeader = mock(BlockHeader.class);
    when(mockFinalizedHeader.getNumber()).thenReturn(3L);
    postMergeContext.setFinalized(mockFinalizedHeader);

    BlockHeader mockHeader = mock(BlockHeader.class);
    when(mockHeader.getNumber()).thenReturn(2L);
    assertThat(postMergeContext.validateCandidateHead(mockHeader)).isFalse();
  }

  @Test
  public void putAndRetrieveFirstPayload() {
    Block mockBlock = mock(Block.class);
    PayloadIdentifier firstPayloadId = new PayloadIdentifier(1L);
    postMergeContext.putPayloadById(firstPayloadId, mockBlock);

    assertThat(postMergeContext.retrieveBlockById(firstPayloadId)).contains(mockBlock);
  }

  @Test
  public void puttingTwoBlocksWithTheSamePayloadIdWeRetrieveTheLatter() {
    Block mockBlock1 = mock(Block.class);
    Block mockBlock2 = mock(Block.class);
    PayloadIdentifier payloadId = new PayloadIdentifier(1L);
    postMergeContext.putPayloadById(payloadId, mockBlock1);
    postMergeContext.putPayloadById(payloadId, mockBlock2);

    assertThat(postMergeContext.retrieveBlockById(payloadId)).contains(mockBlock2);
  }

  @Test
  public void tryingToRetrieveANotYetPutPayloadIdReturnsEmpty() {
    PayloadIdentifier payloadId = new PayloadIdentifier(1L);

    assertThat(postMergeContext.retrieveBlockById(payloadId)).isEmpty();
  }

  @Test
  public void tryingToRetrieveABlockPutButEvictedReturnsEmpty() {
    for (long i = 0; i < PostMergeContext.MAX_BLOCKS_IN_PROGRESS + 1; i++) {
      PayloadIdentifier payloadId = new PayloadIdentifier(i);
      Block mockBlock = mock(Block.class);
      postMergeContext.putPayloadById(payloadId, mockBlock);
    }

    PayloadIdentifier evictedPayloadId = new PayloadIdentifier(0L);

    assertThat(postMergeContext.retrieveBlockById(evictedPayloadId)).isEmpty();
  }

  private static class MergeStateChangeCollector implements MergeStateHandler {
    final List<Boolean> stateChanges = new ArrayList<>();

    @Override
    public void mergeStateChanged(
        final boolean isPoS,
        final Optional<Boolean> oldState,
        final Optional<Difficulty> difficultyStoppedAt) {
      stateChanges.add(isPoS);
    }

    public void reset() {
      stateChanges.clear();
    }
  }
}
