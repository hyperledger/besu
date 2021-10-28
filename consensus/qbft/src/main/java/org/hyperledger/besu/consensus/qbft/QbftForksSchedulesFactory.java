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
package org.hyperledger.besu.consensus.qbft;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.config.QbftFork;
import org.hyperledger.besu.config.QbftFork.VALIDATOR_SELECTION_MODE;
import org.hyperledger.besu.consensus.common.bft.BftForkSpec;
import org.hyperledger.besu.consensus.common.bft.BftForksSchedule;

import java.util.Optional;

public class QbftForksSchedulesFactory {

  public static BftForksSchedule<QbftConfigOptions> create(
      final GenesisConfigOptions genesisConfig) {
    return BftForksSchedule.create(
        genesisConfig.getQbftConfigOptions(),
        genesisConfig.getTransitions().getQbftForks(),
        QbftForksSchedulesFactory::createQbftConfigOptions);
  }

  private static QbftConfigOptions createQbftConfigOptions(
      final BftForkSpec<QbftConfigOptions> lastSpec, final QbftFork fork) {
    final MutableQbftConfigOptions bftConfigOptions =
        new MutableQbftConfigOptions(lastSpec.getConfigOptions());

    fork.getBlockPeriodSeconds().ifPresent(bftConfigOptions::setBlockPeriodSeconds);
    fork.getBlockRewardWei().ifPresent(bftConfigOptions::setBlockRewardWei);

    if (fork.getValidatorSelectionMode().isPresent()) {
      final VALIDATOR_SELECTION_MODE mode = fork.getValidatorSelectionMode().get();
      if (mode == VALIDATOR_SELECTION_MODE.BLOCKHEADER) {
        bftConfigOptions.setValidatorContractAddress(Optional.empty());
        bftConfigOptions.setHasValidatorOverrides(fork.getValidators().isPresent());
      } else if (mode == VALIDATOR_SELECTION_MODE.CONTRACT
          && fork.getValidatorContractAddress().isPresent()) {
        bftConfigOptions.setValidatorContractAddress(fork.getValidatorContractAddress());
      } else if (fork.getValidatorContractAddress().isEmpty()) {
        throw new IllegalStateException(
            "QBFT transition has config with contract mode but no contract address");
      }
    }

    return bftConfigOptions;
  }
}
