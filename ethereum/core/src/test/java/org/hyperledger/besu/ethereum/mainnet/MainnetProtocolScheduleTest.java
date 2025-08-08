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

import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.ARROW_GLACIER;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.BERLIN;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.BYZANTIUM;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.CONSTANTINOPLE;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.DAO_RECOVERY_INIT;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.DAO_RECOVERY_TRANSITION;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.FRONTIER;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.GRAY_GLACIER;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.HOMESTEAD;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.ISTANBUL;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.LONDON;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.MUIR_GLACIER;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.PETERSBURG;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.SPURIOUS_DRAGON;
import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.TANGERINE_WHISTLE;

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
  public void shouldReturnMainnetDefaultProtocolSpecsWhenCustomNumbersAreNotUsed() {
    final ProtocolSchedule sched = ProtocolScheduleFixture.MAINNET;
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(1L)).getHardforkId())
        .isEqualTo(FRONTIER);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(1_150_000L)).getHardforkId())
        .isEqualTo(HOMESTEAD);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(1_920_000L)).getHardforkId())
        .isEqualTo(DAO_RECOVERY_INIT);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(1_920_001L)).getHardforkId())
        .isEqualTo(DAO_RECOVERY_TRANSITION);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(1_920_010L)).getHardforkId())
        .isEqualTo(HOMESTEAD);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(2_463_000L)).getHardforkId())
        .isEqualTo(TANGERINE_WHISTLE);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(2_675_000L)).getHardforkId())
        .isEqualTo(SPURIOUS_DRAGON);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(4_730_000L)).getHardforkId())
        .isEqualTo(BYZANTIUM);
    // Constantinople was originally scheduled for 7_080_000, but postponed
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(7_080_000L)).getHardforkId())
        .isEqualTo(BYZANTIUM);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(7_280_000L)).getHardforkId())
        .isEqualTo(PETERSBURG);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(9_069_000L)).getHardforkId())
        .isEqualTo(ISTANBUL);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(9_200_000L)).getHardforkId())
        .isEqualTo(MUIR_GLACIER);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(12_244_000L)).getHardforkId())
        .isEqualTo(BERLIN);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(12_965_000L)).getHardforkId())
        .isEqualTo(LONDON);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(13_773_000L)).getHardforkId())
        .isEqualTo(ARROW_GLACIER);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(15_050_000L)).getHardforkId())
        .isEqualTo(GRAY_GLACIER);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(Long.MAX_VALUE)).getHardforkId())
        .isEqualTo(GRAY_GLACIER);
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
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(1L)).getHardforkId())
        .isEqualTo(FRONTIER);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(Long.MAX_VALUE)).getHardforkId())
        .isEqualTo(FRONTIER);
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
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(1)).getHardforkId())
        .isEqualTo(FRONTIER);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(2)).getHardforkId())
        .isEqualTo(HOMESTEAD);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(3)).getHardforkId())
        .isEqualTo(DAO_RECOVERY_INIT);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(4)).getHardforkId())
        .isEqualTo(DAO_RECOVERY_TRANSITION);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(13)).getHardforkId())
        .isEqualTo(HOMESTEAD);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(14)).getHardforkId())
        .isEqualTo(TANGERINE_WHISTLE);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(15)).getHardforkId())
        .isEqualTo(SPURIOUS_DRAGON);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(16)).getHardforkId())
        .isEqualTo(BYZANTIUM);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(18)).getHardforkId())
        .isEqualTo(CONSTANTINOPLE);
    Assertions.assertThat(sched.getByBlockHeader(blockHeader(19)).getHardforkId())
        .isEqualTo(PETERSBURG);
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
