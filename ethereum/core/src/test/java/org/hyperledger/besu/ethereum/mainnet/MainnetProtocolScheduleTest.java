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

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.nio.charset.StandardCharsets;

import com.google.common.io.Resources;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class MainnetProtocolScheduleTest {

  @Test
  public void shouldReturnDefaultProtocolSpecsWhenCustomNumbersAreNotUsed() {
    final ProtocolSchedule sched = ProtocolScheduleFixture.MAINNET;
    Assertions.assertThat(sched.getByBlockNumber(1L).getName()).isEqualTo("Frontier");
    Assertions.assertThat(sched.getByBlockNumber(1_150_000L).getName()).isEqualTo("Homestead");
    Assertions.assertThat(sched.getByBlockNumber(1_920_000L).getName())
        .isEqualTo("DaoRecoveryInit");
    Assertions.assertThat(sched.getByBlockNumber(1_920_001L).getName())
        .isEqualTo("DaoRecoveryTransition");
    Assertions.assertThat(sched.getByBlockNumber(1_920_010L).getName()).isEqualTo("Homestead");
    Assertions.assertThat(sched.getByBlockNumber(2_463_000L).getName())
        .isEqualTo("TangerineWhistle");
    Assertions.assertThat(sched.getByBlockNumber(2_675_000L).getName()).isEqualTo("SpuriousDragon");
    Assertions.assertThat(sched.getByBlockNumber(4_730_000L).getName()).isEqualTo("Byzantium");
    // Constantinople was originally scheduled for 7_080_000, but postponed
    Assertions.assertThat(sched.getByBlockNumber(7_080_000L).getName()).isEqualTo("Byzantium");
    Assertions.assertThat(sched.getByBlockNumber(7_280_000L).getName()).isEqualTo("Petersburg");
    Assertions.assertThat(sched.getByBlockNumber(9_069_000L).getName()).isEqualTo("Istanbul");
    Assertions.assertThat(sched.getByBlockNumber(9_200_000L).getName()).isEqualTo("MuirGlacier");
    Assertions.assertThat(sched.getByBlockNumber(12_244_000L).getName()).isEqualTo("Berlin");
    Assertions.assertThat(sched.getByBlockNumber(12_965_000L).getName()).isEqualTo("London");
    Assertions.assertThat(sched.getByBlockNumber(13_773_000L).getName()).isEqualTo("ArrowGlacier");
    Assertions.assertThat(sched.getByBlockNumber(15_050_000L).getName()).isEqualTo("GrayGlacier");
    Assertions.assertThat(sched.getByBlockNumber(Long.MAX_VALUE).getName())
        .isEqualTo("GrayGlacier");
  }

  @Test
  public void shouldOnlyUseFrontierWhenEmptyJsonConfigIsUsed() {
    final ProtocolSchedule sched =
        MainnetProtocolSchedule.fromConfig(
            GenesisConfigFile.fromConfig("{}").getConfigOptions(), EvmConfiguration.DEFAULT);
    Assertions.assertThat(sched.getByBlockNumber(1L).getName()).isEqualTo("Frontier");
    Assertions.assertThat(sched.getByBlockNumber(Long.MAX_VALUE).getName()).isEqualTo("Frontier");
  }

  @Test
  public void createFromConfigWithSettings() {
    final String json =
        "{\"config\": {\"homesteadBlock\": 2, \"daoForkBlock\": 3, \"eip150Block\": 14, \"eip158Block\": 15, \"byzantiumBlock\": 16, \"constantinopleBlock\": 18, \"petersburgBlock\": 19, \"chainId\":1234}}";
    final ProtocolSchedule sched =
        MainnetProtocolSchedule.fromConfig(
            GenesisConfigFile.fromConfig(json).getConfigOptions(), EvmConfiguration.DEFAULT);
    Assertions.assertThat(sched.getByBlockNumber(1).getName()).isEqualTo("Frontier");
    Assertions.assertThat(sched.getByBlockNumber(2).getName()).isEqualTo("Homestead");
    Assertions.assertThat(sched.getByBlockNumber(3).getName()).isEqualTo("DaoRecoveryInit");
    Assertions.assertThat(sched.getByBlockNumber(4).getName()).isEqualTo("DaoRecoveryTransition");
    Assertions.assertThat(sched.getByBlockNumber(13).getName()).isEqualTo("Homestead");
    Assertions.assertThat(sched.getByBlockNumber(14).getName()).isEqualTo("TangerineWhistle");
    Assertions.assertThat(sched.getByBlockNumber(15).getName()).isEqualTo("SpuriousDragon");
    Assertions.assertThat(sched.getByBlockNumber(16).getName()).isEqualTo("Byzantium");
    Assertions.assertThat(sched.getByBlockNumber(18).getName()).isEqualTo("Constantinople");
    Assertions.assertThat(sched.getByBlockNumber(19).getName()).isEqualTo("Petersburg");
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
                    GenesisConfigFile.fromConfig(json).getConfigOptions(),
                    EvmConfiguration.DEFAULT));
  }

  @Test
  public void shouldCreateRopstenConfig() throws Exception {
    final ProtocolSchedule sched =
        MainnetProtocolSchedule.fromConfig(
            GenesisConfigFile.fromConfig(
                    Resources.toString(
                        this.getClass().getResource("/ropsten.json"), StandardCharsets.UTF_8))
                .getConfigOptions(),
            EvmConfiguration.DEFAULT);
    Assertions.assertThat(sched.getByBlockNumber(0L).getName()).isEqualTo("TangerineWhistle");
    Assertions.assertThat(sched.getByBlockNumber(1L).getName()).isEqualTo("TangerineWhistle");
    Assertions.assertThat(sched.getByBlockNumber(10L).getName()).isEqualTo("SpuriousDragon");
    Assertions.assertThat(sched.getByBlockNumber(1_700_000L).getName()).isEqualTo("Byzantium");
    Assertions.assertThat(sched.getByBlockNumber(4_230_000L).getName()).isEqualTo("Constantinople");
    Assertions.assertThat(sched.getByBlockNumber(4_939_394L).getName()).isEqualTo("Petersburg");
    Assertions.assertThat(sched.getByBlockNumber(6_485_846L).getName()).isEqualTo("Istanbul");
    Assertions.assertThat(sched.getByBlockNumber(7_117_117L).getName()).isEqualTo("MuirGlacier");
    Assertions.assertThat(sched.getByBlockNumber(9_812_189L).getName()).isEqualTo("Berlin");
    Assertions.assertThat(sched.getByBlockNumber(10_499_401L).getName()).isEqualTo("London");
    Assertions.assertThat(sched.getByBlockNumber(Long.MAX_VALUE).getName()).isEqualTo("London");
  }

  @Test
  public void shouldCreateGoerliConfig() throws Exception {
    final ProtocolSchedule sched =
        MainnetProtocolSchedule.fromConfig(
            GenesisConfigFile.fromConfig(
                    Resources.toString(
                        this.getClass().getResource("/goerli.json"), StandardCharsets.UTF_8))
                .getConfigOptions(),
            EvmConfiguration.DEFAULT);
    Assertions.assertThat(sched.getByBlockNumber(0L).getName()).isEqualTo("Petersburg");
    Assertions.assertThat(sched.getByBlockNumber(1_561_651L).getName()).isEqualTo("Istanbul");
    Assertions.assertThat(sched.getByBlockNumber(4_460_644L).getName()).isEqualTo("Berlin");
    Assertions.assertThat(sched.getByBlockNumber(5_062_605L).getName()).isEqualTo("London");
    Assertions.assertThat(sched.getByBlockNumber(Long.MAX_VALUE).getName()).isEqualTo("London");
  }

  @Test
  public void shouldCreateRinkebyConfig() throws Exception {
    final ProtocolSchedule sched =
        MainnetProtocolSchedule.fromConfig(
            GenesisConfigFile.fromConfig(
                    Resources.toString(
                        this.getClass().getResource("/rinkeby.json"), StandardCharsets.UTF_8))
                .getConfigOptions(),
            EvmConfiguration.DEFAULT);
    Assertions.assertThat(sched.getByBlockNumber(0L).getName()).isEqualTo("Frontier");
    Assertions.assertThat(sched.getByBlockNumber(1L).getName()).isEqualTo("Homestead");
    Assertions.assertThat(sched.getByBlockNumber(2L).getName()).isEqualTo("TangerineWhistle");
    Assertions.assertThat(sched.getByBlockNumber(3L).getName()).isEqualTo("SpuriousDragon");
    Assertions.assertThat(sched.getByBlockNumber(1_035_301L).getName()).isEqualTo("Byzantium");
    Assertions.assertThat(sched.getByBlockNumber(3_660_663L).getName()).isEqualTo("Constantinople");
    Assertions.assertThat(sched.getByBlockNumber(4_321_234L).getName()).isEqualTo("Petersburg");
    Assertions.assertThat(sched.getByBlockNumber(5_435_345L).getName()).isEqualTo("Istanbul");
    Assertions.assertThat(sched.getByBlockNumber(8_290_928L).getName()).isEqualTo("Berlin");
    Assertions.assertThat(sched.getByBlockNumber(8_897_988L).getName()).isEqualTo("London");
    Assertions.assertThat(sched.getByBlockNumber(Long.MAX_VALUE).getName()).isEqualTo("London");
  }
}
