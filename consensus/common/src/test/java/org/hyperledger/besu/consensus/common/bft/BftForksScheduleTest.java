/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.consensus.common.bft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.JsonBftConfigOptions;

import java.util.List;

import org.junit.Test;

public class BftForksScheduleTest {

  @Test
  public void retrievesGenesisFork() {
    final BftForkSpec<BftConfigOptions> genesisForkSpec =
        new BftForkSpec<>(0, JsonBftConfigOptions.DEFAULT);

    final BftForksSchedule<BftConfigOptions> schedule =
        new BftForksSchedule<>(genesisForkSpec, List.of());
    assertThat(schedule.getFork(0)).isEqualTo(genesisForkSpec);
    assertThat(schedule.getFork(1)).isEqualTo(genesisForkSpec);
  }

  @Test
  public void retrievesLatestFork() {
    final BftForkSpec<BftConfigOptions> genesisForkSpec =
        new BftForkSpec<>(0, JsonBftConfigOptions.DEFAULT);
    final BftForkSpec<BftConfigOptions> forkSpec1 = createForkSpec(1, 10);
    final BftForkSpec<BftConfigOptions> forkSpec2 = createForkSpec(2, 20);

    final BftForksSchedule<BftConfigOptions> schedule =
        new BftForksSchedule<>(genesisForkSpec, List.of(forkSpec1, forkSpec2));

    assertThat(schedule.getFork(0)).isEqualTo(genesisForkSpec);
    assertThat(schedule.getFork(1)).isEqualTo(forkSpec1);
    assertThat(schedule.getFork(2)).isEqualTo(forkSpec2);
    assertThat(schedule.getFork(3)).isEqualTo(forkSpec2);
  }

  @Test
  public void throwsErrorIfHasForkForGenesisBlock() {
    final BftForkSpec<BftConfigOptions> genesisForkSpec =
        new BftForkSpec<>(0, JsonBftConfigOptions.DEFAULT);
    final BftForkSpec<BftConfigOptions> fork = createForkSpec(0, 10);

    assertThatThrownBy(() -> new BftForksSchedule<>(genesisForkSpec, List.of(fork)))
        .hasMessage("Transition cannot be created for genesis block");
  }

  @Test
  public void throwsErrorIfHasForksWithDuplicateBlock() {
    final BftForkSpec<BftConfigOptions> genesisForkSpec =
        new BftForkSpec<>(0, JsonBftConfigOptions.DEFAULT);
    final BftForkSpec<BftConfigOptions> fork1 = createForkSpec(1, 10);
    final BftForkSpec<BftConfigOptions> fork2 = createForkSpec(1, 10);
    final BftForkSpec<BftConfigOptions> fork3 = createForkSpec(3, 10);

    assertThatThrownBy(() -> new BftForksSchedule<>(genesisForkSpec, List.of(fork1, fork2, fork3)))
        .hasMessage("Duplicate transitions cannot be created for the same block");
  }

  private BftForkSpec<BftConfigOptions> createForkSpec(
      final long block, final int blockPeriodSeconds) {
    final MutableBftConfigOptions bftConfigOptions =
        new MutableBftConfigOptions(JsonBftConfigOptions.DEFAULT);
    bftConfigOptions.setBlockPeriodSeconds(blockPeriodSeconds);
    return new BftForkSpec<>(block, bftConfigOptions);
  }
}
