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
package org.hyperledger.besu.ethereum.eth.sync.checkpointsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncTarget;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckpointSyncDownloadPipelineFactoryTest {
  @Mock private SynchronizerConfiguration syncConfig;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolContext protocolContext;
  @Mock private EthContext ethContext;
  @Mock private FastSyncState fastSyncState;

  @Test
  void shouldCreateCheckpointSyncDownloadPipelineFactory() {
    var downloadPipelineFactory =
        new CheckpointSyncDownloadPipelineFactory(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            fastSyncState,
            new NoOpMetricsSystem());

    assertThat(downloadPipelineFactory).isNotNull();
  }

  @Test
  void shouldReturnCheckpointHeaderAsCommonAncestorWhenAvailable() {
    final BlockHeader commonAncestor = mock(BlockHeader.class);
    final BlockHeader checkpointHeader = mock(BlockHeader.class);
    final EthPeer peer = mock(EthPeer.class);
    final SyncTarget syncTarget = mock(SyncTarget.class);

    when(commonAncestor.getNumber()).thenReturn(100L);
    when(checkpointHeader.getNumber()).thenReturn(200L);
    when(peer.getCheckpointHeader()).thenReturn(Optional.of(checkpointHeader));
    when(syncTarget.peer()).thenReturn(peer);
    when(syncTarget.commonAncestor()).thenReturn(commonAncestor);

    var downloadPipelineFactory =
        new CheckpointSyncDownloadPipelineFactory(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            fastSyncState,
            new NoOpMetricsSystem());

    final BlockHeader result = downloadPipelineFactory.getCommonAncestor(syncTarget);

    assertThat(result).isEqualTo(checkpointHeader);
  }

  @Test
  void shouldReturnOriginalCommonAncestorWhenCheckpointNotAvailable() {
    final BlockHeader commonAncestor = mock(BlockHeader.class);
    final EthPeer peer = mock(EthPeer.class);
    final SyncTarget syncTarget = mock(SyncTarget.class);

    when(peer.getCheckpointHeader()).thenReturn(Optional.empty());
    when(syncTarget.peer()).thenReturn(peer);
    when(syncTarget.commonAncestor()).thenReturn(commonAncestor);

    var downloadPipelineFactory =
        new CheckpointSyncDownloadPipelineFactory(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            fastSyncState,
            new NoOpMetricsSystem());

    final BlockHeader result = downloadPipelineFactory.getCommonAncestor(syncTarget);

    assertThat(result).isEqualTo(commonAncestor);
  }

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}
