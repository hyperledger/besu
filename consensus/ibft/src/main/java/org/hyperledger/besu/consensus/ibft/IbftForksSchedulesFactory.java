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
package org.hyperledger.besu.consensus.ibft;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.BftFork;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.common.ForkSpec;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.bft.BftForksScheduleFactory;
import org.hyperledger.besu.consensus.common.bft.MutableBftConfigOptions;

public class IbftForksSchedulesFactory {

  public static ForksSchedule<BftConfigOptions> create(final GenesisConfigOptions genesisConfig) {
    return BftForksScheduleFactory.create(
        genesisConfig.getBftConfigOptions(),
        genesisConfig.getTransitions().getIbftForks(),
        IbftForksSchedulesFactory::createBftConfigOptions);
  }

  private static BftConfigOptions createBftConfigOptions(
      final ForkSpec<BftConfigOptions> lastSpec, final BftFork fork) {
    final MutableBftConfigOptions bftConfigOptions =
        new MutableBftConfigOptions(lastSpec.getValue());

    fork.getBlockPeriodSeconds().ifPresent(bftConfigOptions::setBlockPeriodSeconds);
    fork.getBlockRewardWei().ifPresent(bftConfigOptions::setBlockRewardWei);

    if (fork.isMiningBeneficiaryConfigured()) {
      // Only override if mining beneficiary is explicitly configured
      bftConfigOptions.setMiningBeneficiary(fork.getMiningBeneficiary());
    }

    return bftConfigOptions;
  }
}
