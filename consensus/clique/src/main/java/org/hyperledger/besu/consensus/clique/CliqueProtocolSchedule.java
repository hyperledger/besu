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
import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.MainnetBlockValidator;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockImporter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;

import java.math.BigInteger;
import java.util.Optional;

/** Defines the protocol behaviours for a blockchain using Clique. */
public class CliqueProtocolSchedule {

  private static final BigInteger DEFAULT_CHAIN_ID = BigInteger.valueOf(4);

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final NodeKey nodeKey,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled) {

    final CliqueConfigOptions cliqueConfig = config.getCliqueConfigOptions();

    final Address localNodeAddress = Util.publicKeyToAddress(nodeKey.getPublicKey());

    if (cliqueConfig.getEpochLength() <= 0) {
      throw new IllegalArgumentException("Epoch length in config must be greater than zero");
    }

    final EpochManager epochManager = new EpochManager(cliqueConfig.getEpochLength());

    final Optional<EIP1559> eip1559 =
        ExperimentalEIPs.eip1559Enabled
            ? Optional.of(new EIP1559(config.getEIP1559BlockNumber().orElse(0)))
            : Optional.empty();

    return new ProtocolScheduleBuilder(
            config,
            DEFAULT_CHAIN_ID,
            builder ->
                applyCliqueSpecificModifications(
                    epochManager,
                    cliqueConfig.getBlockPeriodSeconds(),
                    localNodeAddress,
                    builder,
                    eip1559),
            privacyParameters,
            isRevertReasonEnabled)
        .createProtocolSchedule();
  }

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final NodeKey nodeKey,
      final boolean isRevertReasonEnabled) {
    return create(config, nodeKey, PrivacyParameters.DEFAULT, isRevertReasonEnabled);
  }

  private static ProtocolSpecBuilder applyCliqueSpecificModifications(
      final EpochManager epochManager,
      final long secondsBetweenBlocks,
      final Address localNodeAddress,
      final ProtocolSpecBuilder specBuilder,
      final Optional<EIP1559> eip1559) {

    return specBuilder
        .blockHeaderValidatorBuilder(
            getBlockHeaderValidator(epochManager, secondsBetweenBlocks, eip1559))
        .ommerHeaderValidatorBuilder(
            getBlockHeaderValidator(epochManager, secondsBetweenBlocks, eip1559))
        .blockBodyValidatorBuilder(MainnetBlockBodyValidator::new)
        .blockValidatorBuilder(MainnetBlockValidator::new)
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
      final Optional<EIP1559> eip1559) {
    return BlockHeaderValidationRulesetFactory.cliqueBlockHeaderValidator(
        secondsBetweenBlocks, epochManager, eip1559);
  }
}
