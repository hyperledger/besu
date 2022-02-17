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
package org.hyperledger.besu.ethereum.mainnet;

import static org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecs.powHasher;

import org.hyperledger.besu.config.PowAlgorithm;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.feemarket.CoinbaseFeePriceCalculator;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.evm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.DieHardGasCalculator;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.PetersburgGasCalculator;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.TangerineWhistleGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

public class ClassicProtocolSpecs {
  private static final Wei MAX_BLOCK_REWARD = Wei.fromEth(5);

  public static ProtocolSpecBuilder classicRecoveryInitDefinition(
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    return MainnetProtocolSpecs.homesteadDefinition(
            contractSizeLimit, configStackSizeLimit, quorumCompatibilityMode, evmConfiguration)
        .blockHeaderValidatorBuilder(
            feeMarket -> MainnetBlockHeaderValidator.createClassicValidator())
        .name("ClassicRecoveryInit");
  }

  public static ProtocolSpecBuilder tangerineWhistleDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    return MainnetProtocolSpecs.homesteadDefinition(
            contractSizeLimit, configStackSizeLimit, quorumCompatibilityMode, evmConfiguration)
        .gasCalculator(TangerineWhistleGasCalculator::new)
        .transactionValidatorBuilder(
            gasCalculator ->
                new MainnetTransactionValidator(
                    gasCalculator, true, chainId, quorumCompatibilityMode))
        .name("ClassicTangerineWhistle");
  }

  public static ProtocolSpecBuilder dieHardDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    return tangerineWhistleDefinition(
            chainId,
            OptionalInt.empty(),
            configStackSizeLimit,
            quorumCompatibilityMode,
            evmConfiguration)
        .gasCalculator(DieHardGasCalculator::new)
        .difficultyCalculator(ClassicDifficultyCalculators.DIFFICULTY_BOMB_PAUSED)
        .name("DieHard");
  }

  public static ProtocolSpecBuilder gothamDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final OptionalLong ecip1017EraRounds,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    return dieHardDefinition(
            chainId,
            contractSizeLimit,
            configStackSizeLimit,
            quorumCompatibilityMode,
            evmConfiguration)
        .blockReward(MAX_BLOCK_REWARD)
        .difficultyCalculator(ClassicDifficultyCalculators.DIFFICULTY_BOMB_DELAYED)
        .blockProcessorBuilder(
            (transactionProcessor,
                transactionReceiptFactory,
                blockReward,
                miningBeneficiaryCalculator,
                skipZeroBlockRewards,
                goQuorumPrivacyParameters) ->
                new ClassicBlockProcessor(
                    transactionProcessor,
                    transactionReceiptFactory,
                    blockReward,
                    miningBeneficiaryCalculator,
                    skipZeroBlockRewards,
                    ecip1017EraRounds))
        .name("Gotham");
  }

  public static ProtocolSpecBuilder defuseDifficultyBombDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final OptionalLong ecip1017EraRounds,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    return gothamDefinition(
            chainId,
            contractSizeLimit,
            configStackSizeLimit,
            ecip1017EraRounds,
            quorumCompatibilityMode,
            evmConfiguration)
        .difficultyCalculator(ClassicDifficultyCalculators.DIFFICULTY_BOMB_REMOVED)
        .transactionValidatorBuilder(
            gasCalculator ->
                new MainnetTransactionValidator(
                    gasCalculator, true, chainId, quorumCompatibilityMode))
        .name("DefuseDifficultyBomb");
  }

  public static ProtocolSpecBuilder atlantisDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason,
      final OptionalLong ecip1017EraRounds,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    final int contractSizeLimit =
        configContractSizeLimit.orElse(MainnetProtocolSpecs.SPURIOUS_DRAGON_CONTRACT_SIZE_LIMIT);
    final int stackSizeLimit = configStackSizeLimit.orElse(MessageFrame.DEFAULT_MAX_STACK_SIZE);
    return gothamDefinition(
            chainId,
            configContractSizeLimit,
            configStackSizeLimit,
            ecip1017EraRounds,
            quorumCompatibilityMode,
            evmConfiguration)
        .evmBuilder(MainnetEVMs::byzantium)
        .evmConfiguration(evmConfiguration)
        .gasCalculator(SpuriousDragonGasCalculator::new)
        .skipZeroBlockRewards(true)
        .messageCallProcessorBuilder(MessageCallProcessor::new)
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::byzantium)
        .difficultyCalculator(ClassicDifficultyCalculators.EIP100)
        .transactionReceiptFactory(
            enableRevertReason
                ? ClassicProtocolSpecs::byzantiumTransactionReceiptFactoryWithReasonEnabled
                : ClassicProtocolSpecs::byzantiumTransactionReceiptFactory)
        .contractCreationProcessorBuilder(
            (gasCalculator, evm) ->
                new ContractCreationProcessor(
                    gasCalculator,
                    evm,
                    true,
                    Collections.singletonList(MaxCodeSizeRule.of(contractSizeLimit)),
                    1))
        .transactionProcessorBuilder(
            (gasCalculator,
                transactionValidator,
                contractCreationProcessor,
                messageCallProcessor) ->
                new MainnetTransactionProcessor(
                    gasCalculator,
                    transactionValidator,
                    contractCreationProcessor,
                    messageCallProcessor,
                    true,
                    stackSizeLimit,
                    FeeMarket.legacy(),
                    CoinbaseFeePriceCalculator.frontier()))
        .name("Atlantis");
  }

  public static ProtocolSpecBuilder aghartaDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason,
      final OptionalLong ecip1017EraRounds,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    return atlantisDefinition(
            chainId,
            configContractSizeLimit,
            configStackSizeLimit,
            enableRevertReason,
            ecip1017EraRounds,
            quorumCompatibilityMode,
            evmConfiguration)
        .evmBuilder(MainnetEVMs::constantinople)
        .gasCalculator(PetersburgGasCalculator::new)
        .evmBuilder(MainnetEVMs::constantinople)
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::istanbul)
        .name("Agharta");
  }

  public static ProtocolSpecBuilder phoenixDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason,
      final OptionalLong ecip1017EraRounds,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    return aghartaDefinition(
            chainId,
            configContractSizeLimit,
            configStackSizeLimit,
            enableRevertReason,
            ecip1017EraRounds,
            quorumCompatibilityMode,
            evmConfiguration)
        .gasCalculator(IstanbulGasCalculator::new)
        .evmBuilder(
            (gasCalculator, evmConfig) ->
                MainnetEVMs.istanbul(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), evmConfiguration))
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::istanbul)
        .name("Phoenix");
  }

  public static ProtocolSpecBuilder thanosDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason,
      final OptionalLong ecip1017EraRounds,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    return phoenixDefinition(
            chainId,
            configContractSizeLimit,
            configStackSizeLimit,
            enableRevertReason,
            ecip1017EraRounds,
            quorumCompatibilityMode,
            evmConfiguration)
        .blockHeaderValidatorBuilder(
            feeMarket ->
                MainnetBlockHeaderValidator.createPgaBlockHeaderValidator(
                    new EpochCalculator.Ecip1099EpochCalculator(), powHasher(PowAlgorithm.ETHASH)))
        .ommerHeaderValidatorBuilder(
            feeMarket ->
                MainnetBlockHeaderValidator.createLegacyFeeMarketOmmerValidator(
                    new EpochCalculator.Ecip1099EpochCalculator(), powHasher(PowAlgorithm.ETHASH)))
        .name("Thanos");
  }

  private static TransactionReceipt byzantiumTransactionReceiptFactory(
      // ignored because it's always FRONTIER for byzantium
      final TransactionType __,
      final TransactionProcessingResult result,
      final WorldState worldState,
      final long gasUsed) {
    return new TransactionReceipt(
        result.isSuccessful() ? 1 : 0, gasUsed, result.getLogs(), Optional.empty());
  }

  private static TransactionReceipt byzantiumTransactionReceiptFactoryWithReasonEnabled(
      // ignored because it's always FRONTIER for byzantium
      final TransactionType __,
      final TransactionProcessingResult result,
      final WorldState worldState,
      final long gasUsed) {
    return new TransactionReceipt(
        result.isSuccessful() ? 1 : 0, gasUsed, result.getLogs(), result.getRevertReason());
  }

  public static ProtocolSpecBuilder ecip1049Definition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason,
      final OptionalLong ecip1017EraRounds,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    return thanosDefinition(
            chainId,
            configContractSizeLimit,
            configStackSizeLimit,
            enableRevertReason,
            ecip1017EraRounds,
            quorumCompatibilityMode,
            evmConfiguration)
        .blockHeaderValidatorBuilder(
            feeMarket ->
                MainnetBlockHeaderValidator.createPgaBlockHeaderValidator(
                    new EpochCalculator.Ecip1099EpochCalculator(),
                    powHasher(PowAlgorithm.KECCAK256)))
        .ommerHeaderValidatorBuilder(
            feeMarket ->
                MainnetBlockHeaderValidator.createLegacyFeeMarketOmmerValidator(
                    new EpochCalculator.Ecip1099EpochCalculator(),
                    powHasher(PowAlgorithm.KECCAK256)))
        .powHasher(powHasher(PowAlgorithm.KECCAK256))
        .name("ecip1049");
  }

  public static ProtocolSpecBuilder magnetoDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason,
      final OptionalLong ecip1017EraRounds,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    return thanosDefinition(
            chainId,
            configContractSizeLimit,
            configStackSizeLimit,
            enableRevertReason,
            ecip1017EraRounds,
            quorumCompatibilityMode,
            evmConfiguration)
        .gasCalculator(BerlinGasCalculator::new)
        .transactionValidatorBuilder(
            gasCalculator ->
                new MainnetTransactionValidator(
                    gasCalculator,
                    true,
                    chainId,
                    Set.of(TransactionType.FRONTIER, TransactionType.ACCESS_LIST),
                    quorumCompatibilityMode))
        .transactionReceiptFactory(
            enableRevertReason
                ? MainnetProtocolSpecs::berlinTransactionReceiptFactoryWithReasonEnabled
                : MainnetProtocolSpecs::berlinTransactionReceiptFactory)
        .name("Magneto");
  }

  public static ProtocolSpecBuilder mystiqueDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason,
      final OptionalLong ecip1017EraRounds,
      final boolean quorumCompatibilityMode,
      final EvmConfiguration evmConfiguration) {
    final int contractSizeLimit =
        configContractSizeLimit.orElse(MainnetProtocolSpecs.SPURIOUS_DRAGON_CONTRACT_SIZE_LIMIT);
    return magnetoDefinition(
            chainId,
            configContractSizeLimit,
            configStackSizeLimit,
            enableRevertReason,
            ecip1017EraRounds,
            quorumCompatibilityMode,
            evmConfiguration)
        .gasCalculator(LondonGasCalculator::new)
        .contractCreationProcessorBuilder(
            (gasCalculator, evm) ->
                new ContractCreationProcessor(
                    gasCalculator,
                    evm,
                    true,
                    List.of(MaxCodeSizeRule.of(contractSizeLimit), PrefixCodeRule.of()),
                    1))
        .name("Mystique");
  }
}
