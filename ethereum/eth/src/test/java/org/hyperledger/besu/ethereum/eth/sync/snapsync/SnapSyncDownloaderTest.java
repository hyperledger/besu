/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.TrailingPeerRequirements;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.SyncError;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.SyncException;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.StalledDownloadException;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloader;
import org.hyperledger.besu.ethereum.trie.forest.storage.ForestWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.services.tasks.TaskCollection;

import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

public class SnapSyncDownloaderTest {

  @SuppressWarnings("unchecked")
  private final FastSyncActions fastSyncActions = mock(FastSyncActions.class);

  private final WorldStateDownloader worldStateDownloader = mock(WorldStateDownloader.class);
  private final FastSyncStateStorage storage = mock(FastSyncStateStorage.class);

  @SuppressWarnings("unchecked")
  private final TaskCollection<SnapDataRequest> taskCollection = mock(TaskCollection.class);

  private final ChainDownloader chainDownloader = mock(ChainDownloader.class);

  private final Path snapSyncDataDirectory = null;
  private WorldStateStorageCoordinator worldStateStorageCoordinator;
  private SnapSyncDownloader snapSyncDownloader;

  static class SnapSyncDownloaderTestArguments implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(DataStorageFormat.BONSAI), Arguments.of(DataStorageFormat.FOREST));
    }
  }

  public void setup(final DataStorageFormat dataStorageFormat) {
    final WorldStateKeyValueStorage worldStateKeyValueStorage;
    if (dataStorageFormat.equals(DataStorageFormat.BONSAI)) {
      worldStateKeyValueStorage = mock(BonsaiWorldStateKeyValueStorage.class);
      when(((BonsaiWorldStateKeyValueStorage) worldStateKeyValueStorage)
              .isWorldStateAvailable(any(), any()))
          .thenReturn(true);
    } else {
      worldStateKeyValueStorage = mock(ForestWorldStateKeyValueStorage.class);
      when(((ForestWorldStateKeyValueStorage) worldStateKeyValueStorage)
              .isWorldStateAvailable(any()))
          .thenReturn(true);
    }
    when(worldStateKeyValueStorage.getDataStorageFormat()).thenReturn(dataStorageFormat);
    worldStateStorageCoordinator = new WorldStateStorageCoordinator(worldStateKeyValueStorage);

    snapSyncDownloader =
        new SnapSyncDownloader(
            fastSyncActions,
            worldStateStorageCoordinator,
            worldStateDownloader,
            storage,
            taskCollection,
            snapSyncDataDirectory,
            new SnapSyncProcessState(FastSyncState.EMPTY_SYNC_STATE),
            SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS);
  }

  @ParameterizedTest
  @ArgumentsSource(SnapSyncDownloaderTestArguments.class)
  public void shouldStartSnapSyncSuccessfully(final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);

    when(fastSyncActions.selectPivotBlock(any(FastSyncState.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Test complete")));

    final CompletableFuture<FastSyncState> result = snapSyncDownloader.start();

    assertThat(result).isNotNull();
    verify(fastSyncActions).selectPivotBlock(any(FastSyncState.class));
  }

  @ParameterizedTest
  @ArgumentsSource(SnapSyncDownloaderTestArguments.class)
  public void shouldCreateSnapSyncProcessStateWhenStoringState(
      final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState inputState = new FastSyncState(pivotBlockHeader, false);

    final FastSyncState result = snapSyncDownloader.storeState(inputState);

    assertThat(result).isInstanceOf(SnapSyncProcessState.class);
    verify(storage).storeState(inputState);
  }

  @ParameterizedTest
  @ArgumentsSource(SnapSyncDownloaderTestArguments.class)
  public void shouldCreateSnapSyncDownloaderSuccessfully(
      final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);

    assertThat(snapSyncDownloader).isNotNull();
    assertThat(snapSyncDownloader).isInstanceOf(SnapSyncDownloader.class);
  }

  @ParameterizedTest
  @ArgumentsSource(SnapSyncDownloaderTestArguments.class)
  public void shouldCompleteSnapSyncSuccessfully(final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);
    final FastSyncState selectPivotBlockState = new FastSyncState(50, false);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader, false);
    when(fastSyncActions.selectPivotBlock(any(FastSyncState.class)))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(
            any(FastSyncState.class), eq(SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS)))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(completedFuture(null));
    when(worldStateDownloader.run(any(FastSyncActions.class), any(FastSyncState.class)))
        .thenReturn(completedFuture(null));

    final CompletableFuture<FastSyncState> result = snapSyncDownloader.start();

    verify(fastSyncActions).selectPivotBlock(any(FastSyncState.class));
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(storage).storeState(downloadPivotBlockHeaderState);
    verify(fastSyncActions)
        .createChainDownloader(
            any(FastSyncState.class), eq(SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS));
    verify(chainDownloader).start();
    verify(worldStateDownloader).run(any(FastSyncActions.class), any(FastSyncState.class));
    assertThat(result).isDone();
    assertThat(result.join()).isInstanceOf(SnapSyncProcessState.class);
  }

  @ParameterizedTest
  @ArgumentsSource(SnapSyncDownloaderTestArguments.class)
  public void shouldAbortIfSelectPivotBlockFails(final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);
    when(fastSyncActions.selectPivotBlock(any(FastSyncState.class)))
        .thenThrow(new SyncException(SyncError.UNEXPECTED_ERROR));

    final CompletableFuture<FastSyncState> result = snapSyncDownloader.start();

    assertCompletedExceptionally(result, SyncError.UNEXPECTED_ERROR);
    verify(fastSyncActions).selectPivotBlock(any(FastSyncState.class));
    verifyNoMoreInteractions(fastSyncActions);
  }

  @ParameterizedTest
  @ArgumentsSource(SnapSyncDownloaderTestArguments.class)
  public void shouldAbortIfWorldStateDownloadFails(final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);
    final CompletableFuture<Void> worldStateFuture = new CompletableFuture<>();
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final FastSyncState selectPivotBlockState = new FastSyncState(50, false);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader, false);

    when(fastSyncActions.selectPivotBlock(any(FastSyncState.class)))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(
            any(FastSyncState.class), eq(SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS)))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(chainFuture);
    when(worldStateDownloader.run(any(FastSyncActions.class), any(FastSyncState.class)))
        .thenReturn(worldStateFuture);

    final CompletableFuture<FastSyncState> result = snapSyncDownloader.start();

    assertThat(result).isNotDone();

    worldStateFuture.completeExceptionally(new SyncException(SyncError.NO_PEERS_AVAILABLE));
    verify(chainDownloader).cancel();
    chainFuture.completeExceptionally(new CancellationException());
    assertCompletedExceptionally(result, SyncError.NO_PEERS_AVAILABLE);
    assertThat(chainFuture).isCancelled();
  }

  @ParameterizedTest
  @ArgumentsSource(SnapSyncDownloaderTestArguments.class)
  public void shouldAbortIfChainDownloadFails(final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final CompletableFuture<Void> worldStateFuture = new CompletableFuture<>();
    final FastSyncState selectPivotBlockState = new FastSyncState(50, false);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader, false);

    when(fastSyncActions.selectPivotBlock(any(FastSyncState.class)))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(
            any(FastSyncState.class), eq(SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS)))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(chainFuture);
    when(worldStateDownloader.run(any(FastSyncActions.class), any(FastSyncState.class)))
        .thenReturn(worldStateFuture);

    final CompletableFuture<FastSyncState> result = snapSyncDownloader.start();

    assertThat(result).isNotDone();

    chainFuture.completeExceptionally(new SyncException(SyncError.NO_PEERS_AVAILABLE));
    assertCompletedExceptionally(result, SyncError.NO_PEERS_AVAILABLE);
    assertThat(worldStateFuture).isCancelled();
  }

  @ParameterizedTest
  @ArgumentsSource(SnapSyncDownloaderTestArguments.class)
  public void shouldAbortIfStopped(final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);
    final FastSyncState selectPivotBlockState = new FastSyncState(50, false);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader, false);

    when(fastSyncActions.selectPivotBlock(any(FastSyncState.class)))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenAnswer(
            invocation -> {
              CompletableFuture<FastSyncState> future = new CompletableFuture<>();
              Executors.newSingleThreadScheduledExecutor()
                  .schedule(
                      () -> future.complete(downloadPivotBlockHeaderState),
                      500,
                      TimeUnit.MILLISECONDS);
              return future;
            });

    final CompletableFuture<FastSyncState> result = snapSyncDownloader.start();
    snapSyncDownloader.stop();

    Throwable thrown = catchThrowable(() -> result.get());
    assertThat(thrown).hasCauseExactlyInstanceOf(CancellationException.class);

    verify(worldStateDownloader).cancel();
  }

  @SuppressWarnings("unchecked")
  @ParameterizedTest
  @ArgumentsSource(SnapSyncDownloaderTestArguments.class)
  public void shouldResetAndRetryOnStalledWorldStateDownload(
      final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);
    final CompletableFuture<Void> firstWorldStateFuture = new CompletableFuture<>();
    final CompletableFuture<Void> secondWorldStateFuture = new CompletableFuture<>();
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final ChainDownloader secondChainDownloader = mock(ChainDownloader.class);
    final FastSyncState selectPivotBlockState = new FastSyncState(50, false);
    final FastSyncState secondSelectPivotBlockState = new FastSyncState(90, false);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final BlockHeader secondPivotBlockHeader =
        new BlockHeaderTestFixture().number(90).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader, false);
    final FastSyncState secondDownloadPivotBlockHeaderState =
        new FastSyncState(secondPivotBlockHeader, false);

    // First attempt
    when(fastSyncActions.selectPivotBlock(any(FastSyncState.class)))
        .thenReturn(
            completedFuture(selectPivotBlockState), completedFuture(secondSelectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(
            any(FastSyncState.class), eq(SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS)))
        .thenReturn(chainDownloader, secondChainDownloader);
    when(chainDownloader.start()).thenReturn(chainFuture);
    when(worldStateDownloader.run(any(FastSyncActions.class), any(FastSyncState.class)))
        .thenReturn(firstWorldStateFuture, secondWorldStateFuture);

    // Second attempt
    when(fastSyncActions.downloadPivotBlockHeader(secondSelectPivotBlockState))
        .thenReturn(completedFuture(secondDownloadPivotBlockHeaderState));
    when(secondChainDownloader.start()).thenReturn(completedFuture(null));

    final CompletableFuture<FastSyncState> result = snapSyncDownloader.start();

    assertThat(result).isNotDone();

    // Trigger stalled download — should cause re-pivot
    firstWorldStateFuture.completeExceptionally(new StalledDownloadException("test"));
    assertThat(result).isNotDone();
    verify(chainDownloader).cancel();
    chainFuture.completeExceptionally(new CancellationException());

    // Complete second attempt
    secondWorldStateFuture.complete(null);

    assertThat(result).isDone();
    assertThat(result.join()).isInstanceOf(SnapSyncProcessState.class);
  }

  @ParameterizedTest
  @ArgumentsSource(SnapSyncDownloaderTestArguments.class)
  public void shouldNotHaveTrailingPeerRequirementsBeforePivotBlockSelected(
      final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);

    when(fastSyncActions.selectPivotBlock(any(FastSyncState.class)))
        .thenReturn(new CompletableFuture<>());

    snapSyncDownloader.start();
    Assertions.assertThat(snapSyncDownloader.calculateTrailingPeerRequirements()).isEmpty();
  }

  @ParameterizedTest
  @ArgumentsSource(SnapSyncDownloaderTestArguments.class)
  public void shouldNotAllowPeersBeforePivotBlockOnceSelected(
      final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);
    final FastSyncState selectPivotBlockState = new FastSyncState(50, false);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader, false);

    when(fastSyncActions.selectPivotBlock(any(FastSyncState.class)))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(
            any(FastSyncState.class), eq(SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS)))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(new CompletableFuture<>());
    when(worldStateDownloader.run(any(FastSyncActions.class), any(FastSyncState.class)))
        .thenReturn(new CompletableFuture<>());

    snapSyncDownloader.start();
    Assertions.assertThat(snapSyncDownloader.calculateTrailingPeerRequirements())
        .contains(new TrailingPeerRequirements(50, 0));
  }

  @ParameterizedTest
  @ArgumentsSource(SnapSyncDownloaderTestArguments.class)
  public void shouldNotHaveTrailingPeerRequirementsAfterDownloadCompletes(
      final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);
    final FastSyncState selectPivotBlockState = new FastSyncState(50, false);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader, false);

    when(fastSyncActions.selectPivotBlock(any(FastSyncState.class)))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(
            any(FastSyncState.class), eq(SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS)))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(completedFuture(null));
    when(worldStateDownloader.run(any(FastSyncActions.class), any(FastSyncState.class)))
        .thenReturn(completedFuture(null));

    final CompletableFuture<FastSyncState> result = snapSyncDownloader.start();
    assertThat(result).isDone();

    Assertions.assertThat(snapSyncDownloader.calculateTrailingPeerRequirements()).isEmpty();
  }

  private <T> void assertCompletedExceptionally(
      final CompletableFuture<T> future, final SyncError expectedError) {
    assertThat(future).isCompletedExceptionally();
    future.exceptionally(
        actualError -> {
          assertThat(actualError)
              .isInstanceOf(SyncException.class)
              .extracting(ex -> ((SyncException) ex).getError())
              .isEqualTo(expectedError);
          return null;
        });
  }

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}
