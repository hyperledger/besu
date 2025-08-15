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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FastSyncStateStorageTest {
  @TempDir private Path tempDir;

  private FastSyncStateStorage storage;
  private final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().buildHeader();
  private final FastSyncState syncStateWithHeader = new FastSyncState(pivotBlockHeader, false);

  @BeforeEach
  public void setUp() throws Exception {
    storage = new FastSyncStateStorage(tempDir);
  }

  @Test
  public void shouldNotConsiderFastSyncInProgressWhenFileDoesNotExist() {
    assertThat(storage.isFastSyncInProgress()).isFalse();
  }

  @Test
  public void shouldConsiderFastSyncInProgressWhenFileExists() {
    storage.storeState(syncStateWithHeader);
    assertThat(storage.isFastSyncInProgress()).isTrue();

    final FastSyncStateStorage newStorage = new FastSyncStateStorage(tempDir);
    assertThat(newStorage.isFastSyncInProgress()).isTrue();
  }

  @Test
  public void shouldRoundTripHeader() {
    storage.storeState(syncStateWithHeader);
    assertThat(storage.loadState(new MainnetBlockHeaderFunctions())).isEqualTo(syncStateWithHeader);

    final FastSyncStateStorage newStorage = new FastSyncStateStorage(tempDir);
    assertThat(newStorage.loadState(new MainnetBlockHeaderFunctions()))
        .isEqualTo(syncStateWithHeader);
  }

  @Test
  public void shouldReturnEmptyWhenLoadingHeaderAndFileDoesNotExist() {
    assertThat(storage.loadState(new MainnetBlockHeaderFunctions()))
        .isEqualTo(FastSyncState.EMPTY_SYNC_STATE);
  }

  @Test
  public void shouldRemoveStateFileWhenStoringFastSyncWithoutBlockHeader() {
    storage.storeState(syncStateWithHeader);
    assertThat(storage.loadState(new MainnetBlockHeaderFunctions())).isEqualTo(syncStateWithHeader);

    storage.storeState(FastSyncState.EMPTY_SYNC_STATE);
    assertThat(storage.loadState(new MainnetBlockHeaderFunctions()))
        .isEqualTo(FastSyncState.EMPTY_SYNC_STATE);
  }

  @Test
  public void shouldStoreAndLoadStateWithSourceIsTrustedTrue() {
    final FastSyncState stateWithTrustedSource = new FastSyncState(pivotBlockHeader, true);
    storage.storeState(stateWithTrustedSource);

    final FastSyncState loadedState = storage.loadState(new MainnetBlockHeaderFunctions());
    assertThat(loadedState.getPivotBlockHeader())
        .isEqualTo(stateWithTrustedSource.getPivotBlockHeader());
    assertThat(loadedState.isSourceTrusted()).isTrue();
  }

  @Test
  public void shouldStoreAndLoadStateWithSourceIsTrustedFalse() {
    final FastSyncState stateWithUntrustedSource = new FastSyncState(pivotBlockHeader, false);
    storage.storeState(stateWithUntrustedSource);

    final FastSyncState loadedState = storage.loadState(new MainnetBlockHeaderFunctions());
    assertThat(loadedState.getPivotBlockHeader())
        .isEqualTo(stateWithUntrustedSource.getPivotBlockHeader());
    assertThat(loadedState.isSourceTrusted()).isFalse();
  }

  @Test
  public void shouldLoadOldFormatFileWithDefaultSourceIsTrustedFalse() throws IOException {
    // Create an old format file (just the header without version byte)
    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    pivotBlockHeader.writeTo(output);
    Files.write(tempDir.resolve("pivotBlockHeader.rlp"), output.encoded().toArrayUnsafe());

    final FastSyncState loadedState = storage.loadState(new MainnetBlockHeaderFunctions());
    assertThat(loadedState.getPivotBlockHeader().get()).isEqualTo(pivotBlockHeader);
    assertThat(loadedState.isSourceTrusted()).isFalse(); // Should default to false
  }

  @Test
  public void shouldHandleBackwardCompatibilityRoundTrip() throws IOException {
    // First, create an old format file
    final BytesValueRLPOutput oldOutput = new BytesValueRLPOutput();
    pivotBlockHeader.writeTo(oldOutput);
    Files.write(tempDir.resolve("pivotBlockHeader.rlp"), oldOutput.encoded().toArrayUnsafe());

    // Load the old format
    FastSyncState loadedOldFormat = storage.loadState(new MainnetBlockHeaderFunctions());
    assertThat(loadedOldFormat.isSourceTrusted()).isFalse();

    // Store it again (should save in new format)
    storage.storeState(loadedOldFormat);

    // Load again and verify it's still correct
    FastSyncState reloadedState = storage.loadState(new MainnetBlockHeaderFunctions());
    assertThat(reloadedState.getPivotBlockHeader().get()).isEqualTo(pivotBlockHeader);
    assertThat(reloadedState.isSourceTrusted()).isFalse();
  }

  @Test
  public void shouldThrowExceptionWhenLoadingUnsupportedFormatVersion() throws IOException {
    // Create a file with an unsupported version
    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    output.startList();
    output.writeByte((byte) 2); // Use version 2 which is not supported
    pivotBlockHeader.writeTo(output);
    output.writeByte((byte) 0); // sourceIsTrusted = false
    output.endList();
    Files.write(tempDir.resolve("pivotBlockHeader.rlp"), output.encoded().toArrayUnsafe());

    // Attempt to load the state should throw IllegalStateException
    assertThatThrownBy(() -> storage.loadState(new MainnetBlockHeaderFunctions()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported fast sync state format version: 2");
  }
}
