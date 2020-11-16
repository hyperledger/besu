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

import static org.hyperledger.besu.consensus.ibft.IbftBlockHeaderValidationRulesetFactory.ibftBlockHeaderValidator;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.IbftConfigOptions;
import org.hyperledger.besu.ethereum.MainnetBlockValidator;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockImporter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;

import java.math.BigInteger;

/** Defines the protocol behaviours for a blockchain using IBFT. */
public class IbftProtocolSchedule {

  private static final BigInteger DEFAULT_CHAIN_ID = BigInteger.ONE;

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled) {

    return new ProtocolScheduleBuilder(
            config,
            DEFAULT_CHAIN_ID,
            builder -> applyIbftChanges(config.getIbft2ConfigOptions(), builder),
            privacyParameters,
            isRevertReasonEnabled,
            config.isQuorum())
        .createProtocolSchedule();
  }

  public static ProtocolSchedule create(
      final GenesisConfigOptions config, final boolean isRevertReasonEnabled) {
    return create(config, PrivacyParameters.DEFAULT, isRevertReasonEnabled);
  }

  public static ProtocolSchedule create(final GenesisConfigOptions config) {
    return create(config, PrivacyParameters.DEFAULT, false);
  }

  private static ProtocolSpecBuilder applyIbftChanges(
      final IbftConfigOptions ibftConfig, final ProtocolSpecBuilder builder) {

    if (ibftConfig.getEpochLength() <= 0) {
      throw new IllegalArgumentException("Epoch length in config must be greater than zero");
    }

    if (ibftConfig.getBlockRewardWei().signum() < 0) {
      throw new IllegalArgumentException("Ibft2 Block reward in config cannot be negative");
    }

    builder
        .blockHeaderValidatorBuilder(ibftBlockHeaderValidator(ibftConfig.getBlockPeriodSeconds()))
        .ommerHeaderValidatorBuilder(ibftBlockHeaderValidator(ibftConfig.getBlockPeriodSeconds()))
        .blockBodyValidatorBuilder(MainnetBlockBodyValidator::new)
        .blockValidatorBuilder(MainnetBlockValidator::new)
        .blockImporterBuilder(MainnetBlockImporter::new)
        .difficultyCalculator((time, parent, protocolContext) -> BigInteger.ONE)
        .blockReward(Wei.of(ibftConfig.getBlockRewardWei()))
        .skipZeroBlockRewards(true)
        .blockHeaderFunctions(IbftBlockHeaderFunctions.forOnChainBlock());

    if (ibftConfig.getMiningBeneficiary().isPresent()) {
      final Address miningBeneficiary;
      try {
        // Precalculate beneficiary to ensure string is valid now, rather than on lambda execution.
        miningBeneficiary = Address.fromHexString(ibftConfig.getMiningBeneficiary().get());
      } catch (final IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Mining beneficiary in config is not a valid ethereum address", e);
      }
      builder.miningBeneficiaryCalculator(header -> miningBeneficiary);
    }

    return builder;
  }
}
