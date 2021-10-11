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
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.BftFork;
import org.hyperledger.besu.config.JsonBftConfigOptions;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.consensus.common.bft.BftForksSchedule.BftSpecCreator;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mockito.Mockito;

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
  @SuppressWarnings("unchecked")
  public void throwsErrorIfHasForkForGenesisBlock() {
    final BftConfigOptions genesisConfigOptions = JsonBftConfigOptions.DEFAULT;
    final BftFork fork = createFork(0, 10);
    final BftSpecCreator<BftConfigOptions, BftFork> specCreator =
        Mockito.mock(BftSpecCreator.class);

    assertThatThrownBy(
            () -> BftForksSchedule.create(genesisConfigOptions, List.of(fork), specCreator))
        .hasMessage("Transition cannot be created for genesis block");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void throwsErrorIfHasForksWithDuplicateBlock() {
    final BftConfigOptions genesisConfigOptions = JsonBftConfigOptions.DEFAULT;
    final BftFork fork1 = createFork(1, 10);
    final BftFork fork2 = createFork(1, 20);
    final BftFork fork3 = createFork(2, 30);
    final BftSpecCreator<BftConfigOptions, BftFork> specCreator =
        Mockito.mock(BftSpecCreator.class);

    assertThatThrownBy(
            () ->
                BftForksSchedule.create(
                    genesisConfigOptions, List.of(fork1, fork2, fork3), specCreator))
        .hasMessage("Duplicate transitions cannot be created for the same block");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void createsScheduleUsingSpecCreator() {
    final BftConfigOptions genesisConfigOptions = JsonBftConfigOptions.DEFAULT;
    final BftForkSpec<BftConfigOptions> genesisForkSpec =
        new BftForkSpec<>(0, genesisConfigOptions);
    final BftFork fork1 = createFork(1, 10);
    final BftFork fork2 = createFork(2, 20);
    final BftSpecCreator<BftConfigOptions, BftFork> specCreator =
        Mockito.mock(BftSpecCreator.class);

    final BftConfigOptions configOptions1 = createBftConfigOptions(10);
    final BftConfigOptions configOptions2 = createBftConfigOptions(20);
    when(specCreator.create(genesisForkSpec, fork1)).thenReturn(configOptions1);
    when(specCreator.create(new BftForkSpec<>(1, configOptions1), fork2))
        .thenReturn(configOptions2);

    final BftForksSchedule<BftConfigOptions> schedule =
        BftForksSchedule.create(genesisConfigOptions, List.of(fork1, fork2), specCreator);
    assertThat(schedule.getFork(0)).isEqualTo(genesisForkSpec);
    assertThat(schedule.getFork(1)).isEqualTo(new BftForkSpec<>(1, configOptions1));
    assertThat(schedule.getFork(2)).isEqualTo(new BftForkSpec<>(2, configOptions2));
  }

  private BftForkSpec<BftConfigOptions> createForkSpec(
      final long block, final int blockPeriodSeconds) {
    final MutableBftConfigOptions bftConfigOptions = createBftConfigOptions(blockPeriodSeconds);
    return new BftForkSpec<>(block, bftConfigOptions);
  }

  private MutableBftConfigOptions createBftConfigOptions(final int blockPeriodSeconds) {
    final MutableBftConfigOptions bftConfigOptions =
        new MutableBftConfigOptions(JsonBftConfigOptions.DEFAULT);
    bftConfigOptions.setBlockPeriodSeconds(blockPeriodSeconds);
    return bftConfigOptions;
  }

  private BftFork createFork(final long block, final long blockPeriodSeconds) {
    return new BftFork(
        JsonUtil.objectNodeFromMap(
            Map.of(
                BftFork.FORK_BLOCK_KEY, block,
                BftFork.BLOCK_PERIOD_SECONDS_KEY, blockPeriodSeconds)));
  }
}
