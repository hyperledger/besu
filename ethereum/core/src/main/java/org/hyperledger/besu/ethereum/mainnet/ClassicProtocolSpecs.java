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

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.ethereum.core.fees.CoinbaseFeePriceCalculator;
import org.hyperledger.besu.ethereum.core.fees.TransactionPriceCalculator;
import org.hyperledger.besu.ethereum.mainnet.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;

public class ClassicProtocolSpecs {
  private static final Wei MAX_BLOCK_REWARD = Wei.fromEth(5);

  public static ProtocolSpecBuilder<Void> classicRecoveryInitDefinition(
      final OptionalInt contractSizeLimit, final OptionalInt configStackSizeLimit) {
    return MainnetProtocolSpecs.homesteadDefinition(contractSizeLimit, configStackSizeLimit)
        .blockHeaderValidatorBuilder(MainnetBlockHeaderValidator.createClassicValidator())
        .name("ClassicRecoveryInit");
  }

  public static ProtocolSpecBuilder<Void> tangerineWhistleDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit) {
    return MainnetProtocolSpecs.homesteadDefinition(contractSizeLimit, configStackSizeLimit)
        .gasCalculator(TangerineWhistleGasCalculator::new)
        .transactionValidatorBuilder(
            gasCalculator -> new MainnetTransactionValidator(gasCalculator, true, chainId))
        .name("ClassicTangerineWhistle");
  }

  public static ProtocolSpecBuilder<Void> dieHardDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit) {
    return tangerineWhistleDefinition(chainId, OptionalInt.empty(), configStackSizeLimit)
        .gasCalculator(DieHardGasCalculator::new)
        .difficultyCalculator(ClassicDifficultyCalculators.DIFFICULTY_BOMB_PAUSED)
        .name("DieHard");
  }

  public static ProtocolSpecBuilder<Void> gothamDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit) {
    return dieHardDefinition(chainId, contractSizeLimit, configStackSizeLimit)
        .blockReward(MAX_BLOCK_REWARD)
        .difficultyCalculator(ClassicDifficultyCalculators.DIFFICULTY_BOMB_DELAYED)
        .blockProcessorBuilder(
            (transactionProcessor,
                transactionReceiptFactory,
                blockReward,
                miningBeneficiaryCalculator,
                skipZeroBlockRewards,
                gasBudgetCalculator) ->
                new ClassicBlockProcessor(
                    transactionProcessor,
                    transactionReceiptFactory,
                    blockReward,
                    miningBeneficiaryCalculator,
                    skipZeroBlockRewards))
        .name("Gotham");
  }

  public static ProtocolSpecBuilder<Void> defuseDifficultyBombDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit) {
    return gothamDefinition(chainId, contractSizeLimit, configStackSizeLimit)
        .difficultyCalculator(ClassicDifficultyCalculators.DIFFICULTY_BOMB_REMOVED)
        .transactionValidatorBuilder(
            gasCalculator -> new MainnetTransactionValidator(gasCalculator, true, chainId))
        .name("DefuseDifficultyBomb");
  }

  public static ProtocolSpecBuilder<Void> atlantisDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason) {
    final int contractSizeLimit =
        configContractSizeLimit.orElse(MainnetProtocolSpecs.SPURIOUS_DRAGON_CONTRACT_SIZE_LIMIT);
    final int stackSizeLimit = configStackSizeLimit.orElse(MessageFrame.DEFAULT_MAX_STACK_SIZE);
    return gothamDefinition(chainId, configContractSizeLimit, configStackSizeLimit)
        .evmBuilder(MainnetEvmRegistries::byzantium)
        .gasCalculator(SpuriousDragonGasCalculator::new)
        .skipZeroBlockRewards(true)
        .messageCallProcessorBuilder(
            (evm, precompileContractRegistry) ->
                new MainnetMessageCallProcessor(evm, precompileContractRegistry))
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::byzantium)
        .difficultyCalculator(ClassicDifficultyCalculators.EIP100)
        .transactionReceiptFactory(
            enableRevertReason
                ? ClassicProtocolSpecs::byzantiumTransactionReceiptFactoryWithReasonEnabled
                : ClassicProtocolSpecs::byzantiumTransactionReceiptFactory)
        .contractCreationProcessorBuilder(
            (gasCalculator, evm) ->
                new MainnetContractCreationProcessor(
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
                    Account.DEFAULT_VERSION,
                    TransactionPriceCalculator.frontier(),
                    CoinbaseFeePriceCalculator.frontier()))
        .name("Atlantis");
  }

  public static ProtocolSpecBuilder<Void> aghartaDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason) {
    return atlantisDefinition(
            chainId, configContractSizeLimit, configStackSizeLimit, enableRevertReason)
        .evmBuilder(MainnetEvmRegistries::constantinople)
        .gasCalculator(ConstantinopleFixGasCalculator::new)
        .evmBuilder(gasCalculator -> MainnetEvmRegistries.constantinople(gasCalculator))
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::istanbul)
        .name("Agharta");
  }

  public static ProtocolSpecBuilder<Void> phoenixDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason) {
    return aghartaDefinition(
            chainId, configContractSizeLimit, configStackSizeLimit, enableRevertReason)
        .gasCalculator(IstanbulGasCalculator::new)
        .evmBuilder(
            gasCalculator ->
                MainnetEvmRegistries.istanbul(gasCalculator, chainId.orElse(BigInteger.ZERO)))
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::istanbul)
        .name("Phoenix");
  }

  private static TransactionReceipt byzantiumTransactionReceiptFactory(
      final TransactionProcessor.Result result, final WorldState worldState, final long gasUsed) {
    return new TransactionReceipt(
        result.isSuccessful() ? 1 : 0, gasUsed, result.getLogs(), Optional.empty());
  }

  private static TransactionReceipt byzantiumTransactionReceiptFactoryWithReasonEnabled(
      final TransactionProcessor.Result result, final WorldState worldState, final long gasUsed) {
    return new TransactionReceipt(
        result.isSuccessful() ? 1 : 0, gasUsed, result.getLogs(), result.getRevertReason());
  }
}
