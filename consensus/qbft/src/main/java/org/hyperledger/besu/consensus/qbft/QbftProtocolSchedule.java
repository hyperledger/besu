/*
 * Copyright ConsenSys AG.
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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.config.BftFork;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.QbftFork;
import org.hyperledger.besu.config.QbftFork.VALIDATOR_SELECTION_MODE;
import org.hyperledger.besu.consensus.common.bft.BaseBftProtocolSchedule;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.util.function.Supplier;

/** Defines the protocol behaviours for a blockchain using a QBFT consensus mechanism. */
public class QbftProtocolSchedule extends BaseBftProtocolSchedule {

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final BftExtraDataCodec bftExtraDataCodec,
      final EvmConfiguration evmConfiguration) {
    return new QbftProtocolSchedule()
        .createProtocolSchedule(
            config, privacyParameters, isRevertReasonEnabled, bftExtraDataCodec, evmConfiguration);
  }

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final BftExtraDataCodec bftExtraDataCodec,
      final EvmConfiguration evmConfiguration) {
    return create(config, PrivacyParameters.DEFAULT, false, bftExtraDataCodec, evmConfiguration);
  }

  @Override
  protected Supplier<BlockHeaderValidator.Builder> createForkBlockHeaderRuleset(
      final GenesisConfigOptions config, final BftFork fork) {
    checkArgument(fork instanceof QbftFork, "QbftProtocolSchedule must use QbftForks");
    final QbftFork qbftFork = (QbftFork) fork;
    return () ->
        QbftBlockHeaderValidationRulesetFactory.blockHeaderValidator(
            config.getBftConfigOptions().getBlockPeriodSeconds(),
            qbftFork
                .getValidatorSelectionMode()
                .filter(m -> m == VALIDATOR_SELECTION_MODE.CONTRACT)
                .isPresent());
  }

  @Override
  protected Supplier<BlockHeaderValidator.Builder> createGenesisBlockHeaderRuleset(
      final GenesisConfigOptions config) {
    return () ->
        QbftBlockHeaderValidationRulesetFactory.blockHeaderValidator(
            config.getBftConfigOptions().getBlockPeriodSeconds(),
            config.getQbftConfigOptions().getValidatorContractAddress().isPresent());
  }
}
