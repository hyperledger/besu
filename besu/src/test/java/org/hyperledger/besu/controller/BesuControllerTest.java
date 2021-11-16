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

import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BesuControllerTest {

  @Spy private GenesisConfigFile genesisConfigFile = GenesisConfigFile.mainnet();
  @Mock private GenesisConfigOptions genesisConfigOptions;
  @Mock private QbftConfigOptions qbftConfigOptions;

  @Test
  public void missingQbftStartBlock() {
    mockGenesisConfigForMigration("ibft2", OptionalLong.empty());
    assertThatThrownBy(() -> new BesuController.Builder().fromGenesisConfig(genesisConfigFile))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Missing QBFT startBlock config in genesis file");
  }

  @Test
  public void invalidQbftStartBlock() {
    mockGenesisConfigForMigration("ibft2", OptionalLong.of(-1L));
    assertThatThrownBy(() -> new BesuController.Builder().fromGenesisConfig(genesisConfigFile))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Invalid QBFT startBlock config in genesis file");
  }

  @Test
  public void invalidConsensusCombination() {
    when(genesisConfigFile.getConfigOptions(any())).thenReturn(genesisConfigOptions);
    when(genesisConfigOptions.isConsensusMigration()).thenReturn(true);
    // explicitly not setting isIbftLegacy() or isIbft2() for genesisConfigOptions

    assertThatThrownBy(() -> new BesuController.Builder().fromGenesisConfig(genesisConfigFile))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Invalid genesis migration config. Migration is supported from IBFT (legacy) or IBFT2 to QBFT)");
  }

  @Test
  public void createConsensusScheduleBesuControllerBuilderWhenMigratingFromIbft2ToQbft() {
    final long qbftStartBlock = 10L;
    mockGenesisConfigForMigration("ibft2", OptionalLong.of(qbftStartBlock));

    final BesuControllerBuilder besuControllerBuilder =
        new BesuController.Builder().fromGenesisConfig(genesisConfigFile);

    assertThat(besuControllerBuilder).isInstanceOf(ConsensusScheduleBesuControllerBuilder.class);

    final Map<Long, BesuControllerBuilder> besuControllerBuilderSchedule =
        ((ConsensusScheduleBesuControllerBuilder) besuControllerBuilder)
            .getBesuControllerBuilderSchedule();

    assertThat(besuControllerBuilderSchedule).containsKeys(0L, qbftStartBlock);
    assertThat(besuControllerBuilderSchedule.get(0L)).isInstanceOf(IbftBesuControllerBuilder.class);
    assertThat(besuControllerBuilderSchedule.get(qbftStartBlock))
        .isInstanceOf(QbftBesuControllerBuilder.class);
  }

  @Test
  public void createConsensusScheduleBesuControllerBuilderWhenMigratingFromIbftLegacyToQbft() {
    final long qbftStartBlock = 10L;
    mockGenesisConfigForMigration("ibftLegacy", OptionalLong.of(qbftStartBlock));

    final BesuControllerBuilder besuControllerBuilder =
        new BesuController.Builder().fromGenesisConfig(genesisConfigFile);

    assertThat(besuControllerBuilder).isInstanceOf(ConsensusScheduleBesuControllerBuilder.class);

    final Map<Long, BesuControllerBuilder> besuControllerBuilderSchedule =
        ((ConsensusScheduleBesuControllerBuilder) besuControllerBuilder)
            .getBesuControllerBuilderSchedule();

    assertThat(besuControllerBuilderSchedule).containsKeys(0L, qbftStartBlock);
    assertThat(besuControllerBuilderSchedule.get(0L))
        .isInstanceOf(IbftLegacyBesuControllerBuilder.class);
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
      case "ibftlegacy":
        {
          when(genesisConfigOptions.isIbftLegacy()).thenReturn(true);
          break;
        }
      default:
        fail("Invalid consensus algorithm");
    }

    when(genesisConfigOptions.getQbftConfigOptions()).thenReturn(qbftConfigOptions);
    when(qbftConfigOptions.getStartBlock()).thenReturn(startBlock);
  }
}
