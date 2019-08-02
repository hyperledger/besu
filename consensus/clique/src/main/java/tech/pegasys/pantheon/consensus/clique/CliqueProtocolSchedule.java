/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.clique;

import static tech.pegasys.pantheon.consensus.clique.BlockHeaderValidationRulesetFactory.cliqueBlockHeaderValidator;

import tech.pegasys.pantheon.config.CliqueConfigOptions;
import tech.pegasys.pantheon.config.GenesisConfigOptions;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.MainnetBlockValidator;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockBodyValidator;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockImporter;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolScheduleBuilder;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpecBuilder;

import java.math.BigInteger;

/** Defines the protocol behaviours for a blockchain using Clique. */
public class CliqueProtocolSchedule {

  private static final BigInteger DEFAULT_CHAIN_ID = BigInteger.valueOf(4);

  public static ProtocolSchedule<CliqueContext> create(
      final GenesisConfigOptions config,
      final KeyPair nodeKeys,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled) {

    final CliqueConfigOptions cliqueConfig = config.getCliqueConfigOptions();

    final Address localNodeAddress = Util.publicKeyToAddress(nodeKeys.getPublicKey());

    final EpochManager epochManager = new EpochManager(cliqueConfig.getEpochLength());
    return new ProtocolScheduleBuilder<>(
            config,
            DEFAULT_CHAIN_ID,
            builder ->
                applyCliqueSpecificModifications(
                    epochManager, cliqueConfig.getBlockPeriodSeconds(), localNodeAddress, builder),
            privacyParameters,
            isRevertReasonEnabled)
        .createProtocolSchedule();
  }

  public static ProtocolSchedule<CliqueContext> create(
      final GenesisConfigOptions config,
      final KeyPair nodeKeys,
      final boolean isRevertReasonEnabled) {
    return create(config, nodeKeys, PrivacyParameters.DEFAULT, isRevertReasonEnabled);
  }

  private static ProtocolSpecBuilder<CliqueContext> applyCliqueSpecificModifications(
      final EpochManager epochManager,
      final long secondsBetweenBlocks,
      final Address localNodeAddress,
      final ProtocolSpecBuilder<Void> specBuilder) {
    return specBuilder
        .changeConsensusContextType(
            difficultyCalculator -> cliqueBlockHeaderValidator(secondsBetweenBlocks, epochManager),
            difficultyCalculator -> cliqueBlockHeaderValidator(secondsBetweenBlocks, epochManager),
            MainnetBlockBodyValidator::new,
            MainnetBlockValidator::new,
            MainnetBlockImporter::new,
            new CliqueDifficultyCalculator(localNodeAddress))
        .blockReward(Wei.ZERO)
        .skipZeroBlockRewards(true)
        .miningBeneficiaryCalculator(CliqueHelpers::getProposerOfBlock)
        .blockHeaderFunctions(new CliqueBlockHeaderFunctions());
  }
}
