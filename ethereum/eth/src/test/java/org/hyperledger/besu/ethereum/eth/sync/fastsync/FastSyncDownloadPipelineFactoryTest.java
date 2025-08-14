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
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncConfiguration;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

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
  @Mock private SnapSyncConfiguration snapSyncConfiguration;

  @BeforeEach
  void setUp() {
    when(syncConfig.getSnapSyncConfiguration()).thenReturn(snapSyncConfiguration);
    when(snapSyncConfiguration.isSnapSyncTransactionIndexingEnabled()).thenReturn(false);
  }

  @Test
  void shouldUseNoneValidationWhenFastSyncStateSourceIsTrusted() {
    when(fastSyncState.isSourceTrusted()).thenReturn(true);

    var downloadPipelineFactory =
        new FastSyncDownloadPipelineFactory(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            fastSyncState,
            new NoOpMetricsSystem());

    assertThat(downloadPipelineFactory.downloadHeaderValidation).isNotNull();
    assertThat(downloadPipelineFactory.downloadHeaderValidation.getValidationModeForNextBlock())
        .isEqualTo(HeaderValidationMode.NONE);
  }

  @Test
  void shouldUseDetachedValidationWhenFastSyncStateSourceIsNotTrusted() {
    when(fastSyncState.isSourceTrusted()).thenReturn(false);

    var downloadPipelineFactory =
        new FastSyncDownloadPipelineFactory(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            fastSyncState,
            new NoOpMetricsSystem());

    assertThat(downloadPipelineFactory.downloadHeaderValidation).isNotNull();
    assertThat(downloadPipelineFactory.downloadHeaderValidation.getValidationModeForNextBlock())
        .isNotEqualTo(HeaderValidationMode.NONE);
  }
}
