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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate.FastDownloaderFactory;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings("rawtypes")
@RunWith(MockitoJUnitRunner.class)
public class FastDownloaderFactoryTest {

  @Mock private SynchronizerConfiguration syncConfig;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolContext protocolContext;
  @Mock private MetricsSystem metricsSystem;
  @Mock private EthContext ethContext;
  @Mock private WorldStateStorage worldStateStorage;
  @Mock private SyncState syncState;
  @Mock private Clock clock;
  @Mock private Path dataDirectory;

  @SuppressWarnings("unchecked")
  @Test(expected = IllegalStateException.class)
  public void shouldThrowIfSyncModeChangedWhileFastSyncIncomplete() throws NoSuchFieldException {
    initDataDirectory(true);

    when(syncConfig.getSyncMode()).thenReturn(SyncMode.FULL);
    FastDownloaderFactory.create(
        syncConfig,
        dataDirectory,
        protocolSchedule,
        protocolContext,
        metricsSystem,
        ethContext,
        worldStateStorage,
        syncState,
        clock);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  public void shouldNotThrowIfSyncModeChangedWhileFastSyncComplete() throws NoSuchFieldException {
    initDataDirectory(false);

    when(syncConfig.getSyncMode()).thenReturn(SyncMode.FULL);
    final Optional result =
        FastDownloaderFactory.create(
            syncConfig,
            dataDirectory,
            protocolSchedule,
            protocolContext,
            metricsSystem,
            ethContext,
            worldStateStorage,
            syncState,
            clock);
    assertThat(result).isEmpty();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldNotThrowWhenFastSyncModeRequested() throws NoSuchFieldException {
    initDataDirectory(false);

    final MutableBlockchain mutableBlockchain = mock(MutableBlockchain.class);
    when(mutableBlockchain.getChainHeadBlockNumber()).thenReturn(0L);
    when(protocolContext.getBlockchain()).thenReturn(mutableBlockchain);

    when(syncConfig.getSyncMode()).thenReturn(SyncMode.FAST);
    FastDownloaderFactory.create(
        syncConfig,
        dataDirectory,
        protocolSchedule,
        protocolContext,
        metricsSystem,
        ethContext,
        worldStateStorage,
        syncState,
        clock);

    verify(mutableBlockchain).getChainHeadBlockNumber();
  }

  private void initDataDirectory(final boolean isPivotBlockHeaderFileExist) {
    final File pivotBlockHeaderFile = mock(File.class);
    when(pivotBlockHeaderFile.isFile()).thenReturn(isPivotBlockHeaderFileExist);

    final File fastSyncDirFile = mock(File.class);
    when(fastSyncDirFile.isDirectory()).thenReturn(true);

    final Path pivotBlockHeaderPath = mock(Path.class);
    when(pivotBlockHeaderPath.toFile()).thenReturn(pivotBlockHeaderFile);

    final Path fastSyncDir = mock(Path.class);
    when(fastSyncDir.resolve(any(String.class))).thenReturn(pivotBlockHeaderPath);
    when(fastSyncDir.toFile()).thenReturn(fastSyncDirFile);
    when(dataDirectory.resolve(anyString())).thenReturn(fastSyncDir);
  }
}
