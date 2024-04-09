/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

import com.google.common.io.Resources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BesuControllerTest {

  @Spy private GenesisConfigFile genesisConfigFile = GenesisConfigFile.mainnet();
  @Mock private GenesisConfigOptions genesisConfigOptions;
  @Mock private QbftConfigOptions qbftConfigOptions;

  @Test
  public void missingQbftStartBlock() {
    mockGenesisConfigForMigration("ibft2", OptionalLong.empty());
    assertThatThrownBy(
            () -> new BesuController.Builder().fromGenesisConfig(genesisConfigFile, SyncMode.FULL))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Missing QBFT startBlock config in genesis file");
  }

  @Test
  public void invalidQbftStartBlock() {
    mockGenesisConfigForMigration("ibft2", OptionalLong.of(-1L));
    assertThatThrownBy(
            () -> new BesuController.Builder().fromGenesisConfig(genesisConfigFile, SyncMode.FULL))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Invalid QBFT startBlock config in genesis file");
  }

  @Test
  public void invalidConsensusCombination() {
    when(genesisConfigFile.getConfigOptions(any())).thenReturn(genesisConfigOptions);
    when(genesisConfigOptions.isConsensusMigration()).thenReturn(true);
    // explicitly not setting isIbft2() for genesisConfigOptions

    assertThatThrownBy(
            () -> new BesuController.Builder().fromGenesisConfig(genesisConfigFile, SyncMode.FULL))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Invalid genesis migration config. Migration is supported from IBFT (legacy) or IBFT2 to QBFT)");
  }

  @Test
  public void createConsensusScheduleBesuControllerBuilderWhenMigratingFromIbft2ToQbft() {
    final long qbftStartBlock = 10L;
    mockGenesisConfigForMigration("ibft2", OptionalLong.of(qbftStartBlock));

    final BesuControllerBuilder besuControllerBuilder =
        new BesuController.Builder().fromGenesisConfig(genesisConfigFile, SyncMode.FULL);

    assertThat(besuControllerBuilder).isInstanceOf(ConsensusScheduleBesuControllerBuilder.class);

    final Map<Long, BesuControllerBuilder> besuControllerBuilderSchedule =
        ((ConsensusScheduleBesuControllerBuilder) besuControllerBuilder)
            .getBesuControllerBuilderSchedule();

    assertThat(besuControllerBuilderSchedule).containsKeys(0L, qbftStartBlock);
    assertThat(besuControllerBuilderSchedule.get(0L)).isInstanceOf(IbftBesuControllerBuilder.class);
    assertThat(besuControllerBuilderSchedule.get(qbftStartBlock))
        .isInstanceOf(QbftBesuControllerBuilder.class);
  }

  private void mockGenesisConfigForMigration(
      final String consensus, final OptionalLong startBlock) {
    when(genesisConfigFile.getConfigOptions(any())).thenReturn(genesisConfigOptions);
    when(genesisConfigOptions.isConsensusMigration()).thenReturn(true);

    switch (consensus.toLowerCase(Locale.ROOT)) {
      case "ibft2":
        {
          when(genesisConfigOptions.isIbft2()).thenReturn(true);
          break;
        }
      default:
        fail("Invalid consensus algorithm");
    }

    when(genesisConfigOptions.getQbftConfigOptions()).thenReturn(qbftConfigOptions);
    when(qbftConfigOptions.getStartBlock()).thenReturn(startBlock);
  }

  @Test
  public void postMergeCheckpointSyncUsesMergeControllerBuilder() throws IOException {
    final GenesisConfigFile postMergeGenesisFile =
        GenesisConfigFile.fromConfig(
            Resources.toString(
                Resources.getResource("valid_post_merge_near_head_checkpoint.json"),
                StandardCharsets.UTF_8));

    final BesuControllerBuilder besuControllerBuilder =
        new BesuController.Builder()
            .fromGenesisConfig(postMergeGenesisFile, Collections.emptyMap(), SyncMode.CHECKPOINT);

    assertThat(besuControllerBuilder).isInstanceOf(MergeBesuControllerBuilder.class);
  }

  @Test
  public void postMergeCheckpointSyncWithTotalDifficultyEqualsTTDUsesTransitionControllerBuilder()
      throws IOException {
    final GenesisConfigFile mergeAtGenesisFile =
        GenesisConfigFile.fromConfig(
            Resources.toString(
                Resources.getResource(
                    "invalid_post_merge_checkpoint_total_difficulty_same_as_TTD.json"),
                StandardCharsets.UTF_8));

    final BesuControllerBuilder besuControllerBuilder =
        new BesuController.Builder()
            .fromGenesisConfig(mergeAtGenesisFile, Collections.emptyMap(), SyncMode.CHECKPOINT);

    assertThat(besuControllerBuilder).isInstanceOf(TransitionBesuControllerBuilder.class);
  }

  @Test
  public void preMergeCheckpointSyncUsesTransitionControllerBuilder() {
    final BesuControllerBuilder besuControllerBuilder =
        new BesuController.Builder()
            .fromGenesisConfig(genesisConfigFile, Collections.emptyMap(), SyncMode.CHECKPOINT);

    assertThat(besuControllerBuilder).isInstanceOf(TransitionBesuControllerBuilder.class);
  }

  @Test
  public void nonCheckpointSyncUsesTransitionControllerBuild() {
    final BesuControllerBuilder besuControllerBuilder =
        new BesuController.Builder()
            .fromGenesisConfig(genesisConfigFile, Collections.emptyMap(), SyncMode.SNAP);

    assertThat(besuControllerBuilder).isInstanceOf(TransitionBesuControllerBuilder.class);
  }
}
