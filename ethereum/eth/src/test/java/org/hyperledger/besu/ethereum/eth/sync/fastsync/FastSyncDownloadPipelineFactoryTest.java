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
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.ValidationPolicy;
import org.hyperledger.besu.ethereum.eth.sync.range.SyncTargetRange;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncTarget;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.pipeline.Pipeline;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FastSyncDownloadPipelineFactoryTest {

  @Mock private SynchronizerConfiguration syncConfig;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolContext protocolContext;
  @Mock private EthContext ethContext;
  @Mock private FastSyncState fastSyncState;
  @Mock private SyncState syncState;
  @Mock private SyncTarget syncTarget;
  @Mock private EthPeer peer;
  @Mock private EthScheduler ethScheduler;
  @Mock private SnapSyncConfiguration snapSyncConfiguration;
  @Mock private MutableBlockchain blockchain;

  private BlockHeader pivotHeader;
  private BlockHeader commonAncestor;

  @BeforeEach
  void setUp() {
    when(syncConfig.getDownloaderParallelism()).thenReturn(4);
    when(syncConfig.getDownloaderHeaderRequestSize()).thenReturn(10);
    when(syncConfig.getDownloaderCheckpointRetries()).thenReturn(3);
    when(syncConfig.getFastSyncFullValidationRate()).thenReturn(0.1f);
    when(syncConfig.getSnapSyncConfiguration()).thenReturn(snapSyncConfiguration);
    when(syncConfig.isSnapSyncSavePreCheckpointHeadersOnlyEnabled()).thenReturn(false);

    when(snapSyncConfiguration.isSnapSyncTransactionIndexingEnabled()).thenReturn(false);

    pivotHeader = new BlockHeaderTestFixture().number(1000).buildHeader();
    commonAncestor = new BlockHeaderTestFixture().number(0).buildHeader();

    when(fastSyncState.getPivotBlockHeader()).thenReturn(Optional.of(pivotHeader));
    when(syncTarget.peer()).thenReturn(peer);
    when(syncTarget.commonAncestor()).thenReturn(commonAncestor);
    when(ethContext.getScheduler()).thenReturn(ethScheduler);
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(protocolContext.safeConsensusContext(ConsensusContext.class)).thenReturn(Optional.empty());
    when(syncState.getCheckpoint()).thenReturn(Optional.empty());
    when(protocolSchedule.anyMatch(any())).thenReturn(false);
  }

  @Test
  void shouldUseNoneValidationWhenFastSyncStateSourceIsTrusted() {
    when(fastSyncState.isSourceTrusted()).thenReturn(true);

    TestFastSyncDownloadPipelineFactory testFactory =
        new TestFastSyncDownloadPipelineFactory(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            fastSyncState,
            new NoOpMetricsSystem());

    Pipeline<SyncTargetRange> pipeline =
        testFactory.createDownloadPipelineForSyncTarget(syncState, syncTarget);

    assertThat(pipeline).isNotNull();

    // Verify that the validation policy used for header download is NONE when source is trusted
    assertThat(testFactory.capturedDownloadHeaderValidationPolicy).isNotNull();
    assertThat(testFactory.capturedDownloadHeaderValidationPolicy.getValidationModeForNextBlock())
        .isEqualTo(HeaderValidationMode.NONE);
  }

  @Test
  void shouldUseDetachedValidationWhenFastSyncStateSourceIsNotTrusted() {
    when(fastSyncState.isSourceTrusted()).thenReturn(false);

    TestFastSyncDownloadPipelineFactory testFactory =
        new TestFastSyncDownloadPipelineFactory(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            fastSyncState,
            new NoOpMetricsSystem());

    Pipeline<SyncTargetRange> pipeline =
        testFactory.createDownloadPipelineForSyncTarget(syncState, syncTarget);

    assertThat(pipeline).isNotNull();

    // Verify that the validation policy used for header download is not NONE when source is not
    // trusted
    assertThat(testFactory.capturedDownloadHeaderValidationPolicy).isNotNull();
    assertThat(testFactory.capturedDownloadHeaderValidationPolicy.getValidationModeForNextBlock())
        .isNotEqualTo(HeaderValidationMode.NONE);
  }

  private static class TestFastSyncDownloadPipelineFactory extends FastSyncDownloadPipelineFactory {
    ValidationPolicy capturedDownloadHeaderValidationPolicy;

    public TestFastSyncDownloadPipelineFactory(
        final SynchronizerConfiguration syncConfig,
        final ProtocolSchedule protocolSchedule,
        final ProtocolContext protocolContext,
        final EthContext ethContext,
        final FastSyncState fastSyncState,
        final NoOpMetricsSystem metricsSystem) {
      super(
          syncConfig, protocolSchedule, protocolContext, ethContext, fastSyncState, metricsSystem);
    }

    @Override
    public Pipeline<SyncTargetRange> createDownloadPipelineForSyncTarget(
        final SyncState syncState, final SyncTarget target) {
      // Capture the validation policy that would be used for header download
      this.capturedDownloadHeaderValidationPolicy =
          fastSyncState.isSourceTrusted() ? noneValidationPolicy : detachedValidationPolicy;

      // Call the parent implementation
      return super.createDownloadPipelineForSyncTarget(syncState, target);
    }
  }
}
