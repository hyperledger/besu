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
package org.hyperledger.besu.ethereum.difficulty.fixed;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import org.junit.Test;

public class FixedProtocolScheduleTest {

  @Test
  public void reportedDifficultyForAllBlocksIsAFixedValue() {

    final ProtocolSchedule schedule =
        FixedDifficultyProtocolSchedule.create(
            GenesisConfigFile.development().getConfigOptions(), EvmConfiguration.DEFAULT);

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    final BlockHeader parentHeader = headerBuilder.number(1).buildHeader();

    assertThat(
            schedule
                .getByBlockNumber(0)
                .getDifficultyCalculator()
                .nextDifficulty(1, parentHeader, null))
        .isEqualTo(FixedDifficultyCalculators.DEFAULT_DIFFICULTY);

    assertThat(
            schedule
                .getByBlockNumber(500)
                .getDifficultyCalculator()
                .nextDifficulty(1, parentHeader, null))
        .isEqualTo(FixedDifficultyCalculators.DEFAULT_DIFFICULTY);

    assertThat(
            schedule
                .getByBlockNumber(500_000)
                .getDifficultyCalculator()
                .nextDifficulty(1, parentHeader, null))
        .isEqualTo(FixedDifficultyCalculators.DEFAULT_DIFFICULTY);
  }

  @Test
  public void reportedDifficultyForAllBlocksIsAFixedValueKeccak() {

    final ProtocolSchedule schedule =
        FixedDifficultyProtocolSchedule.create(
            GenesisConfigFile.ecip1049dev().getConfigOptions(), EvmConfiguration.DEFAULT);

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    final BlockHeader parentHeader = headerBuilder.number(1).buildHeader();

    assertThat(
            schedule
                .getByBlockNumber(0)
                .getDifficultyCalculator()
                .nextDifficulty(1, parentHeader, null))
        .isEqualTo(10000);

    assertThat(
            schedule
                .getByBlockNumber(500)
                .getDifficultyCalculator()
                .nextDifficulty(1, parentHeader, null))
        .isEqualTo(10000);

    assertThat(
            schedule
                .getByBlockNumber(500_000)
                .getDifficultyCalculator()
                .nextDifficulty(1, parentHeader, null))
        .isEqualTo(10000);
  }
}
