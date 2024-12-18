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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class MainnetProtocolScheduleTest {

  @Test
  public void shouldReturnDefaultProtocolSpecsWhenCustomNumbersAreNotUsed() {
    final ProtocolSchedule sched = ProtocolScheduleFixture.MAINNET;
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(1L)).getName()).isEqualTo("Frontier");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(1_150_000L)).getName())
        .isEqualTo("Homestead");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(1_920_000L)).getName())
        .isEqualTo("DaoRecoveryInit");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(1_920_001L)).getName())
        .isEqualTo("DaoRecoveryTransition");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(1_920_010L)).getName())
        .isEqualTo("Homestead");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(2_463_000L)).getName())
        .isEqualTo("TangerineWhistle");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(2_675_000L)).getName())
        .isEqualTo("SpuriousDragon");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(4_730_000L)).getName())
        .isEqualTo("Byzantium");
    // Constantinople was originally scheduled for 7_080_000, but postponed
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(7_080_000L)).getName())
        .isEqualTo("Byzantium");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(7_280_000L)).getName())
        .isEqualTo("Petersburg");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(9_069_000L)).getName())
        .isEqualTo("Istanbul");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(9_200_000L)).getName())
        .isEqualTo("MuirGlacier");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(12_244_000L)).getName())
        .isEqualTo("Berlin");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(12_965_000L)).getName())
        .isEqualTo("London");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(13_773_000L)).getName())
        .isEqualTo("ArrowGlacier");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(15_050_000L)).getName())
        .isEqualTo("GrayGlacier");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(Long.MAX_VALUE)).getName())
        .isEqualTo("GrayGlacier");
  }

  @Test
  public void shouldOnlyUseFrontierWhenEmptyJsonConfigIsUsed() {
    final ProtocolSchedule sched =
        MainnetProtocolSchedule.fromConfig(
            GenesisConfig.fromConfig("{}").getConfigOptions(),
            EvmConfiguration.DEFAULT,
            MiningConfiguration.MINING_DISABLED,
            new BadBlockManager(),
            false,
            new NoOpMetricsSystem());
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(1L)).getName()).isEqualTo("Frontier");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(Long.MAX_VALUE)).getName())
        .isEqualTo("Frontier");
  }

  @Test
  public void createFromConfigWithSettings() {
    final String json =
        "{\"config\": {\"homesteadBlock\": 2, \"daoForkBlock\": 3, \"eip150Block\": 14, \"eip158Block\": 15, \"byzantiumBlock\": 16, \"constantinopleBlock\": 18, \"petersburgBlock\": 19, \"chainId\":1234}}";
    final ProtocolSchedule sched =
        MainnetProtocolSchedule.fromConfig(
            GenesisConfig.fromConfig(json).getConfigOptions(),
            EvmConfiguration.DEFAULT,
            MiningConfiguration.MINING_DISABLED,
            new BadBlockManager(),
            false,
            new NoOpMetricsSystem());
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(1)).getName()).isEqualTo("Frontier");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(2)).getName()).isEqualTo("Homestead");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(3)).getName())
        .isEqualTo("DaoRecoveryInit");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(4)).getName())
        .isEqualTo("DaoRecoveryTransition");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(13)).getName()).isEqualTo("Homestead");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(14)).getName())
        .isEqualTo("TangerineWhistle");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(15)).getName())
        .isEqualTo("SpuriousDragon");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(16)).getName()).isEqualTo("Byzantium");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(18)).getName())
        .isEqualTo("Constantinople");
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(19)).getName())
        .isEqualTo("Petersburg");
  }

  @Test
  public void outOfOrderConstantinoplesFail() {
    final String json =
        "{\"config\": {\"homesteadBlock\": 2, \"daoForkBlock\": 3, \"eip150Block\": 14, \"eip158Block\": 15, \"byzantiumBlock\": 16, \"constantinopleBlock\": 18, \"petersburgBlock\": 17, \"chainId\":1234}}";
    Assertions.assertThatExceptionOfType(RuntimeException.class)
        .describedAs(
            "Genesis Config Error: 'Petersburg' is scheduled for block 17 but it must be on or after block 18.")
        .isThrownBy(
            () ->
                MainnetProtocolSchedule.fromConfig(
                    GenesisConfig.fromConfig(json).getConfigOptions(),
                    EvmConfiguration.DEFAULT,
                    MiningConfiguration.MINING_DISABLED,
                    new BadBlockManager(),
                    false,
                    new NoOpMetricsSystem()));
  }

  private BlockHeader blockHeader(final long number) {
    return new BlockHeaderTestFixture().number(number).buildHeader();
  }
}
