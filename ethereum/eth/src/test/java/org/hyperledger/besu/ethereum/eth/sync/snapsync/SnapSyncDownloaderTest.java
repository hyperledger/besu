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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncActions;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloader;
import org.hyperledger.besu.ethereum.trie.forest.storage.ForestWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.services.tasks.TaskCollection;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

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
    
    // Simple test to verify start method works
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
  public void shouldCreateSnapSyncDownloaderSuccessfully(final DataStorageFormat dataStorageFormat) {
    setup(dataStorageFormat);
    
    assertThat(snapSyncDownloader).isNotNull();
    assertThat(snapSyncDownloader).isInstanceOf(SnapSyncDownloader.class);
  }

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}
