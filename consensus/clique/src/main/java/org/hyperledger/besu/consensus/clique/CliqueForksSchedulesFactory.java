/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.consensus.clique;

import org.hyperledger.besu.config.CliqueConfigOptions;
import org.hyperledger.besu.config.CliqueFork;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.ImmutableCliqueConfigOptions;
import org.hyperledger.besu.consensus.common.ForkSpec;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.ForksScheduleFactory;

/** The Clique forks schedules factory. */
public class CliqueForksSchedulesFactory {
  /** Default constructor. */
  CliqueForksSchedulesFactory() {}

  /**
   * Create forks schedule.
   *
   * @param genesisConfig the genesis config
   * @return the forks schedule
   */
  public static ForksSchedule<CliqueConfigOptions> create(
      final GenesisConfigOptions genesisConfig) {
    return ForksScheduleFactory.create(
        genesisConfig.getCliqueConfigOptions(),
        genesisConfig.getTransitions().getCliqueForks(),
        CliqueForksSchedulesFactory::createCliqueConfigOptions);
  }

  private static CliqueConfigOptions createCliqueConfigOptions(
      final ForkSpec<CliqueConfigOptions> lastSpec, final CliqueFork fork) {

    var options = ImmutableCliqueConfigOptions.builder().from(lastSpec.getValue());
    fork.getBlockPeriodSeconds().ifPresent(options::blockPeriodSeconds);
    fork.getCreateEmptyBlocks().ifPresent(options::createEmptyBlocks);
    return options.build();
  }
}
