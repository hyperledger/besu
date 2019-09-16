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
package org.hyperledger.besu.consensus.clique;

import static org.assertj.core.api.Java6Assertions.assertThat;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

import org.junit.Test;

public class CliqueProtocolScheduleTest {

  private static final KeyPair NODE_KEYS = KeyPair.generate();

  @Test
  public void protocolSpecsAreCreatedAtBlockDefinedInJson() {
    final String jsonInput =
        "{\"config\": "
            + "{\"chainId\": 4,\n"
            + "\"homesteadBlock\": 1,\n"
            + "\"eip150Block\": 2,\n"
            + "\"eip155Block\": 3,\n"
            + "\"eip158Block\": 3,\n"
            + "\"byzantiumBlock\": 1035301}"
            + "}";

    final GenesisConfigOptions config = GenesisConfigFile.fromConfig(jsonInput).getConfigOptions();
    final ProtocolSchedule<CliqueContext> protocolSchedule =
        CliqueProtocolSchedule.create(config, NODE_KEYS, false);

    final ProtocolSpec<CliqueContext> homesteadSpec = protocolSchedule.getByBlockNumber(1);
    final ProtocolSpec<CliqueContext> tangerineWhistleSpec = protocolSchedule.getByBlockNumber(2);
    final ProtocolSpec<CliqueContext> spuriousDragonSpec = protocolSchedule.getByBlockNumber(3);
    final ProtocolSpec<CliqueContext> byzantiumSpec = protocolSchedule.getByBlockNumber(1035301);

    assertThat(homesteadSpec.equals(tangerineWhistleSpec)).isFalse();
    assertThat(tangerineWhistleSpec.equals(spuriousDragonSpec)).isFalse();
    assertThat(spuriousDragonSpec.equals(byzantiumSpec)).isFalse();
  }

  @Test
  public void parametersAlignWithMainnetWithAdjustments() {
    final ProtocolSpec<CliqueContext> homestead =
        CliqueProtocolSchedule.create(
                GenesisConfigFile.DEFAULT.getConfigOptions(), NODE_KEYS, false)
            .getByBlockNumber(0);

    assertThat(homestead.getName()).isEqualTo("Frontier");
    assertThat(homestead.getBlockReward()).isEqualTo(Wei.ZERO);
    assertThat(homestead.isSkipZeroBlockRewards()).isEqualTo(true);
    assertThat(homestead.getDifficultyCalculator()).isInstanceOf(CliqueDifficultyCalculator.class);
  }
}
