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
package org.hyperledger.besu.consensus.ibft;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.bft.BaseBftProtocolScheduleBuilder;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftProtocolSchedule;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.util.Optional;

/** Defines the protocol behaviours for a blockchain using a BFT consensus mechanism. */
public class IbftProtocolScheduleBuilder extends BaseBftProtocolScheduleBuilder {

  /**
   * Create protocol schedule.
   *
   * @param config the config
   * @param forksSchedule the forks schedule
   * @param privacyParameters the privacy parameters
   * @param isRevertReasonEnabled the is revert reason enabled
   * @param bftExtraDataCodec the bft extra data codec
   * @param evmConfiguration the evm configuration
   * @param badBlockManager the cache to use to keep invalid blocks
   * @return the protocol schedule
   */
  public static BftProtocolSchedule create(
      final GenesisConfigOptions config,
      final ForksSchedule<BftConfigOptions> forksSchedule,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final BftExtraDataCodec bftExtraDataCodec,
      final EvmConfiguration evmConfiguration,
      final BadBlockManager badBlockManager) {
    return new IbftProtocolScheduleBuilder()
        .createProtocolSchedule(
            config,
            forksSchedule,
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
   * @param forksSchedule the forks schedule
   * @param bftExtraDataCodec the bft extra data codec
   * @param evmConfiguration the evm configuration
   * @param badBlockManager the cache to use to keep invalid blocks
   * @return the protocol schedule
   */
  public static BftProtocolSchedule create(
      final GenesisConfigOptions config,
      final ForksSchedule<BftConfigOptions> forksSchedule,
      final BftExtraDataCodec bftExtraDataCodec,
      final EvmConfiguration evmConfiguration,
      final BadBlockManager badBlockManager) {
    return create(
        config,
        forksSchedule,
        PrivacyParameters.DEFAULT,
        false,
        bftExtraDataCodec,
        evmConfiguration,
        badBlockManager);
  }

  @Override
  protected BlockHeaderValidator.Builder createBlockHeaderRuleset(
      final BftConfigOptions config, final FeeMarket feeMarket) {
    final Optional<BaseFeeMarket> baseFeeMarket =
        Optional.of(feeMarket).filter(FeeMarket::implementsBaseFee).map(BaseFeeMarket.class::cast);

    return IbftBlockHeaderValidationRulesetFactory.blockHeaderValidator(
        config.getBlockPeriodSeconds(), baseFeeMarket);
  }
}
