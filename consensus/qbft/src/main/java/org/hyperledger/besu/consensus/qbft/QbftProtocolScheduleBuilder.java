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

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.bft.BaseBftProtocolScheduleBuilder;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftProtocolSchedule;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.util.Optional;

/** Defines the protocol behaviours for a blockchain using a QBFT consensus mechanism. */
public class QbftProtocolScheduleBuilder extends BaseBftProtocolScheduleBuilder {

  /**
   * Create protocol schedule.
   *
   * @param config the config
   * @param qbftForksSchedule the qbft forks schedule
   * @param privacyParameters the privacy parameters
   * @param isRevertReasonEnabled the is revert reason enabled
   * @param bftExtraDataCodec the bft extra data codec
   * @param evmConfiguration the evm configuration
   * @param badBlockManager the cache to use to keep invalid blocks
   * @return the protocol schedule
   */
  public static BftProtocolSchedule create(
      final GenesisConfigOptions config,
      final ForksSchedule<QbftConfigOptions> qbftForksSchedule,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final BftExtraDataCodec bftExtraDataCodec,
      final EvmConfiguration evmConfiguration,
      final BadBlockManager badBlockManager) {
    return new QbftProtocolScheduleBuilder()
        .createProtocolSchedule(
            config,
            qbftForksSchedule,
            privacyParameters,
            isRevertReasonEnabled,
            bftExtraDataCodec,
            evmConfiguration,
            badBlockManager);
  }

  /**
   * Create protocol schedule.
   *
   * @param config the config
   * @param qbftForksSchedule the qbft forks schedule
   * @param bftExtraDataCodec the bft extra data codec
   * @param evmConfiguration the evm configuration
   * @param badBlockManager the cache to use to keep invalid blocks
   * @return the protocol schedule
   */
  public static BftProtocolSchedule create(
      final GenesisConfigOptions config,
      final ForksSchedule<QbftConfigOptions> qbftForksSchedule,
      final BftExtraDataCodec bftExtraDataCodec,
      final EvmConfiguration evmConfiguration,
      final BadBlockManager badBlockManager) {
    return create(
        config,
        qbftForksSchedule,
        PrivacyParameters.DEFAULT,
        false,
        bftExtraDataCodec,
        evmConfiguration,
        badBlockManager);
  }

  /**
   * Create protocol schedule.
   *
   * @param config the config
   * @param qbftForksSchedule the qbft forks schedule
   * @param isRevertReasonEnabled the is revert reason enabled
   * @param bftExtraDataCodec the bft extra data codec
   * @param badBlockManager the cache to use to keep invalid blocks
   * @return the protocol schedule
   */
  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final ForksSchedule<QbftConfigOptions> qbftForksSchedule,
      final boolean isRevertReasonEnabled,
      final BftExtraDataCodec bftExtraDataCodec,
      final BadBlockManager badBlockManager) {
    return create(
        config,
        qbftForksSchedule,
        PrivacyParameters.DEFAULT,
        isRevertReasonEnabled,
        bftExtraDataCodec,
        EvmConfiguration.DEFAULT,
        badBlockManager);
  }

  @Override
  protected BlockHeaderValidator.Builder createBlockHeaderRuleset(
      final BftConfigOptions config, final FeeMarket feeMarket) {
    checkArgument(
        config instanceof QbftConfigOptions,
        "QbftProtocolScheduleBuilder must use QbftConfigOptions");
    final QbftConfigOptions qbftConfigOptions = (QbftConfigOptions) config;
    final Optional<BaseFeeMarket> baseFeeMarket =
        Optional.of(feeMarket).filter(FeeMarket::implementsBaseFee).map(BaseFeeMarket.class::cast);

    return QbftBlockHeaderValidationRulesetFactory.blockHeaderValidator(
        qbftConfigOptions.getBlockPeriodSeconds(),
        qbftConfigOptions.isValidatorContractMode(),
        baseFeeMarket);
  }
}
