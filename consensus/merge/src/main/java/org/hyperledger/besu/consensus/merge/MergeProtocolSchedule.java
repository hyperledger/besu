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
package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.Optional;

public class MergeProtocolSchedule {

  private static final BigInteger DEFAULT_CHAIN_ID = BigInteger.valueOf(1);

  public static ProtocolSchedule create(
      final GenesisConfigOptions config, final boolean isRevertReasonEnabled) {
    return create(config, PrivacyParameters.DEFAULT, isRevertReasonEnabled);
  }

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled) {

    return new ProtocolScheduleBuilder(
            config,
            DEFAULT_CHAIN_ID,
            ProtocolSpecAdapters.create(
                0,
                (specBuilder) ->
                    MergeProtocolSchedule.applyMergeSpecificModifications(
                        specBuilder, config.getChainId())),
            privacyParameters,
            isRevertReasonEnabled,
            config.isQuorum(),
            EvmConfiguration.DEFAULT)
        .createProtocolSchedule();
  }

  private static ProtocolSpecBuilder applyMergeSpecificModifications(
      final ProtocolSpecBuilder specBuilder, final Optional<BigInteger> chainId) {

    return specBuilder
        .evmBuilder(
            (gasCalculator, jdCacheConfig) ->
                MainnetEVMs.paris(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), EvmConfiguration.DEFAULT))
        .blockProcessorBuilder(MergeBlockProcessor::new)
        .blockHeaderValidatorBuilder(MergeProtocolSchedule::getBlockHeaderValidator)
        .blockReward(Wei.ZERO)
        .difficultyCalculator((a, b, c) -> BigInteger.ZERO)
        .skipZeroBlockRewards(true);
  }

  private static BlockHeaderValidator.Builder getBlockHeaderValidator(final FeeMarket feeMarket) {
    return MergeValidationRulesetFactory.mergeBlockHeaderValidator(feeMarket);
  }
}
