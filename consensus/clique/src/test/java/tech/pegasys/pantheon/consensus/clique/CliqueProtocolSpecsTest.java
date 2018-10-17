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
package tech.pegasys.pantheon.consensus.clique;

import static org.assertj.core.api.Java6Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSpecs;
import tech.pegasys.pantheon.ethereum.mainnet.MutableProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;

import org.junit.Test;

public class CliqueProtocolSpecsTest {

  CliqueProtocolSpecs protocolSpecs =
      new CliqueProtocolSpecs(
          15, 30_000, 5, AddressHelpers.ofValue(5), new MutableProtocolSchedule<>());

  @Test
  public void homsteadParametersAlignWithMainnetWithAdjustments() {
    final ProtocolSpec<CliqueContext> homestead = protocolSpecs.homestead();

    assertThat(homestead.getName()).isEqualTo("Homestead");
    assertThat(homestead.getBlockReward()).isEqualTo(Wei.ZERO);
    assertThat(homestead.getDifficultyCalculator()).isInstanceOf(CliqueDifficultyCalculator.class);
  }

  @Test
  public void allSpecsInheritFromMainnetCounterparts() {
    final ProtocolSchedule<Void> mainnetProtocolSchedule = new MutableProtocolSchedule<>();

    assertThat(protocolSpecs.frontier().getName())
        .isEqualTo(MainnetProtocolSpecs.frontier(mainnetProtocolSchedule).getName());
    assertThat(protocolSpecs.homestead().getName())
        .isEqualTo(MainnetProtocolSpecs.homestead(mainnetProtocolSchedule).getName());
    assertThat(protocolSpecs.tangerineWhistle().getName())
        .isEqualTo(MainnetProtocolSpecs.tangerineWhistle(mainnetProtocolSchedule).getName());
    assertThat(protocolSpecs.spuriousDragon().getName())
        .isEqualTo(MainnetProtocolSpecs.spuriousDragon(1, mainnetProtocolSchedule).getName());
    assertThat(protocolSpecs.byzantium().getName())
        .isEqualTo(MainnetProtocolSpecs.byzantium(1, mainnetProtocolSchedule).getName());
  }
}
