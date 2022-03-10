/*
 * Copyright Hyperledger Besu contributors.
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
package org.hyperledger.besu.consensus.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.JsonBftConfigOptions;
import org.hyperledger.besu.consensus.common.bft.MutableBftConfigOptions;
import org.hyperledger.besu.datatypes.Address;

import java.util.List;
import java.util.Optional;

import org.junit.Test;

public class ForksScheduleTest {

  @Test
  public void retrievesGenesisFork() {
    final ForkSpec<BftConfigOptions> genesisForkSpec =
        new ForkSpec<>(0, JsonBftConfigOptions.DEFAULT);
    final ForkSpec<BftConfigOptions> forkSpec1 = createForkSpec(10, 10);

    final ForksSchedule<BftConfigOptions> schedule =
        new ForksSchedule<>(List.of(forkSpec1, genesisForkSpec));
    assertThat(schedule.getFork(0)).isEqualTo(genesisForkSpec);
    assertThat(schedule.getFork(1)).isEqualTo(genesisForkSpec);
  }

  @Test
  public void retrievesLatestFork() {
    final ForkSpec<BftConfigOptions> genesisForkSpec =
        new ForkSpec<>(0, JsonBftConfigOptions.DEFAULT);
    final ForkSpec<BftConfigOptions> forkSpec1 = createForkSpec(1, 10);
    final ForkSpec<BftConfigOptions> forkSpec2 = createForkSpec(2, 20);
    final Optional<Address> miningBeneficiary3 =
        Optional.of(Address.fromHexString("0xdee0519f7c7cb0f9843fa1e93b99255c89507a9c"));
    final ForkSpec<BftConfigOptions> forkSpec3 =
        createForkSpecWithMiningBeneficiary(3, miningBeneficiary3);
    final ForkSpec<BftConfigOptions> forkSpec4 =
        createForkSpecWithMiningBeneficiary(4, Optional.empty());

    final ForksSchedule<BftConfigOptions> schedule =
        new ForksSchedule<>(List.of(genesisForkSpec, forkSpec1, forkSpec2, forkSpec3, forkSpec4));

    assertThat(schedule.getFork(0)).isEqualTo(genesisForkSpec);
    assertThat(schedule.getFork(1)).isEqualTo(forkSpec1);
    assertThat(schedule.getFork(2)).isEqualTo(forkSpec2);
    assertThat(schedule.getFork(3)).isEqualTo(forkSpec3);
    assertThat(schedule.getFork(3).getValue().getMiningBeneficiary()).isEqualTo(miningBeneficiary3);
    assertThat(schedule.getFork(4)).isEqualTo(forkSpec4);
    assertThat(schedule.getFork(4).getValue().getMiningBeneficiary()).isEmpty();
  }

  private ForkSpec<BftConfigOptions> createForkSpecWithMiningBeneficiary(
      final long block, final Optional<Address> beneficiary) {
    final MutableBftConfigOptions bftConfigOptions =
        new MutableBftConfigOptions(JsonBftConfigOptions.DEFAULT);
    bftConfigOptions.setMiningBeneficiary(beneficiary);
    return new ForkSpec<>(block, bftConfigOptions);
  }

  private ForkSpec<BftConfigOptions> createForkSpec(
      final long block, final int blockPeriodSeconds) {
    final MutableBftConfigOptions bftConfigOptions = createBftConfigOptions(blockPeriodSeconds);
    return new ForkSpec<>(block, bftConfigOptions);
  }

  private MutableBftConfigOptions createBftConfigOptions(final int blockPeriodSeconds) {
    final MutableBftConfigOptions bftConfigOptions =
        new MutableBftConfigOptions(JsonBftConfigOptions.DEFAULT);
    bftConfigOptions.setBlockPeriodSeconds(blockPeriodSeconds);
    return bftConfigOptions;
  }
}
