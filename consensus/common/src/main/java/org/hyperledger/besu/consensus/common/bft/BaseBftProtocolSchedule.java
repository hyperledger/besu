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
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockImporter;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecs;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Defines the protocol behaviours for a blockchain using a BFT consensus mechanism. */
public abstract class BaseBftProtocolSchedule {

  private static final BigInteger DEFAULT_CHAIN_ID = BigInteger.ONE;

  public ProtocolSchedule createProtocolSchedule(
      final GenesisConfigOptions config,
      final ForksSchedule<? extends BftConfigOptions> forksSchedule,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled,
      final BftExtraDataCodec bftExtraDataCodec,
      final EvmConfiguration evmConfiguration) {
    final Map<Long, Function<ProtocolSpecBuilder, ProtocolSpecBuilder>> specMap = new HashMap<>();

    forksSchedule
        .getForks()
        .forEach(
            forkSpec ->
                specMap.put(
                    forkSpec.getBlock(),
                    builder ->
                        applyBftChanges(
                            builder, forkSpec.getValue(), config.isQuorum(), bftExtraDataCodec)));

    final ProtocolSpecAdapters specAdapters = new ProtocolSpecAdapters(specMap);

    return new ProtocolScheduleBuilder(
            config,
            DEFAULT_CHAIN_ID,
            specAdapters,
            privacyParameters,
            isRevertReasonEnabled,
            config.isQuorum(),
            evmConfiguration)
        .createProtocolSchedule();
  }

  protected abstract BlockHeaderValidator.Builder createBlockHeaderRuleset(
      final BftConfigOptions config, final FeeMarket feeMarket);

  private ProtocolSpecBuilder applyBftChanges(
      final ProtocolSpecBuilder builder,
      final BftConfigOptions configOptions,
      final boolean goQuorumMode,
      final BftExtraDataCodec bftExtraDataCodec) {
    if (configOptions.getEpochLength() <= 0) {
      throw new IllegalArgumentException("Epoch length in config must be greater than zero");
    }
    if (configOptions.getBlockRewardWei().signum() < 0) {
      throw new IllegalArgumentException("Bft Block reward in config cannot be negative");
    }

    return builder
        .blockHeaderValidatorBuilder(
            feeMarket -> createBlockHeaderRuleset(configOptions, feeMarket))
        .ommerHeaderValidatorBuilder(
            feeMarket -> createBlockHeaderRuleset(configOptions, feeMarket))
        .blockBodyValidatorBuilder(MainnetBlockBodyValidator::new)
        .blockValidatorBuilder(MainnetProtocolSpecs.blockValidatorBuilder(goQuorumMode))
        .blockImporterBuilder(MainnetBlockImporter::new)
        .difficultyCalculator((time, parent, protocolContext) -> BigInteger.ONE)
        .skipZeroBlockRewards(true)
        .blockHeaderFunctions(BftBlockHeaderFunctions.forOnchainBlock(bftExtraDataCodec))
        .blockReward(Wei.of(configOptions.getBlockRewardWei()))
        .miningBeneficiaryCalculator(
            header -> configOptions.getMiningBeneficiary().orElseGet(header::getCoinbase));
  }
}
