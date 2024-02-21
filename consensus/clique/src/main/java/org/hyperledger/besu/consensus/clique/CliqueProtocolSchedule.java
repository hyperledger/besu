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
package org.hyperledger.besu.consensus.clique;

import org.hyperledger.besu.config.CliqueConfigOptions;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockImporter;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecs;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.Optional;

/** Defines the protocol behaviours for a blockchain using Clique. */
public class CliqueProtocolSchedule {

  private static final BigInteger DEFAULT_CHAIN_ID = BigInteger.valueOf(4);

  /**
   * Create protocol schedule.
   *
   * @param config the config
   * @param nodeKey the node key
   * @param privacyParameters the privacy parameters
   * @param isRevertReasonEnabled the is revert reason enabled
   * @param evmConfiguration the evm configuration
   * @param badBlockManager the cache to use to keep invalid blocks
   * @return the protocol schedule
   */
  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final NodeKey nodeKey,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final EvmConfiguration evmConfiguration,
      final BadBlockManager badBlockManager) {

    final CliqueConfigOptions cliqueConfig = config.getCliqueConfigOptions();

    final Address localNodeAddress = Util.publicKeyToAddress(nodeKey.getPublicKey());

    if (cliqueConfig.getEpochLength() <= 0) {
      throw new IllegalArgumentException("Epoch length in config must be greater than zero");
    }

    final EpochManager epochManager = new EpochManager(cliqueConfig.getEpochLength());

    return new ProtocolScheduleBuilder(
            config,
            DEFAULT_CHAIN_ID,
            ProtocolSpecAdapters.create(
                0,
                builder ->
                    applyCliqueSpecificModifications(
                        epochManager,
                        cliqueConfig.getBlockPeriodSeconds(),
                        cliqueConfig.getCreateEmptyBlocks(),
                        localNodeAddress,
                        builder)),
            privacyParameters,
            isRevertReasonEnabled,
            evmConfiguration,
            badBlockManager)
        .createProtocolSchedule();
  }

  /**
   * Create protocol schedule.
   *
   * @param config the config
   * @param nodeKey the node key
   * @param isRevertReasonEnabled the is revert reason enabled
   * @param evmConfiguration the evm configuration
   * @param badBlockManager the cache to use to keep invalid blocks
   * @return the protocol schedule
   */
  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final NodeKey nodeKey,
      final boolean isRevertReasonEnabled,
      final EvmConfiguration evmConfiguration,
      final BadBlockManager badBlockManager) {
    return create(
        config,
        nodeKey,
        PrivacyParameters.DEFAULT,
        isRevertReasonEnabled,
        evmConfiguration,
        badBlockManager);
  }

  private static ProtocolSpecBuilder applyCliqueSpecificModifications(
      final EpochManager epochManager,
      final long secondsBetweenBlocks,
      final boolean createEmptyBlocks,
      final Address localNodeAddress,
      final ProtocolSpecBuilder specBuilder) {

    return specBuilder
        .blockHeaderValidatorBuilder(
            baseFeeMarket ->
                getBlockHeaderValidator(
                    epochManager, secondsBetweenBlocks, createEmptyBlocks, baseFeeMarket))
        .ommerHeaderValidatorBuilder(
            baseFeeMarket ->
                getBlockHeaderValidator(
                    epochManager, secondsBetweenBlocks, createEmptyBlocks, baseFeeMarket))
        .blockBodyValidatorBuilder(MainnetBlockBodyValidator::new)
        .blockValidatorBuilder(MainnetProtocolSpecs.blockValidatorBuilder())
        .blockImporterBuilder(MainnetBlockImporter::new)
        .difficultyCalculator(new CliqueDifficultyCalculator(localNodeAddress))
        .blockReward(Wei.ZERO)
        .skipZeroBlockRewards(true)
        .miningBeneficiaryCalculator(CliqueHelpers::getProposerOfBlock)
        .blockHeaderFunctions(new CliqueBlockHeaderFunctions());
  }

  private static BlockHeaderValidator.Builder getBlockHeaderValidator(
      final EpochManager epochManager,
      final long secondsBetweenBlocks,
      final boolean createEmptyBlocks,
      final FeeMarket feeMarket) {
    Optional<BaseFeeMarket> baseFeeMarket =
        Optional.of(feeMarket).filter(FeeMarket::implementsBaseFee).map(BaseFeeMarket.class::cast);

    return BlockHeaderValidationRulesetFactory.cliqueBlockHeaderValidator(
        secondsBetweenBlocks, createEmptyBlocks, epochManager, baseFeeMarket);
  }
}
