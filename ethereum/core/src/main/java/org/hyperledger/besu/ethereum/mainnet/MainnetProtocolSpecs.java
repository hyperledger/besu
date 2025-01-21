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

import static org.hyperledger.besu.ethereum.mainnet.requests.MainnetRequestsProcessor.pragueRequestsProcessors;

import org.hyperledger.besu.config.BlobScheduleOptions;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.PowAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.MainnetBlockValidator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.core.feemarket.CoinbaseFeePriceCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder.BlockValidatorBuilder;
import org.hyperledger.besu.ethereum.mainnet.blockhash.CancunBlockHashProcessor;
import org.hyperledger.besu.ethereum.mainnet.blockhash.FrontierBlockHashProcessor;
import org.hyperledger.besu.ethereum.mainnet.blockhash.PragueBlockHashProcessor;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.mainnet.parallelization.MainnetParallelBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.requests.MainnetRequestsValidator;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestContractAddresses;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionValidator;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.contractvalidation.EOFValidationCodeRule;
import org.hyperledger.besu.evm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.evm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ByzantiumGasCalculator;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.gascalculator.FrontierGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.gascalculator.HomesteadGasCalculator;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.OsakaGasCalculator;
import org.hyperledger.besu.evm.gascalculator.PetersburgGasCalculator;
import org.hyperledger.besu.evm.gascalculator.PragueGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ShanghaiGasCalculator;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.TangerineWhistleGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.io.Resources;
import io.vertx.core.json.JsonArray;

/** Provides the various {@link ProtocolSpec}s on mainnet hard forks. */
public abstract class MainnetProtocolSpecs {

  private static final Address RIPEMD160_PRECOMPILE =
      Address.fromHexString("0x0000000000000000000000000000000000000003");

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  // A consensus bug at Ethereum mainnet transaction 0xcf416c53
  // deleted an empty account even when the message execution scope
  // failed, but the transaction itself succeeded.
  private static final Set<Address> SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES =
      Set.of(RIPEMD160_PRECOMPILE);

  private static final Wei FRONTIER_BLOCK_REWARD = Wei.fromEth(5);

  private static final Wei BYZANTIUM_BLOCK_REWARD = Wei.fromEth(3);

  private static final Wei CONSTANTINOPLE_BLOCK_REWARD = Wei.fromEth(2);

  private MainnetProtocolSpecs() {}

  public static ProtocolSpecBuilder frontierDefinition(
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return new ProtocolSpecBuilder()
        .gasCalculator(FrontierGasCalculator::new)
        .gasLimitCalculatorBuilder(feeMarket -> new FrontierTargetingGasLimitCalculator())
        .evmBuilder(MainnetEVMs::frontier)
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::frontier)
        .messageCallProcessorBuilder(MessageCallProcessor::new)
        .contractCreationProcessorBuilder(
            evm ->
                new ContractCreationProcessor(
                    evm, false, Collections.singletonList(MaxCodeSizeRule.from(evm)), 0))
        .transactionValidatorFactoryBuilder(
            (evm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    evm.getGasCalculator(), gasLimitCalculator, false, Optional.empty()))
        .transactionProcessorBuilder(
            (gasCalculator,
                feeMarket,
                transactionValidatorFactory,
                contractCreationProcessor,
                messageCallProcessor) ->
                MainnetTransactionProcessor.builder()
                    .gasCalculator(gasCalculator)
                    .transactionValidatorFactory(transactionValidatorFactory)
                    .contractCreationProcessor(contractCreationProcessor)
                    .messageCallProcessor(messageCallProcessor)
                    .clearEmptyAccounts(false)
                    .warmCoinbase(false)
                    .maxStackSize(evmConfiguration.evmStackSize())
                    .feeMarket(FeeMarket.legacy())
                    .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.frontier())
                    .build())
        .privateTransactionProcessorBuilder(
            (transactionValidatorFactory,
                contractCreationProcessor,
                messageCallProcessor,
                privateTransactionValidator) ->
                new PrivateTransactionProcessor(
                    transactionValidatorFactory,
                    contractCreationProcessor,
                    messageCallProcessor,
                    false,
                    evmConfiguration.evmStackSize(),
                    new PrivateTransactionValidator(Optional.empty())))
        .difficultyCalculator(MainnetDifficultyCalculators.FRONTIER)
        .blockHeaderValidatorBuilder(feeMarket -> MainnetBlockHeaderValidator.create())
        .ommerHeaderValidatorBuilder(
            feeMarket -> MainnetBlockHeaderValidator.createLegacyFeeMarketOmmerValidator())
        .blockBodyValidatorBuilder(MainnetBlockBodyValidator::new)
        .transactionReceiptFactory(MainnetProtocolSpecs::frontierTransactionReceiptFactory)
        .blockReward(FRONTIER_BLOCK_REWARD)
        .skipZeroBlockRewards(false)
        .blockProcessorBuilder(
            isParallelTxProcessingEnabled
                ? new MainnetParallelBlockProcessor.ParallelBlockProcessorBuilder(metricsSystem)
                : MainnetBlockProcessor::new)
        .blockValidatorBuilder(MainnetProtocolSpecs.blockValidatorBuilder())
        .blockImporterBuilder(MainnetBlockImporter::new)
        .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
        .miningBeneficiaryCalculator(BlockHeader::getCoinbase)
        .evmConfiguration(evmConfiguration)
        .blockHashProcessor(new FrontierBlockHashProcessor())
        .name("Frontier");
  }

  public static PoWHasher powHasher(final PowAlgorithm powAlgorithm) {
    if (powAlgorithm == null) {
      return PoWHasher.UNSUPPORTED;
    }
    return powAlgorithm == PowAlgorithm.ETHASH ? PoWHasher.ETHASH_LIGHT : PoWHasher.UNSUPPORTED;
  }

  public static BlockValidatorBuilder blockValidatorBuilder() {
    return MainnetBlockValidator::new;
  }

  public static ProtocolSpecBuilder homesteadDefinition(
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return frontierDefinition(evmConfiguration, isParallelTxProcessingEnabled, metricsSystem)
        .gasCalculator(HomesteadGasCalculator::new)
        .evmBuilder(MainnetEVMs::homestead)
        .contractCreationProcessorBuilder(
            evm ->
                new ContractCreationProcessor(
                    evm, true, Collections.singletonList(MaxCodeSizeRule.from(evm)), 0))
        .transactionValidatorFactoryBuilder(
            (evm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    evm.getGasCalculator(), gasLimitCalculator, true, Optional.empty()))
        .difficultyCalculator(MainnetDifficultyCalculators.HOMESTEAD)
        .name("Homestead");
  }

  public static ProtocolSpecBuilder daoRecoveryInitDefinition(
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return homesteadDefinition(evmConfiguration, isParallelTxProcessingEnabled, metricsSystem)
        .blockHeaderValidatorBuilder(feeMarket -> MainnetBlockHeaderValidator.createDaoValidator())
        .blockProcessorBuilder(
            (transactionProcessor,
                transactionReceiptFactory,
                blockReward,
                miningBeneficiaryCalculator,
                skipZeroBlockRewards,
                protocolSchedule) ->
                new DaoBlockProcessor(
                    isParallelTxProcessingEnabled
                        ? new MainnetParallelBlockProcessor(
                            transactionProcessor,
                            transactionReceiptFactory,
                            blockReward,
                            miningBeneficiaryCalculator,
                            skipZeroBlockRewards,
                            protocolSchedule,
                            metricsSystem)
                        : new MainnetBlockProcessor(
                            transactionProcessor,
                            transactionReceiptFactory,
                            blockReward,
                            miningBeneficiaryCalculator,
                            skipZeroBlockRewards,
                            protocolSchedule)))
        .name("DaoRecoveryInit");
  }

  public static ProtocolSpecBuilder daoRecoveryTransitionDefinition(
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return daoRecoveryInitDefinition(evmConfiguration, isParallelTxProcessingEnabled, metricsSystem)
        .blockProcessorBuilder(
            isParallelTxProcessingEnabled
                ? new MainnetParallelBlockProcessor.ParallelBlockProcessorBuilder(metricsSystem)
                : MainnetBlockProcessor::new)
        .name("DaoRecoveryTransition");
  }

  public static ProtocolSpecBuilder tangerineWhistleDefinition(
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return homesteadDefinition(evmConfiguration, isParallelTxProcessingEnabled, metricsSystem)
        .gasCalculator(TangerineWhistleGasCalculator::new)
        .name("TangerineWhistle");
  }

  public static ProtocolSpecBuilder spuriousDragonDefinition(
      final Optional<BigInteger> chainId,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return tangerineWhistleDefinition(
            evmConfiguration, isParallelTxProcessingEnabled, metricsSystem)
        .isReplayProtectionSupported(true)
        .gasCalculator(SpuriousDragonGasCalculator::new)
        .skipZeroBlockRewards(true)
        .messageCallProcessorBuilder(
            (evm, precompileContractRegistry) ->
                new MessageCallProcessor(
                    evm,
                    precompileContractRegistry,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES))
        .contractCreationProcessorBuilder(
            evm ->
                new ContractCreationProcessor(
                    evm,
                    true,
                    Collections.singletonList(MaxCodeSizeRule.from(evm)),
                    1,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES))
        .transactionValidatorFactoryBuilder(
            (evm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    evm.getGasCalculator(), gasLimitCalculator, true, chainId))
        .transactionProcessorBuilder(
            (gasCalculator,
                feeMarket,
                transactionValidator,
                contractCreationProcessor,
                messageCallProcessor) ->
                MainnetTransactionProcessor.builder()
                    .gasCalculator(gasCalculator)
                    .transactionValidatorFactory(transactionValidator)
                    .contractCreationProcessor(contractCreationProcessor)
                    .messageCallProcessor(messageCallProcessor)
                    .clearEmptyAccounts(true)
                    .warmCoinbase(false)
                    .maxStackSize(evmConfiguration.evmStackSize())
                    .feeMarket(feeMarket)
                    .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.frontier())
                    .build())
        .name("SpuriousDragon");
  }

  public static ProtocolSpecBuilder byzantiumDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return spuriousDragonDefinition(
            chainId, evmConfiguration, isParallelTxProcessingEnabled, metricsSystem)
        .gasCalculator(ByzantiumGasCalculator::new)
        .evmBuilder(MainnetEVMs::byzantium)
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::byzantium)
        .difficultyCalculator(MainnetDifficultyCalculators.BYZANTIUM)
        .transactionReceiptFactory(
            enableRevertReason
                ? MainnetProtocolSpecs::byzantiumTransactionReceiptFactoryWithReasonEnabled
                : MainnetProtocolSpecs::byzantiumTransactionReceiptFactory)
        .blockReward(BYZANTIUM_BLOCK_REWARD)
        .privateTransactionValidatorBuilder(() -> new PrivateTransactionValidator(chainId))
        .privateTransactionProcessorBuilder(
            (transactionValidatorFactory,
                contractCreationProcessor,
                messageCallProcessor,
                privateTransactionValidator) ->
                new PrivateTransactionProcessor(
                    transactionValidatorFactory,
                    contractCreationProcessor,
                    messageCallProcessor,
                    false,
                    evmConfiguration.evmStackSize(),
                    privateTransactionValidator))
        .name("Byzantium");
  }

  public static ProtocolSpecBuilder constantinopleDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return byzantiumDefinition(
            chainId,
            enableRevertReason,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem)
        .difficultyCalculator(MainnetDifficultyCalculators.CONSTANTINOPLE)
        .gasCalculator(ConstantinopleGasCalculator::new)
        .evmBuilder(MainnetEVMs::constantinople)
        .blockReward(CONSTANTINOPLE_BLOCK_REWARD)
        .name("Constantinople");
  }

  public static ProtocolSpecBuilder petersburgDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return constantinopleDefinition(
            chainId,
            enableRevertReason,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem)
        .gasCalculator(PetersburgGasCalculator::new)
        .name("Petersburg");
  }

  public static ProtocolSpecBuilder istanbulDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return petersburgDefinition(
            chainId,
            enableRevertReason,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem)
        .gasCalculator(IstanbulGasCalculator::new)
        .evmBuilder(
            (gasCalculator, jdCacheConfig) ->
                MainnetEVMs.istanbul(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), evmConfiguration))
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::istanbul)
        .contractCreationProcessorBuilder(
            evm ->
                new ContractCreationProcessor(
                    evm,
                    true,
                    Collections.singletonList(MaxCodeSizeRule.from(evm)),
                    1,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES))
        .name("Istanbul");
  }

  static ProtocolSpecBuilder muirGlacierDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return istanbulDefinition(
            chainId,
            enableRevertReason,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem)
        .difficultyCalculator(MainnetDifficultyCalculators.MUIR_GLACIER)
        .name("MuirGlacier");
  }

  static ProtocolSpecBuilder berlinDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return muirGlacierDefinition(
            chainId,
            enableRevertReason,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem)
        .gasCalculator(BerlinGasCalculator::new)
        .transactionValidatorFactoryBuilder(
            (evm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    evm.getGasCalculator(),
                    gasLimitCalculator,
                    true,
                    chainId,
                    Set.of(TransactionType.FRONTIER, TransactionType.ACCESS_LIST)))
        .transactionReceiptFactory(
            enableRevertReason
                ? MainnetProtocolSpecs::berlinTransactionReceiptFactoryWithReasonEnabled
                : MainnetProtocolSpecs::berlinTransactionReceiptFactory)
        .name("Berlin");
  }

  static ProtocolSpecBuilder londonDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    final long londonForkBlockNumber =
        genesisConfigOptions.getLondonBlockNumber().orElse(Long.MAX_VALUE);
    final BaseFeeMarket londonFeeMarket;
    if (genesisConfigOptions.isZeroBaseFee()) {
      londonFeeMarket = FeeMarket.zeroBaseFee(londonForkBlockNumber);
    } else if (genesisConfigOptions.isFixedBaseFee()) {
      londonFeeMarket =
          FeeMarket.fixedBaseFee(
              londonForkBlockNumber, miningConfiguration.getMinTransactionGasPrice());
    } else {
      londonFeeMarket =
          FeeMarket.london(londonForkBlockNumber, genesisConfigOptions.getBaseFeePerGas());
    }
    return berlinDefinition(
            chainId,
            enableRevertReason,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem)
        .feeMarket(londonFeeMarket)
        .gasCalculator(LondonGasCalculator::new)
        .gasLimitCalculatorBuilder(
            feeMarket ->
                new LondonTargetingGasLimitCalculator(
                    londonForkBlockNumber, (BaseFeeMarket) feeMarket))
        .transactionValidatorFactoryBuilder(
            (evm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    evm.getGasCalculator(),
                    gasLimitCalculator,
                    feeMarket,
                    true,
                    chainId,
                    Set.of(
                        TransactionType.FRONTIER,
                        TransactionType.ACCESS_LIST,
                        TransactionType.EIP1559),
                    Integer.MAX_VALUE))
        .transactionProcessorBuilder(
            (gasCalculator,
                feeMarket,
                transactionValidatorFactory,
                contractCreationProcessor,
                messageCallProcessor) ->
                MainnetTransactionProcessor.builder()
                    .gasCalculator(gasCalculator)
                    .transactionValidatorFactory(transactionValidatorFactory)
                    .contractCreationProcessor(contractCreationProcessor)
                    .messageCallProcessor(messageCallProcessor)
                    .clearEmptyAccounts(true)
                    .warmCoinbase(false)
                    .maxStackSize(evmConfiguration.evmStackSize())
                    .feeMarket(feeMarket)
                    .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.eip1559())
                    .build())
        .contractCreationProcessorBuilder(
            evm ->
                new ContractCreationProcessor(
                    evm,
                    true,
                    List.of(MaxCodeSizeRule.from(evm), PrefixCodeRule.of()),
                    1,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES))
        .evmBuilder(
            (gasCalculator, jdCacheConfig) ->
                MainnetEVMs.london(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), evmConfiguration))
        .difficultyCalculator(MainnetDifficultyCalculators.LONDON)
        .blockHeaderValidatorBuilder(
            feeMarket ->
                MainnetBlockHeaderValidator.createBaseFeeMarketValidator((BaseFeeMarket) feeMarket))
        .ommerHeaderValidatorBuilder(
            feeMarket ->
                MainnetBlockHeaderValidator.createBaseFeeMarketOmmerValidator(
                    (BaseFeeMarket) feeMarket))
        .blockBodyValidatorBuilder(BaseFeeBlockBodyValidator::new)
        .name("London");
  }

  static ProtocolSpecBuilder arrowGlacierDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return londonDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem)
        .difficultyCalculator(MainnetDifficultyCalculators.ARROW_GLACIER)
        .name("ArrowGlacier");
  }

  static ProtocolSpecBuilder grayGlacierDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return arrowGlacierDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem)
        .difficultyCalculator(MainnetDifficultyCalculators.GRAY_GLACIER)
        .name("GrayGlacier");
  }

  static ProtocolSpecBuilder parisDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {

    return grayGlacierDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem)
        .evmBuilder(
            (gasCalculator, jdCacheConfig) ->
                MainnetEVMs.paris(gasCalculator, chainId.orElse(BigInteger.ZERO), evmConfiguration))
        .difficultyCalculator(MainnetDifficultyCalculators.PROOF_OF_STAKE_DIFFICULTY)
        .blockHeaderValidatorBuilder(MainnetBlockHeaderValidator::mergeBlockHeaderValidator)
        .blockReward(Wei.ZERO)
        .skipZeroBlockRewards(true)
        .isPoS(true)
        .name("ParisFork");
  }

  static ProtocolSpecBuilder shanghaiDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return parisDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem)
        // gas calculator has new code to support EIP-3860 limit and meter initcode
        .gasCalculator(ShanghaiGasCalculator::new)
        // EVM has a new operation for EIP-3855 PUSH0 instruction
        .evmBuilder(
            (gasCalculator, jdCacheConfig) ->
                MainnetEVMs.shanghai(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), evmConfiguration))
        // we need to flip the Warm Coinbase flag for EIP-3651 warm coinbase
        .transactionProcessorBuilder(
            (gasCalculator,
                feeMarket,
                transactionValidatorFactory,
                contractCreationProcessor,
                messageCallProcessor) ->
                MainnetTransactionProcessor.builder()
                    .gasCalculator(gasCalculator)
                    .transactionValidatorFactory(transactionValidatorFactory)
                    .contractCreationProcessor(contractCreationProcessor)
                    .messageCallProcessor(messageCallProcessor)
                    .clearEmptyAccounts(true)
                    .warmCoinbase(true)
                    .maxStackSize(evmConfiguration.evmStackSize())
                    .feeMarket(feeMarket)
                    .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.eip1559())
                    .build())
        // Contract creation rules for EIP-3860 Limit and meter intitcode
        .transactionValidatorFactoryBuilder(
            (evm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    evm.getGasCalculator(),
                    gasLimitCalculator,
                    feeMarket,
                    true,
                    chainId,
                    Set.of(
                        TransactionType.FRONTIER,
                        TransactionType.ACCESS_LIST,
                        TransactionType.EIP1559),
                    evm.getMaxInitcodeSize()))
        .withdrawalsProcessor(new WithdrawalsProcessor())
        .withdrawalsValidator(new WithdrawalsValidator.AllowedWithdrawals())
        .name("Shanghai");
  }

  static ProtocolSpecBuilder cancunDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    final long londonForkBlockNumber = genesisConfigOptions.getLondonBlockNumber().orElse(0L);

    final var cancunBlobSchedule =
        genesisConfigOptions
            .getBlobScheduleOptions()
            .flatMap(BlobScheduleOptions::getCancun)
            .orElse(BlobScheduleOptions.BlobSchedule.CANCUN_DEFAULT);

    final BaseFeeMarket cancunFeeMarket;
    if (genesisConfigOptions.isZeroBaseFee()) {
      cancunFeeMarket = FeeMarket.zeroBaseFee(londonForkBlockNumber);
    } else if (genesisConfigOptions.isFixedBaseFee()) {
      cancunFeeMarket =
          FeeMarket.fixedBaseFee(
              londonForkBlockNumber, miningConfiguration.getMinTransactionGasPrice());
    } else {
      cancunFeeMarket =
          FeeMarket.cancun(
              londonForkBlockNumber,
              genesisConfigOptions.getBaseFeePerGas(),
              cancunBlobSchedule.getBaseFeeUpdateFraction());
    }

    final java.util.function.Supplier<GasCalculator> cancunGasCalcSupplier =
        () -> new CancunGasCalculator(cancunBlobSchedule.getTarget());

    return shanghaiDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem)
        .feeMarket(cancunFeeMarket)
        // gas calculator for EIP-4844 blob gas
        .gasCalculator(cancunGasCalcSupplier)
        // gas limit with EIP-4844 max blob gas per block
        .gasLimitCalculatorBuilder(
            feeMarket ->
                new CancunTargetingGasLimitCalculator(
                    londonForkBlockNumber, (BaseFeeMarket) feeMarket, cancunBlobSchedule.getMax()))
        // EVM changes to support EIP-1153: TSTORE and EIP-5656: MCOPY
        .evmBuilder(
            (gasCalculator, jdCacheConfig) ->
                MainnetEVMs.cancun(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), evmConfiguration))
        // use Cancun fee market
        .transactionProcessorBuilder(
            (gasCalculator,
                feeMarket,
                transactionValidator,
                contractCreationProcessor,
                messageCallProcessor) ->
                MainnetTransactionProcessor.builder()
                    .gasCalculator(gasCalculator)
                    .transactionValidatorFactory(transactionValidator)
                    .contractCreationProcessor(contractCreationProcessor)
                    .messageCallProcessor(messageCallProcessor)
                    .clearEmptyAccounts(true)
                    .warmCoinbase(true)
                    .maxStackSize(evmConfiguration.evmStackSize())
                    .feeMarket(feeMarket)
                    .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.eip1559())
                    .codeDelegationProcessor(
                        new CodeDelegationProcessor(
                            chainId, SIGNATURE_ALGORITHM.get().getHalfCurveOrder()))
                    .build())
        // change to check for max blob gas per block for EIP-4844
        .transactionValidatorFactoryBuilder(
            (evm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    evm.getGasCalculator(),
                    gasLimitCalculator,
                    feeMarket,
                    true,
                    chainId,
                    Set.of(
                        TransactionType.FRONTIER,
                        TransactionType.ACCESS_LIST,
                        TransactionType.EIP1559,
                        TransactionType.BLOB),
                    evm.getMaxInitcodeSize()))
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::cancun)
        .blockHeaderValidatorBuilder(
            fm ->
                MainnetBlockHeaderValidator.blobAwareBlockHeaderValidator(
                    fm, cancunGasCalcSupplier))
        .blockHashProcessor(new CancunBlockHashProcessor())
        .name("Cancun");
  }

  static ProtocolSpecBuilder cancunEOFDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {

    final var cancunBlobSchedule =
        genesisConfigOptions
            .getBlobScheduleOptions()
            .flatMap(BlobScheduleOptions::getCancun)
            .orElse(BlobScheduleOptions.BlobSchedule.CANCUN_DEFAULT);

    ProtocolSpecBuilder protocolSpecBuilder =
        cancunDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem);
    return addEOF(
            genesisConfigOptions,
            chainId,
            evmConfiguration,
            protocolSpecBuilder,
            cancunBlobSchedule.getTarget(),
            cancunBlobSchedule.getMax())
        .name("CancunEOF");
  }

  static ProtocolSpecBuilder pragueDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {

    RequestContractAddresses requestContractAddresses =
        RequestContractAddresses.fromGenesis(genesisConfigOptions);

    final long londonForkBlockNumber = genesisConfigOptions.getLondonBlockNumber().orElse(0L);
    final var pragueBlobSchedule =
        genesisConfigOptions
            .getBlobScheduleOptions()
            .flatMap(BlobScheduleOptions::getPrague)
            .orElse(BlobScheduleOptions.BlobSchedule.PRAGUE_DEFAULT);

    // EIP-3074 AUTH and AUTHCALL gas | EIP-7840 Blob schedule | EIP-7691 6/9 blob increase
    final java.util.function.Supplier<GasCalculator> pragueGasCalcSupplier =
        () -> new PragueGasCalculator(pragueBlobSchedule.getTarget());

    final BaseFeeMarket pragueFeeMarket;
    if (genesisConfigOptions.isZeroBaseFee()) {
      pragueFeeMarket = FeeMarket.zeroBaseFee(londonForkBlockNumber);
    } else if (genesisConfigOptions.isFixedBaseFee()) {
      pragueFeeMarket =
          FeeMarket.fixedBaseFee(
              londonForkBlockNumber, miningConfiguration.getMinTransactionGasPrice());
    } else {
      pragueFeeMarket =
          FeeMarket.prague(
              londonForkBlockNumber,
              genesisConfigOptions.getBaseFeePerGas(),
              pragueBlobSchedule.getBaseFeeUpdateFraction());
    }

    return cancunDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem)
        .feeMarket(pragueFeeMarket)
        .gasCalculator(pragueGasCalcSupplier)
        // EIP-7840 Blob schedule | EIP-7691 6/9 blob increase
        .gasLimitCalculatorBuilder(
            feeMarket ->
                new PragueTargetingGasLimitCalculator(
                    londonForkBlockNumber, (BaseFeeMarket) feeMarket, pragueBlobSchedule.getMax()))
        // EIP-3074 AUTH and AUTHCALL
        .evmBuilder(
            (gasCalculator, jdCacheConfig) ->
                MainnetEVMs.prague(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), evmConfiguration))

        // EIP-2537 BLS12-381 precompiles
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::prague)

        // EIP-7002 Withdrawals / EIP-6610 Deposits / EIP-7685 Requests
        .requestsValidator(new MainnetRequestsValidator())
        // EIP-7002 Withdrawals / EIP-6610 Deposits / EIP-7685 Requests
        .requestProcessorCoordinator(pragueRequestsProcessors(requestContractAddresses))

        // change to accept EIP-7702 transactions
        .transactionValidatorFactoryBuilder(
            (evm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    evm.getGasCalculator(),
                    gasLimitCalculator,
                    feeMarket,
                    true,
                    chainId,
                    Set.of(
                        TransactionType.FRONTIER,
                        TransactionType.ACCESS_LIST,
                        TransactionType.EIP1559,
                        TransactionType.BLOB,
                        TransactionType.DELEGATE_CODE),
                    evm.getMaxInitcodeSize()))

        // TODO SLD EIP-7840 Can we dynamically wire in the appropriate GasCalculator instead of
        // overriding
        // blockHeaderValidatorBuilder every time the GasCalculator changes?
        // EIP-7840 blob schedule | EIP-7691 6/9 blob increase
        .blockHeaderValidatorBuilder(
            fm ->
                MainnetBlockHeaderValidator.blobAwareBlockHeaderValidator(
                    fm, pragueGasCalcSupplier))
        // EIP-2935 Blockhash processor
        .blockHashProcessor(new PragueBlockHashProcessor())
        .name("Prague");
  }

  static ProtocolSpecBuilder osakaDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {

    final var osakaBlobSchedule =
        genesisConfigOptions
            .getBlobScheduleOptions()
            .flatMap(BlobScheduleOptions::getOsaka)
            .orElse(BlobScheduleOptions.BlobSchedule.OSAKA_DEFAULT);

    ProtocolSpecBuilder protocolSpecBuilder =
        pragueDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem);
    return addEOF(
            genesisConfigOptions,
            chainId,
            evmConfiguration,
            protocolSpecBuilder,
            osakaBlobSchedule.getTarget(),
            osakaBlobSchedule.getMax())
        .name("Osaka");
  }

  private static ProtocolSpecBuilder addEOF(
      final GenesisConfigOptions genesisConfigOptions,
      final Optional<BigInteger> chainId,
      final EvmConfiguration evmConfiguration,
      final ProtocolSpecBuilder protocolSpecBuilder,
      final int targetBlobsPerBlock,
      final int maxBlobsPerBlock) {

    final long londonForkBlockNumber = genesisConfigOptions.getLondonBlockNumber().orElse(0L);
    final java.util.function.Supplier<GasCalculator> osakaGasCalcSupplier =
        () -> new OsakaGasCalculator(targetBlobsPerBlock);
    return protocolSpecBuilder
        // EIP-7692 EOF v1 Gas calculator
        .gasCalculator(osakaGasCalcSupplier)
        .gasLimitCalculatorBuilder(
            feeMarket ->
                new OsakaTargetingGasLimitCalculator(
                    londonForkBlockNumber, (BaseFeeMarket) feeMarket, maxBlobsPerBlock))
        // EIP-7692 EOF v1 EVM and opcodes
        .evmBuilder(
            (gasCalculator, jdCacheConfig) ->
                MainnetEVMs.osaka(gasCalculator, chainId.orElse(BigInteger.ZERO), evmConfiguration))
        // EIP-7698 EOF v1 creation transaction
        .contractCreationProcessorBuilder(
            evm ->
                new ContractCreationProcessor(
                    evm,
                    true,
                    List.of(MaxCodeSizeRule.from(evm), EOFValidationCodeRule.from(evm)),
                    1,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES))
        .blockHeaderValidatorBuilder(
            fm ->
                MainnetBlockHeaderValidator.blobAwareBlockHeaderValidator(
                    fm, osakaGasCalcSupplier));
  }

  static ProtocolSpecBuilder futureEipsDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    return osakaDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem)
        // Use Future EIP configured EVM
        .evmBuilder(
            (gasCalculator, jdCacheConfig) ->
                MainnetEVMs.futureEips(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), evmConfiguration))
        // change contract call creator to accept EOF code
        .contractCreationProcessorBuilder(
            evm ->
                new ContractCreationProcessor(
                    evm,
                    true,
                    List.of(MaxCodeSizeRule.from(evm), EOFValidationCodeRule.from(evm)),
                    1,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES))
        // use future configured precompiled contracts
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::futureEips)
        .name("FutureEips");
  }

  static ProtocolSpecBuilder experimentalEipsDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {

    return futureEipsDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            miningConfiguration,
            isParallelTxProcessingEnabled,
            metricsSystem)
        .evmBuilder(
            (gasCalculator, jdCacheConfig) ->
                MainnetEVMs.experimentalEips(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), evmConfiguration))
        .name("ExperimentalEips");
  }

  private static TransactionReceipt frontierTransactionReceiptFactory(
      // ignored because it's always FRONTIER
      final TransactionType __,
      final TransactionProcessingResult result,
      final WorldState worldState,
      final long gasUsed) {
    return new TransactionReceipt(
        worldState.frontierRootHash(),
        gasUsed,
        result.getLogs(),
        Optional.empty()); // No revert reason in frontier
  }

  private static TransactionReceipt byzantiumTransactionReceiptFactory(
      // ignored because it's always FRONTIER
      final TransactionType __,
      final TransactionProcessingResult result,
      final WorldState worldState,
      final long gasUsed) {
    return new TransactionReceipt(
        result.isSuccessful() ? 1 : 0, gasUsed, result.getLogs(), Optional.empty());
  }

  private static TransactionReceipt byzantiumTransactionReceiptFactoryWithReasonEnabled(
      // ignored because it's always FRONTIER
      final TransactionType __,
      final TransactionProcessingResult result,
      final WorldState worldState,
      final long gasUsed) {
    return new TransactionReceipt(
        result.isSuccessful() ? 1 : 0, gasUsed, result.getLogs(), result.getRevertReason());
  }

  static TransactionReceipt berlinTransactionReceiptFactory(
      final TransactionType transactionType,
      final TransactionProcessingResult transactionProcessingResult,
      final WorldState worldState,
      final long gasUsed) {
    return new TransactionReceipt(
        transactionType,
        transactionProcessingResult.isSuccessful() ? 1 : 0,
        gasUsed,
        transactionProcessingResult.getLogs(),
        Optional.empty());
  }

  static TransactionReceipt berlinTransactionReceiptFactoryWithReasonEnabled(
      final TransactionType transactionType,
      final TransactionProcessingResult transactionProcessingResult,
      final WorldState worldState,
      final long gasUsed) {
    return new TransactionReceipt(
        transactionType,
        transactionProcessingResult.isSuccessful() ? 1 : 0,
        gasUsed,
        transactionProcessingResult.getLogs(),
        transactionProcessingResult.getRevertReason());
  }

  private record DaoBlockProcessor(BlockProcessor wrapped) implements BlockProcessor {

    @Override
    public BlockProcessingResult processBlock(
        final Blockchain blockchain,
        final MutableWorldState worldState,
        final BlockHeader blockHeader,
        final List<Transaction> transactions,
        final List<BlockHeader> ommers,
        final Optional<List<Withdrawal>> withdrawals,
        final PrivateMetadataUpdater privateMetadataUpdater) {
      updateWorldStateForDao(worldState);
      return wrapped.processBlock(
          blockchain,
          worldState,
          blockHeader,
          transactions,
          ommers,
          withdrawals,
          privateMetadataUpdater);
    }

    private static final Address DAO_REFUND_CONTRACT_ADDRESS =
        Address.fromHexString("0xbf4ed7b27f1d666546e30d74d50d173d20bca754");

    private void updateWorldStateForDao(final MutableWorldState worldState) {
      try {
        final JsonArray json =
            new JsonArray(
                Resources.toString(
                    Objects.requireNonNull(this.getClass().getResource("/daoAddresses.json")),
                    StandardCharsets.UTF_8));
        final List<Address> addresses =
            IntStream.range(0, json.size())
                .mapToObj(json::getString)
                .map(Address::fromHexString)
                .toList();
        final WorldUpdater worldUpdater = worldState.updater();
        final MutableAccount daoRefundContract =
            worldUpdater.getOrCreate(DAO_REFUND_CONTRACT_ADDRESS);
        for (final Address address : addresses) {
          final MutableAccount account = worldUpdater.getOrCreate(address);
          final Wei balance = account.getBalance();
          account.decrementBalance(balance);
          daoRefundContract.incrementBalance(balance);
        }
        worldUpdater.commit();
      } catch (final IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
