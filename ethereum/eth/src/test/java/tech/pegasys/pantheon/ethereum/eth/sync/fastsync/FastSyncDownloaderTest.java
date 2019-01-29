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
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncError.CHAIN_TOO_SHORT;
import static tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncError.NO_PEERS_AVAILABLE;
import static tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncError.UNEXPECTED_ERROR;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.eth.sync.worldstate.WorldStateDownloader;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import org.junit.Test;

public class FastSyncDownloaderTest {

  private static final CompletableFuture<Void> COMPLETE = completedFuture(null);

  @SuppressWarnings("unchecked")
  private final FastSyncActions<Void> fastSyncActions = mock(FastSyncActions.class);

  private final WorldStateDownloader worldStateDownloader = mock(WorldStateDownloader.class);

  private final FastSyncDownloader<Void> downloader =
      new FastSyncDownloader<>(fastSyncActions, worldStateDownloader);

  @Test
  public void shouldCompleteFastSyncSuccessfully() {
    final FastSyncState selectPivotBlockState = new FastSyncState(OptionalLong.of(50));
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState =
        new FastSyncState(OptionalLong.of(50), Optional.of(pivotBlockHeader));
    when(fastSyncActions.waitForSuitablePeers()).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock()).thenReturn(selectPivotBlockState);
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.downloadChain(downloadPivotBlockHeaderState)).thenReturn(COMPLETE);
    when(worldStateDownloader.run(pivotBlockHeader)).thenReturn(COMPLETE);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers();
    verify(fastSyncActions).selectPivotBlock();
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(fastSyncActions).downloadChain(downloadPivotBlockHeaderState);
    verify(worldStateDownloader).run(pivotBlockHeader);
    verifyNoMoreInteractions(fastSyncActions);
    verifyNoMoreInteractions(worldStateDownloader);
    assertThat(result).isCompletedWithValue(downloadPivotBlockHeaderState);
  }

  @Test
  public void shouldAbortIfWaitForSuitablePeersFails() {
    when(fastSyncActions.waitForSuitablePeers())
        .thenReturn(completedExceptionally(new FastSyncException(UNEXPECTED_ERROR)));

    final CompletableFuture<FastSyncState> result = downloader.start();

    assertCompletedExceptionally(result, UNEXPECTED_ERROR);

    verify(fastSyncActions).waitForSuitablePeers();
    verifyNoMoreInteractions(fastSyncActions);
  }

  @Test
  public void shouldAbortIfSelectPivotBlockFails() {
    when(fastSyncActions.waitForSuitablePeers()).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock()).thenThrow(new FastSyncException(CHAIN_TOO_SHORT));

    final CompletableFuture<FastSyncState> result = downloader.start();

    assertCompletedExceptionally(result, CHAIN_TOO_SHORT);

    verify(fastSyncActions).waitForSuitablePeers();
    verify(fastSyncActions).selectPivotBlock();
    verifyNoMoreInteractions(fastSyncActions);
  }

  @Test
  public void shouldAbortIfWorldStateDownloadFails() {
    final CompletableFuture<Void> worldStateFuture = new CompletableFuture<>();
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final FastSyncState selectPivotBlockState = new FastSyncState(OptionalLong.of(50));
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState =
        new FastSyncState(OptionalLong.of(50), Optional.of(pivotBlockHeader));
    when(fastSyncActions.waitForSuitablePeers()).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock()).thenReturn(selectPivotBlockState);
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.downloadChain(downloadPivotBlockHeaderState)).thenReturn(chainFuture);
    when(worldStateDownloader.run(pivotBlockHeader)).thenReturn(worldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers();
    verify(fastSyncActions).selectPivotBlock();
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(fastSyncActions).downloadChain(downloadPivotBlockHeaderState);
    verify(worldStateDownloader).run(pivotBlockHeader);
    verifyNoMoreInteractions(fastSyncActions);
    verifyNoMoreInteractions(worldStateDownloader);

    assertThat(result).isNotDone();

    worldStateFuture.completeExceptionally(new FastSyncException(NO_PEERS_AVAILABLE));
    assertCompletedExceptionally(result, NO_PEERS_AVAILABLE);
    assertThat(chainFuture).isCancelled();
  }

  @Test
  public void shouldAbortIfChainDownloadFails() {
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final CompletableFuture<Void> worldStateFuture = new CompletableFuture<>();
    final FastSyncState selectPivotBlockState = new FastSyncState(OptionalLong.of(50));
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState =
        new FastSyncState(OptionalLong.of(50), Optional.of(pivotBlockHeader));
    when(fastSyncActions.waitForSuitablePeers()).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock()).thenReturn(selectPivotBlockState);
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.downloadChain(downloadPivotBlockHeaderState)).thenReturn(chainFuture);
    when(worldStateDownloader.run(pivotBlockHeader)).thenReturn(worldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers();
    verify(fastSyncActions).selectPivotBlock();
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(fastSyncActions).downloadChain(downloadPivotBlockHeaderState);
    verify(worldStateDownloader).run(pivotBlockHeader);
    verifyNoMoreInteractions(fastSyncActions);
    verifyNoMoreInteractions(worldStateDownloader);

    assertThat(result).isNotDone();

    chainFuture.completeExceptionally(new FastSyncException(NO_PEERS_AVAILABLE));
    assertCompletedExceptionally(result, NO_PEERS_AVAILABLE);
    assertThat(worldStateFuture).isCancelled();
  }

  @Test
  public void shouldNotConsiderFastSyncCompleteIfOnlyWorldStateDownloadIsComplete() {
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final CompletableFuture<Void> worldStateFuture = new CompletableFuture<>();
    final FastSyncState selectPivotBlockState = new FastSyncState(OptionalLong.of(50));
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState =
        new FastSyncState(OptionalLong.of(50), Optional.of(pivotBlockHeader));
    when(fastSyncActions.waitForSuitablePeers()).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock()).thenReturn(selectPivotBlockState);
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.downloadChain(downloadPivotBlockHeaderState)).thenReturn(chainFuture);
    when(worldStateDownloader.run(pivotBlockHeader)).thenReturn(worldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers();
    verify(fastSyncActions).selectPivotBlock();
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(fastSyncActions).downloadChain(downloadPivotBlockHeaderState);
    verify(worldStateDownloader).run(pivotBlockHeader);
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
    final FastSyncState selectPivotBlockState = new FastSyncState(OptionalLong.of(50));
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState =
        new FastSyncState(OptionalLong.of(50), Optional.of(pivotBlockHeader));
    when(fastSyncActions.waitForSuitablePeers()).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock()).thenReturn(selectPivotBlockState);
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.downloadChain(downloadPivotBlockHeaderState)).thenReturn(chainFuture);
    when(worldStateDownloader.run(pivotBlockHeader)).thenReturn(worldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers();
    verify(fastSyncActions).selectPivotBlock();
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(fastSyncActions).downloadChain(downloadPivotBlockHeaderState);
    verify(worldStateDownloader).run(pivotBlockHeader);
    verifyNoMoreInteractions(fastSyncActions);
    verifyNoMoreInteractions(worldStateDownloader);

    assertThat(result).isNotDone();

    chainFuture.complete(null);
    assertThat(result).isNotDone();
  }

  private <T> CompletableFuture<T> completedExceptionally(final Throwable error) {
    final CompletableFuture<T> result = new CompletableFuture<>();
    result.completeExceptionally(error);
    return result;
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
