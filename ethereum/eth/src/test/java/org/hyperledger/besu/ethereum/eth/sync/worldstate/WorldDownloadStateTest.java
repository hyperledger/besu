/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.eth.sync.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.tasks.CachingTaskCollection;
import org.hyperledger.besu.services.tasks.InMemoryTaskQueue;
import org.hyperledger.besu.testutil.TestClock;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;

public class WorldDownloadStateTest {

  private static final BytesValue ROOT_NODE_DATA = BytesValue.of(1, 2, 3, 4);
  private static final Hash ROOT_NODE_HASH = Hash.hash(ROOT_NODE_DATA);
  private static final int MAX_REQUESTS_WITHOUT_PROGRESS = 10;
  private static final long MIN_MILLIS_BEFORE_STALLING = 50_000;

  private final WorldStateStorage worldStateStorage =
      new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());

  private final BlockHeader header =
      new BlockHeaderTestFixture().stateRoot(ROOT_NODE_HASH).buildHeader();
  private final CachingTaskCollection<NodeDataRequest> pendingRequests =
      new CachingTaskCollection<>(new InMemoryTaskQueue<>());
  private final WorldStateDownloadProcess worldStateDownloadProcess =
      mock(WorldStateDownloadProcess.class);

  private final TestClock clock = new TestClock();
  private final WorldDownloadState downloadState =
      new WorldDownloadState(
          pendingRequests, MAX_REQUESTS_WITHOUT_PROGRESS, MIN_MILLIS_BEFORE_STALLING, clock);

  private final CompletableFuture<Void> future = downloadState.getDownloadFuture();

  @Before
  public void setUp() {
    downloadState.setRootNodeData(ROOT_NODE_DATA);
    assertThat(downloadState.isDownloading()).isTrue();
  }

  @Test
  public void shouldCompleteReturnedFutureWhenNoPendingTasksRemain() {
    downloadState.checkCompletion(worldStateStorage, header);

    assertThat(future).isCompleted();
    assertThat(downloadState.isDownloading()).isFalse();
  }

  @Test
  public void shouldStoreRootNodeBeforeReturnedFutureCompletes() {
    final CompletableFuture<Void> postFutureChecks =
        future.thenAccept(
            result ->
                assertThat(worldStateStorage.getAccountStateTrieNode(ROOT_NODE_HASH))
                    .contains(ROOT_NODE_DATA));

    downloadState.checkCompletion(worldStateStorage, header);

    assertThat(future).isCompleted();
    assertThat(postFutureChecks).isCompleted();
  }

  @Test
  public void shouldNotCompleteWhenThereArePendingTasks() {
    pendingRequests.add(NodeDataRequest.createAccountDataRequest(Hash.EMPTY_TRIE_HASH));

    downloadState.checkCompletion(worldStateStorage, header);

    assertThat(future).isNotDone();
    assertThat(worldStateStorage.getAccountStateTrieNode(ROOT_NODE_HASH)).isEmpty();
    assertThat(downloadState.isDownloading()).isTrue();
  }

  @Test
  public void shouldCancelOutstandingTasksWhenFutureIsCancelled() {
    final EthTask<?> outstandingTask1 = mock(EthTask.class);
    final EthTask<?> outstandingTask2 = mock(EthTask.class);
    downloadState.addOutstandingTask(outstandingTask1);
    downloadState.addOutstandingTask(outstandingTask2);

    pendingRequests.add(NodeDataRequest.createAccountDataRequest(Hash.EMPTY_TRIE_HASH));
    pendingRequests.add(NodeDataRequest.createAccountDataRequest(Hash.EMPTY));
    downloadState.setWorldStateDownloadProcess(worldStateDownloadProcess);

    future.cancel(true);

    verify(outstandingTask1).cancel();
    verify(outstandingTask2).cancel();

    assertThat(pendingRequests.isEmpty()).isTrue();
    verify(worldStateDownloadProcess).abort();
    assertThat(downloadState.isDownloading()).isFalse();
  }

  @Test
  public void shouldResetRequestsSinceProgressCountWhenProgressIsMade() {
    downloadState.requestComplete(false);
    downloadState.requestComplete(false);

    downloadState.requestComplete(true);
    clock.stepMillis(MIN_MILLIS_BEFORE_STALLING + 1);

    for (int i = 0; i < MAX_REQUESTS_WITHOUT_PROGRESS - 1; i++) {
      downloadState.requestComplete(false);
      assertThat(downloadState.getDownloadFuture()).isNotDone();
    }

    downloadState.requestComplete(false);
    assertWorldStateStalled(downloadState);
  }

  @Test
  public void shouldNotBeStalledWhenMaxRequestsReachedUntilMinimumTimeAlsoReached() {
    for (int i = 0; i < MAX_REQUESTS_WITHOUT_PROGRESS; i++) {
      downloadState.requestComplete(false);
      assertThat(downloadState.getDownloadFuture()).isNotDone();
    }

    // Exceeding the requests without progress limit doesn't trigger stalled state
    downloadState.requestComplete(false);
    assertThat(downloadState.getDownloadFuture()).isNotDone();

    // Until the minimum time has elapsed, then the next request with no progress marks as stalled
    clock.stepMillis(MIN_MILLIS_BEFORE_STALLING + 1);
    downloadState.requestComplete(false);
    assertWorldStateStalled(downloadState);
  }

  @Test
  public void shouldNotBeStalledIfMinimumTimeIsReachedButMaximumRequestsIsNot() {
    clock.stepMillis(MIN_MILLIS_BEFORE_STALLING + 1);
    downloadState.requestComplete(false);
    assertThat(downloadState.getDownloadFuture()).isNotDone();
  }

  @Test
  public void shouldResetTimeSinceProgressWhenProgressIsMade() {
    // Enough time has progressed but the next request makes progress so we are not stalled.
    clock.stepMillis(MIN_MILLIS_BEFORE_STALLING + 1);
    downloadState.requestComplete(true);
    assertThat(downloadState.getDownloadFuture()).isNotDone();

    // We then reach the max number of requests without progress but the timer should have reset
    for (int i = 0; i < MAX_REQUESTS_WITHOUT_PROGRESS; i++) {
      downloadState.requestComplete(false);
      assertThat(downloadState.getDownloadFuture()).isNotDone();
    }
    assertThat(downloadState.getDownloadFuture()).isNotDone();
  }

  @Test
  public void shouldNotAddRequestsAfterDownloadIsCompleted() {
    downloadState.checkCompletion(worldStateStorage, header);

    downloadState.enqueueRequests(
        Stream.of(NodeDataRequest.createAccountDataRequest(Hash.EMPTY_TRIE_HASH)));
    downloadState.enqueueRequest(NodeDataRequest.createAccountDataRequest(Hash.EMPTY_TRIE_HASH));

    assertThat(pendingRequests.isEmpty()).isTrue();
  }

  private void assertWorldStateStalled(final WorldDownloadState state) {
    final CompletableFuture<Void> future = state.getDownloadFuture();
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .isInstanceOf(ExecutionException.class)
        .hasRootCauseInstanceOf(StalledDownloadException.class);
  }
}
