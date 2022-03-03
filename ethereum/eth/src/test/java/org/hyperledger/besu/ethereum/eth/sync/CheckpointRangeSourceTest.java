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
package org.hyperledger.besu.ethereum.eth.sync;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.NoAvailablePeersException;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.SyncTerminationCondition;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.junit.Test;

public class CheckpointRangeSourceTest {

  private static final int CHECKPOINT_TIMEOUTS_PERMITTED = 3;
  private static final Duration RETRY_DELAY_DURATION = Duration.ofSeconds(2);
  private final EthPeer peer = mock(EthPeer.class);
  private final CheckpointHeaderFetcher checkpointFetcher = mock(CheckpointHeaderFetcher.class);
  private final CheckpointRangeSource.SyncTargetChecker syncTargetChecker =
      mock(CheckpointRangeSource.SyncTargetChecker.class);
  private final EthScheduler ethScheduler = mock(EthScheduler.class);

  private final BlockHeader commonAncestor = header(10);
  private final CheckpointRangeSource source =
      new CheckpointRangeSource(
          checkpointFetcher,
          syncTargetChecker,
          ethScheduler,
          peer,
          commonAncestor,
          CHECKPOINT_TIMEOUTS_PERMITTED,
          Duration.ofMillis(1),
          SyncTerminationCondition.never());

  @Test
  public void shouldHaveNextWhenNoCheckpointsLoadedButSyncTargetCheckerSaysToContinue() {
    when(syncTargetChecker.shouldContinueDownloadingFromSyncTarget(peer, commonAncestor))
        .thenReturn(true);
    assertThat(source).hasNext();
  }

  @Test
  public void shouldHaveNextWhenMoreCheckpointsAreLoadedRegardlessOfSyncTargetChecker() {
    when(checkpointFetcher.getNextCheckpointHeaders(peer, commonAncestor))
        .thenReturn(completedFuture(asList(header(15), header(20))));
    when(syncTargetChecker.shouldContinueDownloadingFromSyncTarget(peer, commonAncestor))
        .thenReturn(false);

    source.next();
    verify(checkpointFetcher).getNextCheckpointHeaders(peer, commonAncestor);

    assertThat(source).hasNext();
  }

  @Test
  public void shouldNotHaveNextWhenNoCheckpointsLoadedAndSyncTargetCheckerReturnsFalse() {
    // e.g. when a better sync target is available
    when(syncTargetChecker.shouldContinueDownloadingFromSyncTarget(peer, commonAncestor))
        .thenReturn(false);
    assertThat(source).isExhausted();
  }

  @Test
  public void shouldNotHaveNextWhenNoMoreCheckpointsAvailableAndRetryLimitReached() {
    when(syncTargetChecker.shouldContinueDownloadingFromSyncTarget(any(), any())).thenReturn(true);
    when(checkpointFetcher.getNextCheckpointHeaders(peer, commonAncestor))
        .thenReturn(CompletableFuture.failedFuture(new TimeoutException()));

    for (int i = 1; i <= CHECKPOINT_TIMEOUTS_PERMITTED; i++) {
      assertThat(source).hasNext();
      assertThat(source.next()).isNull();
      verify(checkpointFetcher, times(i)).getNextCheckpointHeaders(peer, commonAncestor);
    }

    // Too many timeouts, give up on this sync target.
    assertThat(source).isExhausted();
  }

  @Test
  public void shouldConsiderHeaderRequestFailedIfNoNewHeadersReturned() {
    when(syncTargetChecker.shouldContinueDownloadingFromSyncTarget(any(), any())).thenReturn(true);
    when(checkpointFetcher.getNextCheckpointHeaders(peer, commonAncestor))
        .thenReturn(completedFuture(emptyList()));

    for (int i = 1; i <= CHECKPOINT_TIMEOUTS_PERMITTED; i++) {
      assertThat(source).hasNext();
      assertThat(source.next()).isNull();
      verify(checkpointFetcher, times(i)).getNextCheckpointHeaders(peer, commonAncestor);
    }

    // Too many timeouts, give up on this sync target.
    assertThat(source).isExhausted();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldDelayBeforeRetryingRequestForCheckpointHeadersAfterEmptyResponse() {
    when(syncTargetChecker.shouldContinueDownloadingFromSyncTarget(any(), any())).thenReturn(true);
    when(checkpointFetcher.getNextCheckpointHeaders(peer, commonAncestor))
        .thenReturn(completedFuture(emptyList()));

    assertThat(source.next()).isNull();
    verify(checkpointFetcher).getNextCheckpointHeaders(peer, commonAncestor);
    verify(ethScheduler).scheduleFutureTask(any(Supplier.class), eq(RETRY_DELAY_DURATION));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldDelayBeforeRetryingRequestForCheckpointHeadersAfterFailure() {
    when(syncTargetChecker.shouldContinueDownloadingFromSyncTarget(any(), any())).thenReturn(true);
    when(checkpointFetcher.getNextCheckpointHeaders(peer, commonAncestor))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Nope")));

    assertThat(source.next()).isNull();
    verify(checkpointFetcher).getNextCheckpointHeaders(peer, commonAncestor);
    verify(ethScheduler).scheduleFutureTask(any(Supplier.class), eq(RETRY_DELAY_DURATION));
  }

  @Test
  public void shouldResetCheckpointFailureCountWhenMoreCheckpointsReceived() {
    when(syncTargetChecker.shouldContinueDownloadingFromSyncTarget(any(), any())).thenReturn(true);
    when(checkpointFetcher.getNextCheckpointHeaders(any(), any()))
        .thenReturn(CompletableFuture.failedFuture(new TimeoutException()))
        .thenReturn(CompletableFuture.failedFuture(new TimeoutException()))
        .thenReturn(completedFuture(singletonList(header(15))))
        .thenReturn(CompletableFuture.failedFuture(new TimeoutException()));

    assertThat(source.next()).isNull(); // Fail
    assertThat(source.next()).isNull(); // Fail
    assertThat(source.next()).isNotNull(); // Succeed
    assertThat(source.next()).isNull(); // Fail
    assertThat(source.next()).isNull(); // Fail

    // Failure count should have been reset by the success so limit hasn't been reached.
    assertThat(source).hasNext();
  }

  @Test
  public void shouldRequestMoreHeadersWhenCurrentSetHasRunOut() {
    when(checkpointFetcher.getNextCheckpointHeaders(peer, commonAncestor))
        .thenReturn(completedFuture(asList(header(15), header(20))));

    when(checkpointFetcher.getNextCheckpointHeaders(peer, header(20)))
        .thenReturn(completedFuture(asList(header(25), header(30))));

    assertThat(source.next()).isEqualTo(new CheckpointRange(peer, commonAncestor, header(15)));
    verify(checkpointFetcher).getNextCheckpointHeaders(peer, commonAncestor);
    verify(checkpointFetcher).nextCheckpointEndsAtChainHead(peer, commonAncestor);

    assertThat(source.next()).isEqualTo(new CheckpointRange(peer, header(15), header(20)));
    verifyNoMoreInteractions(checkpointFetcher);

    assertThat(source.next()).isEqualTo(new CheckpointRange(peer, header(20), header(25)));
    verify(checkpointFetcher).getNextCheckpointHeaders(peer, header(20));
    verify(checkpointFetcher).nextCheckpointEndsAtChainHead(peer, header(20));

    assertThat(source.next()).isEqualTo(new CheckpointRange(peer, header(25), header(30)));
    verifyNoMoreInteractions(checkpointFetcher);
  }

  @Test
  public void shouldReturnCheckpointsFromExistingBatch() {
    when(checkpointFetcher.getNextCheckpointHeaders(peer, commonAncestor))
        .thenReturn(completedFuture(asList(header(15), header(20))));

    assertThat(source.next()).isEqualTo(new CheckpointRange(peer, commonAncestor, header(15)));
    assertThat(source.next()).isEqualTo(new CheckpointRange(peer, header(15), header(20)));
  }

  @Test
  public void shouldReturnNullIfNewHeadersNotAvailableInTime() {
    when(checkpointFetcher.getNextCheckpointHeaders(peer, commonAncestor))
        .thenReturn(new CompletableFuture<>());

    assertThat(source.next()).isNull();
  }

  @Test
  public void shouldNotRequestMoreHeadersIfOriginalRequestStillInProgress() {
    when(checkpointFetcher.getNextCheckpointHeaders(peer, commonAncestor))
        .thenReturn(new CompletableFuture<>());

    assertThat(source.next()).isNull();
    verify(checkpointFetcher).getNextCheckpointHeaders(peer, commonAncestor);
    verify(checkpointFetcher).nextCheckpointEndsAtChainHead(peer, commonAncestor);

    assertThat(source.next()).isNull();
    verifyNoMoreInteractions(checkpointFetcher);
  }

  @Test
  public void shouldReturnCheckpointsOnceHeadersRequestCompletes() {
    final CompletableFuture<List<BlockHeader>> future = new CompletableFuture<>();
    when(checkpointFetcher.getNextCheckpointHeaders(peer, commonAncestor)).thenReturn(future);

    assertThat(source.next()).isNull();
    verify(checkpointFetcher).getNextCheckpointHeaders(peer, commonAncestor);

    future.complete(asList(header(15), header(20)));
    assertThat(source.next()).isEqualTo(new CheckpointRange(peer, commonAncestor, header(15)));
  }

  @Test
  public void shouldSendNewRequestIfRequestForHeadersFails() {
    when(checkpointFetcher.getNextCheckpointHeaders(peer, commonAncestor))
        .thenReturn(CompletableFuture.failedFuture(new NoAvailablePeersException()))
        .thenReturn(completedFuture(asList(header(15), header(20))));

    // Returns null when the first request fails
    assertThat(source.next()).isNull();
    verify(checkpointFetcher).getNextCheckpointHeaders(peer, commonAncestor);

    // Then retries
    assertThat(source.next()).isEqualTo(new CheckpointRange(peer, commonAncestor, header(15)));
    verify(checkpointFetcher, times(2)).getNextCheckpointHeaders(peer, commonAncestor);
  }

  @Test
  public void shouldReturnUnboundedCheckpointRangeWhenNextCheckpointEndsAtChainHead() {
    when(syncTargetChecker.shouldContinueDownloadingFromSyncTarget(peer, commonAncestor))
        .thenReturn(true);
    when(checkpointFetcher.nextCheckpointEndsAtChainHead(peer, commonAncestor)).thenReturn(true);

    assertThat(source).hasNext();
    assertThat(source.next()).isEqualTo(new CheckpointRange(peer, commonAncestor));

    // Once we've sent an open-ended range we shouldn't have any more ranges.
    assertThat(source).isExhausted();
    assertThat(source.next()).isNull();
  }

  private BlockHeader header(final int number) {
    return new BlockHeaderTestFixture().number(number).buildHeader();
  }
}
