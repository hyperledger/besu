/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.development;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;

import org.junit.Test;

public class DevelopmentProtocolScheduleTest {

  @Test
  public void reportedDifficultyForAllBlocksIsAFixedValue() {

    final ProtocolSchedule<Void> schedule =
        DevelopmentProtocolSchedule.create(GenesisConfigFile.DEFAULT.getConfigOptions());

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

    final BlockHeader parentHeader = headerBuilder.number(1).buildHeader();

    assertThat(
            schedule
                .getByBlockNumber(0)
                .getDifficultyCalculator()
                .nextDifficulty(1, parentHeader, null))
        .isEqualTo(DevelopmentDifficultyCalculators.MINIMUM_DIFFICULTY);

    assertThat(
            schedule
                .getByBlockNumber(500)
                .getDifficultyCalculator()
                .nextDifficulty(1, parentHeader, null))
        .isEqualTo(DevelopmentDifficultyCalculators.MINIMUM_DIFFICULTY);

    assertThat(
            schedule
                .getByBlockNumber(500_000)
                .getDifficultyCalculator()
                .nextDifficulty(1, parentHeader, null))
        .isEqualTo(DevelopmentDifficultyCalculators.MINIMUM_DIFFICULTY);
  }
}
