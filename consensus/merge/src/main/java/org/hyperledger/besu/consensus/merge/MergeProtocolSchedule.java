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
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** The Merge protocol schedule. */
public class MergeProtocolSchedule {

  private static final BigInteger DEFAULT_CHAIN_ID = BigInteger.valueOf(1);

  /**
   * Create protocol schedule.
   *
   * @param config the config
   * @param isRevertReasonEnabled the is revert reason enabled
   * @param badBlockManager the cache to use to keep invalid blocks
   * @return the protocol schedule
   */
  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final boolean isRevertReasonEnabled,
      final BadBlockManager badBlockManager) {
    return create(config, PrivacyParameters.DEFAULT, isRevertReasonEnabled, badBlockManager);
  }

  /**
   * Create protocol schedule.
   *
   * @param config the config
   * @param privacyParameters the privacy parameters
   * @param isRevertReasonEnabled the is revert reason enabled
   * @param badBlockManager the cache to use to keep invalid blocks
   * @return the protocol schedule
   */
  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final BadBlockManager badBlockManager) {

    Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> postMergeModifications =
        new HashMap<>();
    postMergeModifications.put(
        0L,
        (specBuilder) ->
            MergeProtocolSchedule.applyParisSpecificModifications(
                specBuilder, config.getChainId()));
    unapplyModificationsFromShanghaiOnwards(config, postMergeModifications);

    return new ProtocolScheduleBuilder(
            config,
            DEFAULT_CHAIN_ID,
            new ProtocolSpecAdapters(postMergeModifications),
            privacyParameters,
            isRevertReasonEnabled,
            EvmConfiguration.DEFAULT,
            badBlockManager)
        .createProtocolSchedule();
  }

  /**
   * Apply Paris specific modifications because the Merge Transition code does not utilise {@link
   * org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecFactory.parisDefinition} until the
   * shanghaiDefinition is utilised. This is due to the way the Transition works via TTD rather than
   * via a blockNumber so it can't be looked up in the schedule.
   */
  private static ProtocolSpecBuilder applyParisSpecificModifications(
      final ProtocolSpecBuilder specBuilder, final Optional<BigInteger> chainId) {

    return specBuilder
        .evmBuilder(
            (gasCalculator, jdCacheConfig) ->
                MainnetEVMs.paris(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), EvmConfiguration.DEFAULT))
        .blockHeaderValidatorBuilder(MergeProtocolSchedule::getBlockHeaderValidator)
        .blockReward(Wei.ZERO)
        .difficultyCalculator((a, b, c) -> BigInteger.ZERO)
        .skipZeroBlockRewards(true)
        .isPoS(true)
        .name("Paris");
  }

  private static BlockHeaderValidator.Builder getBlockHeaderValidator(final FeeMarket feeMarket) {
    return MergeValidationRulesetFactory.mergeBlockHeaderValidator(feeMarket);
  }

  private static void unapplyModificationsFromShanghaiOnwards(
      final GenesisConfigOptions config,
      final Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> postMergeModifications) {
    // Any post-Paris fork can rely on the MainnetProtocolSpec definitions again
    // Must allow for config to skip Shanghai and go straight to a later fork.
    if (config.getForkBlockTimestamps().size() > 0) {
      postMergeModifications.put(config.getForkBlockTimestamps().get(0), Function.identity());
    }
  }
}
