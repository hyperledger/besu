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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.TrailingPeerRequirements;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate.FastWorldStateDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate.NodeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.StalledDownloadException;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloader;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.TaskCollection;

import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

public class FastSyncDownloaderTest {

  private static final CompletableFuture<FastSyncState> COMPLETE =
      completedFuture(FastSyncState.EMPTY_SYNC_STATE);

  @SuppressWarnings("unchecked")
  private final FastSyncActions fastSyncActions = mock(FastSyncActions.class);

  private final WorldStateStorage worldStateStorage = mock(WorldStateStorage.class);

  private final WorldStateDownloader worldStateDownloader = mock(FastWorldStateDownloader.class);
  private final FastSyncStateStorage storage = mock(FastSyncStateStorage.class);

  @SuppressWarnings("unchecked")
  private final TaskCollection<NodeDataRequest> taskCollection = mock(TaskCollection.class);

  private final ChainDownloader chainDownloader = mock(ChainDownloader.class);

  private final Path fastSyncDataDirectory = null;

  private final FastSyncDownloader<NodeDataRequest> downloader =
      new FastSyncDownloader<>(
          fastSyncActions,
          worldStateStorage,
          worldStateDownloader,
          storage,
          taskCollection,
          fastSyncDataDirectory,
          FastSyncState.EMPTY_SYNC_STATE);

  @Before
  public void setup() {
    when(worldStateStorage.isWorldStateAvailable(any(), any())).thenReturn(true);
  }

  @Test
  public void shouldCompleteFastSyncSuccessfully() {
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(completedFuture(null));
    when(worldStateDownloader.run(
            any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader))))
        .thenReturn(completedFuture(null));

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(storage).storeState(downloadPivotBlockHeaderState);
    verify(fastSyncActions).createChainDownloader(downloadPivotBlockHeaderState);
    verify(chainDownloader).start();
    verify(worldStateDownloader)
        .run(any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader)));
    verifyNoMoreInteractions(fastSyncActions, worldStateDownloader, storage);
    assertThat(result).isCompletedWithValue(downloadPivotBlockHeaderState);
  }

  @Test
  public void shouldResumeFastSync() {
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState fastSyncState = new FastSyncState(pivotBlockHeader);
    final CompletableFuture<FastSyncState> complete = completedFuture(fastSyncState);
    when(fastSyncActions.waitForSuitablePeers(fastSyncState)).thenReturn(complete);
    when(fastSyncActions.selectPivotBlock(fastSyncState)).thenReturn(complete);
    when(fastSyncActions.downloadPivotBlockHeader(fastSyncState)).thenReturn(complete);
    when(fastSyncActions.createChainDownloader(fastSyncState)).thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(completedFuture(null));
    when(worldStateDownloader.run(
            any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader))))
        .thenReturn(completedFuture(null));

    final FastSyncDownloader<NodeDataRequest> resumedDownloader =
        new FastSyncDownloader<>(
            fastSyncActions,
            worldStateStorage,
            worldStateDownloader,
            storage,
            taskCollection,
            fastSyncDataDirectory,
            fastSyncState);

    final CompletableFuture<FastSyncState> result = resumedDownloader.start();

    verify(fastSyncActions).waitForSuitablePeers(fastSyncState);
    verify(fastSyncActions).selectPivotBlock(fastSyncState);
    verify(fastSyncActions).downloadPivotBlockHeader(fastSyncState);
    verify(storage).storeState(fastSyncState);
    verify(fastSyncActions).createChainDownloader(fastSyncState);
    verify(chainDownloader).start();
    verify(worldStateDownloader)
        .run(any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader)));
    verifyNoMoreInteractions(fastSyncActions, worldStateDownloader, storage);
    assertThat(result).isCompletedWithValue(fastSyncState);
  }

  @Test
  public void shouldAbortIfWaitForSuitablePeersFails() {
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(
            CompletableFuture.failedFuture(new FastSyncException(FastSyncError.UNEXPECTED_ERROR)));

    final CompletableFuture<FastSyncState> result = downloader.start();

    assertCompletedExceptionally(result, FastSyncError.UNEXPECTED_ERROR);

    verify(fastSyncActions).waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE);
    verifyNoMoreInteractions(fastSyncActions);
  }

  @Test
  public void shouldAbortIfSelectPivotBlockFails() {
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenThrow(new FastSyncException(FastSyncError.UNEXPECTED_ERROR));

    final CompletableFuture<FastSyncState> result = downloader.start();

    assertCompletedExceptionally(result, FastSyncError.UNEXPECTED_ERROR);

    verify(fastSyncActions).waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    verifyNoMoreInteractions(fastSyncActions);
  }

  @Test
  public void shouldAbortIfWorldStateDownloadFails() {
    final CompletableFuture<Void> worldStateFuture = new CompletableFuture<>();
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(chainFuture);
    when(worldStateDownloader.run(
            any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader))))
        .thenReturn(worldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(storage).storeState(downloadPivotBlockHeaderState);
    verify(fastSyncActions).createChainDownloader(downloadPivotBlockHeaderState);
    verify(worldStateDownloader)
        .run(any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader)));
    verifyNoMoreInteractions(fastSyncActions, worldStateDownloader, storage);

    assertThat(result).isNotDone();

    worldStateFuture.completeExceptionally(new FastSyncException(FastSyncError.NO_PEERS_AVAILABLE));
    verify(chainDownloader).cancel();
    chainFuture.completeExceptionally(new CancellationException());
    assertCompletedExceptionally(result, FastSyncError.NO_PEERS_AVAILABLE);
    assertThat(chainFuture).isCancelled();
  }

  @Test
  public void shouldAbortIfChainDownloadFails() {
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final CompletableFuture<Void> worldStateFuture = new CompletableFuture<>();
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(chainFuture);
    when(worldStateDownloader.run(
            any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader))))
        .thenReturn(worldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(fastSyncActions).createChainDownloader(downloadPivotBlockHeaderState);
    verify(worldStateDownloader)
        .run(any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader)));
    verifyNoMoreInteractions(fastSyncActions);
    verifyNoMoreInteractions(worldStateDownloader);

    assertThat(result).isNotDone();

    chainFuture.completeExceptionally(new FastSyncException(FastSyncError.NO_PEERS_AVAILABLE));
    assertCompletedExceptionally(result, FastSyncError.NO_PEERS_AVAILABLE);
    assertThat(worldStateFuture).isCancelled();
  }

  @Test
  public void shouldAbortIfStopped() {
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    doAnswer(
            invocation -> {
              CompletableFuture<FastSyncState> future = new CompletableFuture<>();
              Executors.newSingleThreadScheduledExecutor()
                  .schedule(
                      () -> future.complete(downloadPivotBlockHeaderState),
                      500,
                      TimeUnit.MILLISECONDS);
              return future;
            })
        .when(fastSyncActions)
        .downloadPivotBlockHeader(selectPivotBlockState);

    final CompletableFuture<FastSyncState> result = downloader.start();
    downloader.stop();

    Throwable thrown = catchThrowable(() -> result.get());
    assertThat(thrown).hasCauseExactlyInstanceOf(CancellationException.class);

    verify(fastSyncActions).waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(storage).storeState(downloadPivotBlockHeaderState);
    verify(worldStateDownloader).cancel();
    verifyNoMoreInteractions(fastSyncActions, worldStateDownloader, storage);
  }

  @Test
  public void shouldNotConsiderFastSyncCompleteIfOnlyWorldStateDownloadIsComplete() {
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final CompletableFuture<Void> worldStateFuture = new CompletableFuture<>();
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(chainFuture);
    when(worldStateDownloader.run(
            any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader))))
        .thenReturn(worldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(fastSyncActions).createChainDownloader(downloadPivotBlockHeaderState);
    verify(worldStateDownloader)
        .run(any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader)));
    verifyNoMoreInteractions(fastSyncActions);
    verifyNoMoreInteractions(worldStateDownloader);

    assertThat(result).isNotDone();

    worldStateFuture.complete(null);
    assertThat(result).isNotDone();
  }

  @Test
  public void shouldNotConsiderFastSyncCompleteIfOnlyChainDownloadIsComplete() {
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final CompletableFuture<Void> worldStateFuture = new CompletableFuture<>();
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(chainFuture);
    when(worldStateDownloader.run(
            any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader))))
        .thenReturn(worldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(fastSyncActions).createChainDownloader(downloadPivotBlockHeaderState);
    verify(worldStateDownloader)
        .run(any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader)));
    verifyNoMoreInteractions(fastSyncActions);
    verifyNoMoreInteractions(worldStateDownloader);

    assertThat(result).isNotDone();

    chainFuture.complete(null);
    assertThat(result).isNotDone();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldResetFastSyncStateAndRestartProcessIfWorldStateIsUnavailable() {
    final CompletableFuture<Void> firstWorldStateFuture = new CompletableFuture<>();
    final CompletableFuture<Void> secondWorldStateFuture = new CompletableFuture<>();
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final ChainDownloader secondChainDownloader = mock(ChainDownloader.class);
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final FastSyncState secondSelectPivotBlockState = new FastSyncState(90);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final BlockHeader secondPivotBlockHeader =
        new BlockHeaderTestFixture().number(90).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    final FastSyncState secondDownloadPivotBlockHeaderState =
        new FastSyncState(secondPivotBlockHeader);
    // First attempt
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(
            completedFuture(selectPivotBlockState), completedFuture(secondSelectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(chainFuture);
    when(worldStateDownloader.run(
            any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader))))
        .thenReturn(firstWorldStateFuture);

    // Second attempt with new pivot block
    when(fastSyncActions.downloadPivotBlockHeader(secondSelectPivotBlockState))
        .thenReturn(completedFuture(secondDownloadPivotBlockHeaderState));

    when(fastSyncActions.createChainDownloader(secondDownloadPivotBlockHeaderState))
        .thenReturn(secondChainDownloader);
    when(secondChainDownloader.start()).thenReturn(completedFuture(null));
    when(worldStateDownloader.run(
            any(FastSyncActions.class), eq(new FastSyncState(secondPivotBlockHeader))))
        .thenReturn(secondWorldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(storage).storeState(downloadPivotBlockHeaderState);
    verify(fastSyncActions).createChainDownloader(downloadPivotBlockHeaderState);
    verify(worldStateDownloader)
        .run(any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader)));
    verifyNoMoreInteractions(fastSyncActions, worldStateDownloader, storage);

    assertThat(result).isNotDone();

    firstWorldStateFuture.completeExceptionally(new StalledDownloadException("test"));
    assertThat(result).isNotDone();
    verify(chainDownloader).cancel();
    // A real chain downloader would cause the chainFuture to complete when cancel is called.
    chainFuture.completeExceptionally(new CancellationException());

    verify(fastSyncActions, times(2)).waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions, times(2)).selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(secondSelectPivotBlockState);
    verify(storage).storeState(secondDownloadPivotBlockHeaderState);
    verify(fastSyncActions).createChainDownloader(secondDownloadPivotBlockHeaderState);
    verify(worldStateDownloader)
        .run(any(FastSyncActions.class), eq(new FastSyncState(secondPivotBlockHeader)));
    verifyNoMoreInteractions(fastSyncActions, worldStateDownloader, storage);

    secondWorldStateFuture.complete(null);

    assertThat(result).isCompletedWithValue(secondDownloadPivotBlockHeaderState);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldResetFastSyncStateAndRestartProcessIfANonFastSyncExceptionOccurs() {
    final CompletableFuture<Void> firstWorldStateFuture = new CompletableFuture<>();
    final CompletableFuture<Void> secondWorldStateFuture = new CompletableFuture<>();
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final ChainDownloader secondChainDownloader = mock(ChainDownloader.class);
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final FastSyncState secondSelectPivotBlockState = new FastSyncState(90);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final BlockHeader secondPivotBlockHeader =
        new BlockHeaderTestFixture().number(90).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    final FastSyncState secondDownloadPivotBlockHeaderState =
        new FastSyncState(secondPivotBlockHeader);
    // First attempt
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(
            completedFuture(selectPivotBlockState), completedFuture(secondSelectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(chainFuture);
    when(worldStateDownloader.run(
            any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader))))
        .thenReturn(firstWorldStateFuture);
    when(fastSyncActions.scheduleFutureTask(any(), any()))
        .thenAnswer(invocation -> ((Supplier) invocation.getArgument(0)).get());

    // Second attempt
    when(fastSyncActions.downloadPivotBlockHeader(secondSelectPivotBlockState))
        .thenReturn(completedFuture(secondDownloadPivotBlockHeaderState));

    when(fastSyncActions.createChainDownloader(secondDownloadPivotBlockHeaderState))
        .thenReturn(secondChainDownloader);
    when(secondChainDownloader.start()).thenReturn(completedFuture(null));
    when(worldStateDownloader.run(
            any(FastSyncActions.class), eq(new FastSyncState(secondPivotBlockHeader))))
        .thenReturn(secondWorldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(storage).storeState(downloadPivotBlockHeaderState);
    verify(fastSyncActions).createChainDownloader(downloadPivotBlockHeaderState);
    verify(worldStateDownloader)
        .run(any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader)));
    verifyNoMoreInteractions(fastSyncActions, worldStateDownloader, storage);

    assertThat(result).isNotDone();

    firstWorldStateFuture.completeExceptionally(new RuntimeException("Test"));

    assertThat(result).isNotDone();
    verify(chainDownloader).cancel();
    // A real chain downloader would cause the chainFuture to complete when cancel is called.
    chainFuture.completeExceptionally(new CancellationException());

    verify(fastSyncActions).scheduleFutureTask(any(), any());
    verify(fastSyncActions, times(2)).waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions, times(2)).selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(secondSelectPivotBlockState);
    verify(storage).storeState(secondDownloadPivotBlockHeaderState);
    verify(fastSyncActions).createChainDownloader(secondDownloadPivotBlockHeaderState);
    verify(worldStateDownloader)
        .run(any(FastSyncActions.class), eq(new FastSyncState(secondPivotBlockHeader)));
    verifyNoMoreInteractions(fastSyncActions, worldStateDownloader, storage);

    secondWorldStateFuture.complete(null);

    assertThat(result).isCompletedWithValue(secondDownloadPivotBlockHeaderState);
  }

  @Test
  public void shouldNotHaveTrailingPeerRequirementsBeforePivotBlockSelected() {
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(new CompletableFuture<>());

    downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE);
    Assertions.assertThat(downloader.calculateTrailingPeerRequirements()).isEmpty();
  }

  @Test
  public void shouldNotAllowPeersBeforePivotBlockOnceSelected() {
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(new CompletableFuture<>());
    when(worldStateDownloader.run(
            any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader))))
        .thenReturn(new CompletableFuture<>());

    downloader.start();
    Assertions.assertThat(downloader.calculateTrailingPeerRequirements())
        .contains(new TrailingPeerRequirements(50, 0));
  }

  @Test
  public void shouldNotHaveTrailingPeerRequirementsAfterDownloadCompletes() {
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(completedFuture(null));
    when(worldStateDownloader.run(
            any(FastSyncActions.class), eq(new FastSyncState(pivotBlockHeader))))
        .thenReturn(completedFuture(null));

    final CompletableFuture<FastSyncState> result = downloader.start();
    assertThat(result).isDone();

    Assertions.assertThat(downloader.calculateTrailingPeerRequirements()).isEmpty();
  }

  private <T> void assertCompletedExceptionally(
      final CompletableFuture<T> future, final FastSyncError expectedError) {
    assertThat(future).isCompletedExceptionally();
    future.exceptionally(
        actualError -> {
          assertThat(actualError)
              .isInstanceOf(FastSyncException.class)
              .extracting(ex -> ((FastSyncException) ex).getError())
              .isEqualTo(expectedError);
          return null;
        });
  }
}
