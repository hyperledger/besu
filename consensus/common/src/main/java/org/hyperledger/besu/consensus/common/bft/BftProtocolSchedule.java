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
package org.hyperledger.besu.consensus.common.bft;

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockImporter;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecs;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;

import java.math.BigInteger;
import java.util.function.Function;

/** Defines the protocol behaviours for a blockchain using a BFT consensus mechanism. */
public class BftProtocolSchedule {

  private static final BigInteger DEFAULT_CHAIN_ID = BigInteger.ONE;

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final Function<Integer, BlockHeaderValidator.Builder> blockHeaderRuleset,
      final BftExtraDataCodec bftExtraDataCodec) {

    return new ProtocolScheduleBuilder(
            config,
            DEFAULT_CHAIN_ID,
            ProtocolSpecAdapters.create(
                0,
                builder ->
                    applyBftChanges(
                        config.getBftConfigOptions(),
                        builder,
                        config.isQuorum(),
                        blockHeaderRuleset,
                        bftExtraDataCodec)),
            privacyParameters,
            isRevertReasonEnabled,
            config.isQuorum())
        .createProtocolSchedule();
  }

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final boolean isRevertReasonEnabled,
      final Function<Integer, BlockHeaderValidator.Builder> blockHeaderRuleset,
      final BftExtraDataCodec bftExtraDataCodec) {
    return create(
        config,
        PrivacyParameters.DEFAULT,
        isRevertReasonEnabled,
        blockHeaderRuleset,
        bftExtraDataCodec);
  }

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final Function<Integer, BlockHeaderValidator.Builder> blockHeaderRuleset,
      final BftExtraDataCodec bftExtraDataCodec) {
    return create(config, PrivacyParameters.DEFAULT, false, blockHeaderRuleset, bftExtraDataCodec);
  }

  private static ProtocolSpecBuilder applyBftChanges(
      final BftConfigOptions configOptions,
      final ProtocolSpecBuilder builder,
      final boolean goQuorumMode,
      final Function<Integer, BlockHeaderValidator.Builder> blockHeaderRuleset,
      final BftExtraDataCodec bftExtraDataCodec) {

    if (configOptions.getEpochLength() <= 0) {
      throw new IllegalArgumentException("Epoch length in config must be greater than zero");
    }

    if (configOptions.getBlockRewardWei().signum() < 0) {
      throw new IllegalArgumentException("Bft Block reward in config cannot be negative");
    }

    builder
        .blockHeaderValidatorBuilder(
            blockHeaderRuleset.apply(configOptions.getBlockPeriodSeconds()))
        .ommerHeaderValidatorBuilder(
            blockHeaderRuleset.apply(configOptions.getBlockPeriodSeconds()))
        .blockBodyValidatorBuilder(MainnetBlockBodyValidator::new)
        .blockValidatorBuilder(MainnetProtocolSpecs.blockValidatorBuilder(goQuorumMode))
        .blockImporterBuilder(MainnetBlockImporter::new)
        .difficultyCalculator((time, parent, protocolContext) -> BigInteger.ONE)
        .blockReward(Wei.of(configOptions.getBlockRewardWei()))
        .skipZeroBlockRewards(true)
        .blockHeaderFunctions(BftBlockHeaderFunctions.forOnChainBlock(bftExtraDataCodec));

    if (configOptions.getMiningBeneficiary().isPresent()) {
      final Address miningBeneficiary;
      try {
        // Precalculate beneficiary to ensure string is valid now, rather than on lambda execution.
        miningBeneficiary = Address.fromHexString(configOptions.getMiningBeneficiary().get());
      } catch (final IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Mining beneficiary in config is not a valid ethereum address", e);
      }
      builder.miningBeneficiaryCalculator(header -> miningBeneficiary);
    }

    return builder;
  }
}
