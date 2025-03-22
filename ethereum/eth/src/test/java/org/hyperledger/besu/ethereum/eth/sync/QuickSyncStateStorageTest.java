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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class QuickSyncStateStorageTest {
  @TempDir private Path tempDir;

  private QuickSyncStateStorage storage;
  private final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().buildHeader();
  private final QuickSyncState syncStateWithHeader = new QuickSyncState(pivotBlockHeader);

  @BeforeEach
  public void setUp() throws Exception {
    storage = new QuickSyncStateStorage(tempDir);
  }

  @Test
  public void shouldNotConsiderFastSyncInProgressWhenFileDoesNotExist() {
    assertThat(storage.isFastSyncInProgress()).isFalse();
  }

  @Test
  public void shouldConsiderFastSyncInProgressWhenFileExists() {
    storage.storeState(syncStateWithHeader);
    assertThat(storage.isFastSyncInProgress()).isTrue();

    final QuickSyncStateStorage newStorage = new QuickSyncStateStorage(tempDir);
    assertThat(newStorage.isFastSyncInProgress()).isTrue();
  }

  @Test
  public void shouldRoundTripHeader() {
    storage.storeState(syncStateWithHeader);
    assertThat(storage.loadState(new MainnetBlockHeaderFunctions())).isEqualTo(syncStateWithHeader);

    final QuickSyncStateStorage newStorage = new QuickSyncStateStorage(tempDir);
    assertThat(newStorage.loadState(new MainnetBlockHeaderFunctions()))
        .isEqualTo(syncStateWithHeader);
  }

  @Test
  public void shouldReturnEmptyWhenLoadingHeaderAndFileDoesNotExist() {
    assertThat(storage.loadState(new MainnetBlockHeaderFunctions()))
        .isEqualTo(QuickSyncState.EMPTY_SYNC_STATE);
  }

  @Test
  public void shouldRemoveStateFileWhenStoringFastSyncWithoutBlockHeader() {
    storage.storeState(syncStateWithHeader);
    assertThat(storage.loadState(new MainnetBlockHeaderFunctions())).isEqualTo(syncStateWithHeader);

    storage.storeState(QuickSyncState.EMPTY_SYNC_STATE);
    assertThat(storage.loadState(new MainnetBlockHeaderFunctions()))
        .isEqualTo(QuickSyncState.EMPTY_SYNC_STATE);
  }
}
