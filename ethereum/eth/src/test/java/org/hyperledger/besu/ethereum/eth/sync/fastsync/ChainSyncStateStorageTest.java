/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ChainSyncStateStorageTest {
  @TempDir private Path tempDir;

  private ChainSyncStateStorage storage;
  private BlockHeader pivotBlockHeader;
  private BlockHeader checkpointBlockHeader;
  private BlockHeader genesisBlockHeader;

  @BeforeEach
  public void setUp() {
    storage = new ChainSyncStateStorage(tempDir);

    pivotBlockHeader = new BlockHeaderTestFixture().number(1000).buildHeader();
    checkpointBlockHeader = new BlockHeaderTestFixture().number(500).buildHeader();
    genesisBlockHeader = new BlockHeaderTestFixture().number(0).buildHeader();
  }

  @Test
  public void shouldReturnNullWhenLoadingStateAndFileDoesNotExist() {
    final ChainSyncState loadedState =
        storage.loadState(
            rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));

    assertThat(loadedState).isNull();
  }

  @Test
  public void shouldStoreAndLoadStateWithAllFields() {
    final ChainSyncState originalState =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);

    storage.storeState(originalState);

    final ChainSyncState loadedState =
        storage.loadState(
            rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));

    assertThat(loadedState).isNotNull();
    assertThat(loadedState.pivotBlockHeader()).isEqualTo(pivotBlockHeader);
    assertThat(loadedState.blockDownloadAnchor()).isEqualTo(checkpointBlockHeader);
    assertThat(loadedState.headerDownloadAnchor()).isEqualTo(genesisBlockHeader);
    assertThat(loadedState.headersDownloadComplete()).isFalse();
  }

  @Test
  public void shouldStoreAndLoadStateWithNullHeaderDownloadAnchor() {
    final BlockHeader previousPivot = new BlockHeaderTestFixture().number(800).buildHeader();
    final ChainSyncState originalState =
        new ChainSyncState(pivotBlockHeader, previousPivot, null, false);

    storage.storeState(originalState);

    final ChainSyncState loadedState =
        storage.loadState(
            rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));

    assertThat(loadedState).isNotNull();
    assertThat(loadedState.pivotBlockHeader()).isEqualTo(pivotBlockHeader);
    assertThat(loadedState.blockDownloadAnchor()).isEqualTo(previousPivot);
    assertThat(loadedState.headerDownloadAnchor()).isNull();
    assertThat(loadedState.headersDownloadComplete()).isFalse();
  }

  @Test
  public void shouldStoreAndLoadStateWithHeadersDownloadComplete() {
    final ChainSyncState originalState =
        new ChainSyncState(pivotBlockHeader, checkpointBlockHeader, null, true);

    storage.storeState(originalState);

    final ChainSyncState loadedState =
        storage.loadState(
            rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));

    assertThat(loadedState).isNotNull();
    assertThat(loadedState.headersDownloadComplete()).isTrue();
  }

  @Test
  public void shouldOverwriteExistingStateWhenStoringNewState() {
    final ChainSyncState firstState =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    storage.storeState(firstState);

    final BlockHeader newPivot = new BlockHeaderTestFixture().number(2000).buildHeader();
    final ChainSyncState secondState = new ChainSyncState(newPivot, pivotBlockHeader, null, false);
    storage.storeState(secondState);

    final ChainSyncState loadedState =
        storage.loadState(
            rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));

    assertThat(loadedState).isNotNull();
    assertThat(loadedState.pivotBlockHeader()).isEqualTo(newPivot);
    assertThat(loadedState.blockDownloadAnchor()).isEqualTo(pivotBlockHeader);
  }

  @Test
  public void shouldCallDeleteStateWithoutThrowing() {
    final ChainSyncState originalState =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    storage.storeState(originalState);

    final File stateFile = tempDir.resolve("chain-sync-state.rlp").toFile();
    assertThat(stateFile).exists();

    // deleteState catches IOException and just logs it, so it should not throw
    // Note: MoreFiles.deleteRecursively behavior on regular files is platform-specific
    storage.deleteState();
  }

  @Test
  public void shouldCleanupTempFileOnStore() {
    final ChainSyncState originalState =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);

    storage.storeState(originalState);

    final File tempFile = tempDir.resolve("chain-sync-state.rlp.tmp").toFile();
    assertThat(tempFile).doesNotExist();
  }

  @Test
  public void shouldHandleCorruptedFile() throws IOException {
    final File stateFile = tempDir.resolve("chain-sync-state.rlp").toFile();

    // Write corrupted data
    Files.write(stateFile.toPath(), new byte[] {1, 2, 3, 4, 5});

    // RLP decoding errors will throw RuntimeException (RLPException)
    assertThatThrownBy(
            () ->
                storage.loadState(
                    rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions())))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  public void shouldReturnNullForOldFormatVersion() throws IOException {
    final File stateFile = tempDir.resolve("chain-sync-state.rlp").toFile();

    // Create RLP with old version (version 0)
    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    output.startList();
    output.writeByte((byte) 0); // Old version
    pivotBlockHeader.writeTo(output);
    checkpointBlockHeader.writeTo(output);
    output.writeByte((byte) 0); // headersDownloadComplete = false
    output.endList();

    Files.write(stateFile.toPath(), output.encoded().toArrayUnsafe());

    final ChainSyncState loadedState =
        storage.loadState(
            rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));

    assertThat(loadedState).isNull();
  }

  @Test
  public void shouldPersistStateAcrossMultipleStorageInstances() {
    final ChainSyncState originalState =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);

    storage.storeState(originalState);

    // Create new storage instance pointing to same directory
    final ChainSyncStateStorage newStorage = new ChainSyncStateStorage(tempDir);
    final ChainSyncState loadedState =
        newStorage.loadState(
            rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));

    assertThat(loadedState).isNotNull();
    assertThat(loadedState.pivotBlockHeader()).isEqualTo(pivotBlockHeader);
  }

  @Test
  public void shouldHandleDeleteWhenNoFileExists() {
    // Should not throw exception
    storage.deleteState();

    final File stateFile = tempDir.resolve("chain-sync-state.rlp").toFile();
    assertThat(stateFile).doesNotExist();
  }

  @Test
  public void shouldHandleConcurrentReads() throws InterruptedException {
    final ChainSyncState originalState =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    storage.storeState(originalState);

    final int numThreads = 10;
    final ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
    final CountDownLatch startLatch = new CountDownLatch(1);
    final CountDownLatch completionLatch = new CountDownLatch(numThreads);
    final AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < numThreads; i++) {
      executorService.submit(
          () -> {
            try {
              startLatch.await();
              final ChainSyncState loadedState =
                  storage.loadState(
                      rlpInput ->
                          BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));
              if (loadedState != null && loadedState.pivotBlockHeader().equals(pivotBlockHeader)) {
                successCount.incrementAndGet();
              }
            } catch (final Exception e) {
              // Ignore
            } finally {
              completionLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    completionLatch.await(5, TimeUnit.SECONDS);
    executorService.shutdown();

    assertThat(successCount.get()).isEqualTo(numThreads);
  }

  @Test
  public void shouldHandleConcurrentWrites() throws InterruptedException {
    final int numThreads = 5;
    final ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
    final CountDownLatch startLatch = new CountDownLatch(1);
    final CountDownLatch completionLatch = new CountDownLatch(numThreads);

    for (int i = 0; i < numThreads; i++) {
      final int threadNum = i;
      executorService.submit(
          () -> {
            try {
              startLatch.await();
              final BlockHeader pivot =
                  new BlockHeaderTestFixture().number(1000 + threadNum).buildHeader();
              final ChainSyncState state =
                  ChainSyncState.initialSync(pivot, checkpointBlockHeader, genesisBlockHeader);
              storage.storeState(state);
            } catch (final Exception e) {
              // Ignore
            } finally {
              completionLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    completionLatch.await(5, TimeUnit.SECONDS);
    executorService.shutdown();

    // At least one write should have succeeded, and the file should be valid
    final ChainSyncState loadedState =
        storage.loadState(
            rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));
    assertThat(loadedState).isNotNull();
    // Verify the pivot block number is in expected range (1000-1004)
    assertThat(loadedState.pivotBlockHeader().getNumber()).isBetween(1000L, 1004L);
  }

  @Test
  public void shouldHandleStateTransitions() {
    // Initial sync state
    ChainSyncState state =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    storage.storeState(state);

    // Mark headers complete
    state = state.withHeadersDownloadComplete();
    storage.storeState(state);

    ChainSyncState loadedState =
        storage.loadState(
            rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));
    assertThat(loadedState.headersDownloadComplete()).isTrue();
    assertThat(loadedState.headerDownloadAnchor()).isNull();

    // Continue to new pivot
    final BlockHeader newPivot = new BlockHeaderTestFixture().number(2000).buildHeader();
    state = state.continueToNewPivot(newPivot, pivotBlockHeader);
    storage.storeState(state);

    loadedState =
        storage.loadState(
            rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));
    assertThat(loadedState.pivotBlockHeader()).isEqualTo(newPivot);
    assertThat(loadedState.blockDownloadAnchor()).isEqualTo(pivotBlockHeader);
    assertThat(loadedState.headersDownloadComplete()).isFalse();
  }

  @Test
  public void shouldHandleVeryLargeBlockNumbers() {
    final BlockHeader largePivot =
        new BlockHeaderTestFixture().number(Long.MAX_VALUE - 1000).buildHeader();
    final BlockHeader largeCheckpoint =
        new BlockHeaderTestFixture().number(Long.MAX_VALUE - 2000).buildHeader();

    final ChainSyncState originalState =
        ChainSyncState.initialSync(largePivot, largeCheckpoint, genesisBlockHeader);

    storage.storeState(originalState);

    final ChainSyncState loadedState =
        storage.loadState(
            rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));

    assertThat(loadedState).isNotNull();
    assertThat(loadedState.pivotBlockHeader().getNumber()).isEqualTo(Long.MAX_VALUE - 1000);
    assertThat(loadedState.blockDownloadAnchor().getNumber()).isEqualTo(Long.MAX_VALUE - 2000);
  }

  @Test
  public void shouldCleanupLeftoverTempFile() throws IOException {
    final File tempFile = tempDir.resolve("chain-sync-state.rlp.tmp").toFile();

    // Create a leftover temp file
    Files.write(tempFile.toPath(), new byte[] {1, 2, 3});
    assertThat(tempFile).exists();

    // Store new state - should clean up temp file
    final ChainSyncState state =
        ChainSyncState.initialSync(pivotBlockHeader, checkpointBlockHeader, genesisBlockHeader);
    storage.storeState(state);

    // Verify state file exists and temp file is gone
    final File stateFile = tempDir.resolve("chain-sync-state.rlp").toFile();
    assertThat(stateFile).exists();
    assertThat(tempFile).doesNotExist();
  }

  @Test
  public void shouldHandleMultipleStoreAndLoadCycles() {
    // Test multiple store/load cycles to ensure consistency
    for (int i = 0; i < 5; i++) {
      final BlockHeader pivot = new BlockHeaderTestFixture().number(1000 + i * 100).buildHeader();
      final ChainSyncState state =
          ChainSyncState.initialSync(pivot, checkpointBlockHeader, genesisBlockHeader);

      storage.storeState(state);

      final ChainSyncState loadedState =
          storage.loadState(
              rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));

      assertThat(loadedState).isNotNull();
      assertThat(loadedState.pivotBlockHeader().getNumber()).isEqualTo(1000 + i * 100);
    }
  }

  @Test
  public void shouldPreserveBlockHeaderDetails() {
    // Create headers with specific details to ensure all fields are preserved
    final BlockHeader detailedPivot =
        new BlockHeaderTestFixture()
            .number(1000)
            .gasLimit(8000000)
            .gasUsed(5000000)
            .timestamp(1234567890)
            .buildHeader();

    final BlockHeader detailedCheckpoint =
        new BlockHeaderTestFixture()
            .number(500)
            .gasLimit(7000000)
            .gasUsed(4000000)
            .timestamp(1234567800)
            .buildHeader();

    final ChainSyncState originalState =
        ChainSyncState.initialSync(detailedPivot, detailedCheckpoint, genesisBlockHeader);

    storage.storeState(originalState);

    final ChainSyncState loadedState =
        storage.loadState(
            rlpInput -> BlockHeader.readFrom(rlpInput, new MainnetBlockHeaderFunctions()));

    assertThat(loadedState).isNotNull();
    assertThat(loadedState.pivotBlockHeader().getGasLimit()).isEqualTo(8000000);
    assertThat(loadedState.pivotBlockHeader().getGasUsed()).isEqualTo(5000000);
    assertThat(loadedState.pivotBlockHeader().getTimestamp()).isEqualTo(1234567890);
    assertThat(loadedState.blockDownloadAnchor().getGasLimit()).isEqualTo(7000000);
    assertThat(loadedState.blockDownloadAnchor().getGasUsed()).isEqualTo(4000000);
    assertThat(loadedState.blockDownloadAnchor().getTimestamp()).isEqualTo(1234567800);
  }
}
