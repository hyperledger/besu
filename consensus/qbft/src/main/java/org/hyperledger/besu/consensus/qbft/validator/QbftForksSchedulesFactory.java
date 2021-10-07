/*
 *  Copyright Hyperledger Besu Contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.consensus.qbft.validator;

import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.config.QbftFork;
import org.hyperledger.besu.config.QbftFork.VALIDATOR_SELECTION_MODE;
import org.hyperledger.besu.consensus.common.BftForksSchedule;
import org.hyperledger.besu.consensus.qbft.MutableQbftConfigOptions;

import java.util.List;
import java.util.Optional;

public class QbftForksSchedulesFactory {

  public static BftForksSchedule<QbftConfigOptions> create(
      final QbftConfigOptions genesisConfig, final List<QbftFork> qbftForks) {
    return BftForksSchedule.create(
        genesisConfig,
        qbftForks,
        (lastSpec, fork) -> {
          final MutableQbftConfigOptions bftConfigOptions =
              new MutableQbftConfigOptions(lastSpec.getConfigOptions());

          if (fork.getBlockPeriodSeconds().isPresent()) {
            bftConfigOptions.setBlockPeriodSeconds(fork.getBlockPeriodSeconds().getAsInt());
          }
          if (fork.getBlockRewardWei().isPresent()) {
            bftConfigOptions.setBlockRewardWei(fork.getBlockRewardWei().get());
          }
          if (fork.getValidatorSelectionMode().isPresent()) {
            final VALIDATOR_SELECTION_MODE validatorSelectionMode =
                fork.getValidatorSelectionMode().get();
            if (validatorSelectionMode == VALIDATOR_SELECTION_MODE.BLOCKHEADER) {
              bftConfigOptions.setValidatorContractAddress(Optional.empty());
            }
          }
          if (fork.getValidatorContractAddress().isPresent()) {
            bftConfigOptions.setValidatorContractAddress(fork.getValidatorContractAddress());
          }

          return bftConfigOptions;
        });
  }
}
