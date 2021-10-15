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

package org.hyperledger.besu.consensus.qbft.validator;

import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.consensus.common.bft.BftForkSpec;
import org.hyperledger.besu.consensus.common.bft.BftForksSchedule;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.function.Consumer;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class QbftTransitionNotifier {

  private static final Logger LOG = LogManager.getLogger();

  private final BftForksSchedule<QbftConfigOptions> bftForksSchedule;
  private final Consumer<String> msgConsumer;

  public QbftTransitionNotifier(final BftForksSchedule<QbftConfigOptions> bftForksSchedule) {
    this.bftForksSchedule = bftForksSchedule;
    this.msgConsumer = LOG::info;
  }

  @VisibleForTesting
  public QbftTransitionNotifier(
      final BftForksSchedule<QbftConfigOptions> bftForksSchedule,
      final Consumer<String> msgConsumer) {
    this.bftForksSchedule = bftForksSchedule;
    this.msgConsumer = msgConsumer;
  }

  public void checkTransitionChange(final BlockHeader parentHeader) {
    final BftForkSpec<QbftConfigOptions> currentForkSpec =
        bftForksSchedule.getFork(parentHeader.getNumber());
    final BftForkSpec<QbftConfigOptions> nextForkSpec =
        bftForksSchedule.getFork(parentHeader.getNumber() + 1L);

    final QbftConfigOptions currentConfigOptions = currentForkSpec.getConfigOptions();
    final QbftConfigOptions nextConfigOptions = nextForkSpec.getConfigOptions();

    if (hasChangedConfig(currentConfigOptions, nextConfigOptions)) {
      msgConsumer.accept(
          String.format(
              "Transitioning validator selection mode from %s to %s",
              parseConfigToLog(currentConfigOptions), parseConfigToLog(nextConfigOptions)));
    }
  }

  private boolean hasChangedConfig(
      final QbftConfigOptions currentConfig, final QbftConfigOptions nextConfig) {
    if (currentConfig.isValidatorBlockHeaderMode() && nextConfig.isValidatorBlockHeaderMode()) {
      return false;
    }

    return !currentConfig
        .getValidatorContractAddress()
        .equals(nextConfig.getValidatorContractAddress());
  }

  private String parseConfigToLog(final QbftConfigOptions configOptions) {
    if (configOptions.getValidatorContractAddress().isPresent()) {
      return String.format("ADDRESS(%s)", configOptions.getValidatorContractAddress().get());
    } else {
      return "BLOCKHEADER";
    }
  }
}
