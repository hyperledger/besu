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
package org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.StalledDownloadException;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloadProcess;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.forest.storage.ForestWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.tasks.InMemoryTasksPriorityQueues;
import org.hyperledger.besu.testutil.TestClock;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

public class FastWorldDownloadStateTest {

  private static final Bytes ROOT_NODE_DATA = Bytes.of(1, 2, 3, 4);
  private static final Hash ROOT_NODE_HASH = Hash.hash(ROOT_NODE_DATA);
  private static final int MAX_REQUESTS_WITHOUT_PROGRESS = 10;
  private static final long MIN_MILLIS_BEFORE_STALLING = 50_000;

  private WorldStateKeyValueStorage worldStateKeyValueStorage;

  private WorldStateStorageCoordinator worldStateStorageCoordinator;

  private final BlockHeader header =
      new BlockHeaderTestFixture().stateRoot(ROOT_NODE_HASH).buildHeader();
  private final InMemoryTasksPriorityQueues<NodeDataRequest> pendingRequests =
      new InMemoryTasksPriorityQueues<>();
  private final WorldStateDownloadProcess worldStateDownloadProcess =
      mock(WorldStateDownloadProcess.class);

  private final TestClock clock = new TestClock();
  private FastWorldDownloadState downloadState;

  private CompletableFuture<Void> future;

  static class FastWorldDownloadStateTestArguments implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(DataStorageFormat.BONSAI), Arguments.of(DataStorageFormat.FOREST));
    }
  }

  public void setUp(final DataStorageFormat storageFormat) {
    if (storageFormat == DataStorageFormat.BONSAI) {
      worldStateKeyValueStorage =
          new BonsaiWorldStateKeyValueStorage(
              new InMemoryKeyValueStorageProvider(),
              new NoOpMetricsSystem(),
              DataStorageConfiguration.DEFAULT_BONSAI_CONFIG);
    } else {
      worldStateKeyValueStorage =
          new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    }
    worldStateStorageCoordinator = new WorldStateStorageCoordinator(worldStateKeyValueStorage);

    downloadState =
        new FastWorldDownloadState(
            worldStateStorageCoordinator,
            pendingRequests,
            MAX_REQUESTS_WITHOUT_PROGRESS,
            MIN_MILLIS_BEFORE_STALLING,
            clock,
            SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS);
    assertThat(downloadState.isDownloading()).isTrue();
    downloadState.setRootNodeData(ROOT_NODE_DATA);
    future = downloadState.getDownloadFuture();
  }

  @ParameterizedTest
  @ArgumentsSource(FastWorldDownloadStateTestArguments.class)
  public void shouldCompleteReturnedFutureWhenNoPendingTasksRemain(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    downloadState.checkCompletion(header);

    assertThat(future).isCompleted();
    assertThat(downloadState.isDownloading()).isFalse();
  }

  @ParameterizedTest
  @ArgumentsSource(FastWorldDownloadStateTestArguments.class)
  public void shouldStoreRootNodeBeforeReturnedFutureCompletes(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final CompletableFuture<Void> postFutureChecks =
        future.thenAccept(
            result ->
                assertThat(
                        worldStateStorageCoordinator.getAccountStateTrieNode(
                            Bytes.EMPTY, ROOT_NODE_HASH))
                    .contains(ROOT_NODE_DATA));

    downloadState.checkCompletion(header);

    assertThat(future).isCompleted();
    assertThat(postFutureChecks).isCompleted();
  }

  @ParameterizedTest
  @ArgumentsSource(FastWorldDownloadStateTestArguments.class)
  public void shouldNotCompleteWhenThereArePendingTasks(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    pendingRequests.add(
        NodeDataRequest.createAccountDataRequest(Hash.EMPTY_TRIE_HASH, Optional.empty()));

    downloadState.checkCompletion(header);

    assertThat(future).isNotDone();
    assertThat(worldStateStorageCoordinator.getAccountStateTrieNode(Bytes.EMPTY, ROOT_NODE_HASH))
        .isEmpty();
    assertThat(downloadState.isDownloading()).isTrue();
  }

  @ParameterizedTest
  @ArgumentsSource(FastWorldDownloadStateTestArguments.class)
  public void shouldCancelOutstandingTasksWhenFutureIsCancelled(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final EthTask<?> outstandingTask1 = mock(EthTask.class);
    final EthTask<?> outstandingTask2 = mock(EthTask.class);
    downloadState.addOutstandingTask(outstandingTask1);
    downloadState.addOutstandingTask(outstandingTask2);

    pendingRequests.add(
        NodeDataRequest.createAccountDataRequest(Hash.EMPTY_TRIE_HASH, Optional.empty()));
    pendingRequests.add(NodeDataRequest.createAccountDataRequest(Hash.EMPTY, Optional.empty()));
    downloadState.setWorldStateDownloadProcess(worldStateDownloadProcess);

    future.cancel(true);

    verify(outstandingTask1).cancel();
    verify(outstandingTask2).cancel();

    assertThat(pendingRequests.isEmpty()).isTrue();
    verify(worldStateDownloadProcess).abort();
    assertThat(downloadState.isDownloading()).isFalse();
  }

  @ParameterizedTest
  @ArgumentsSource(FastWorldDownloadStateTestArguments.class)
  public void shouldResetRequestsSinceProgressCountWhenProgressIsMade(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
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

  @ParameterizedTest
  @ArgumentsSource(FastWorldDownloadStateTestArguments.class)
  public void shouldNotBeStalledWhenMaxRequestsReachedUntilMinimumTimeAlsoReached(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
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

  @ParameterizedTest
  @ArgumentsSource(FastWorldDownloadStateTestArguments.class)
  public void shouldNotBeStalledIfMinimumTimeIsReachedButMaximumRequestsIsNot(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    clock.stepMillis(MIN_MILLIS_BEFORE_STALLING + 1);
    downloadState.requestComplete(false);
    assertThat(downloadState.getDownloadFuture()).isNotDone();
  }

  @ParameterizedTest
  @ArgumentsSource(FastWorldDownloadStateTestArguments.class)
  public void shouldResetTimeSinceProgressWhenProgressIsMade(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
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

  @ParameterizedTest
  @ArgumentsSource(FastWorldDownloadStateTestArguments.class)
  public void shouldNotAddRequestsAfterDownloadIsCompleted(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    downloadState.checkCompletion(header);

    downloadState.enqueueRequests(
        Stream.of(
            NodeDataRequest.createAccountDataRequest(Hash.EMPTY_TRIE_HASH, Optional.empty())));
    downloadState.enqueueRequest(
        NodeDataRequest.createAccountDataRequest(Hash.EMPTY_TRIE_HASH, Optional.empty()));

    assertThat(pendingRequests.isEmpty()).isTrue();
  }

  private void assertWorldStateStalled(final FastWorldDownloadState state) {
    final CompletableFuture<Void> future = state.getDownloadFuture();
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .isInstanceOf(ExecutionException.class)
        .hasRootCauseInstanceOf(StalledDownloadException.class);
  }

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}
