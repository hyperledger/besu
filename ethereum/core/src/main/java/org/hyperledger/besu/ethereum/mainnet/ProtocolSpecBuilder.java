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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.DEFAULT_PRIVACY;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.PLUGIN_PRIVACY;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.blockhash.BlockHashProcessor;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.FlexiblePrivacyPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.PrivacyPluginPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.PrivacyPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.requests.ProhibitedRequestValidator;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestProcessorCoordinator;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestsValidator;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionValidator;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class ProtocolSpecBuilder {
  private Supplier<GasCalculator> gasCalculatorBuilder;
  private Function<FeeMarket, GasLimitCalculator> gasLimitCalculatorBuilder;
  private Wei blockReward;
  private boolean skipZeroBlockRewards;

  private BlockHeaderFunctions blockHeaderFunctions;
  private AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  private DifficultyCalculator difficultyCalculator;
  private EvmConfiguration evmConfiguration;
  private BiFunction<GasCalculator, EvmConfiguration, EVM> evmBuilder;
  private TransactionValidatorFactoryBuilder transactionValidatorFactoryBuilder;
  private Function<FeeMarket, BlockHeaderValidator.Builder> blockHeaderValidatorBuilder;
  private Function<FeeMarket, BlockHeaderValidator.Builder> ommerHeaderValidatorBuilder;
  private Function<ProtocolSchedule, BlockBodyValidator> blockBodyValidatorBuilder;
  private Function<EVM, ContractCreationProcessor> contractCreationProcessorBuilder;
  private Function<PrecompiledContractConfiguration, PrecompileContractRegistry>
      precompileContractRegistryBuilder;
  private BiFunction<EVM, PrecompileContractRegistry, MessageCallProcessor>
      messageCallProcessorBuilder;
  private TransactionProcessorBuilder transactionProcessorBuilder;

  private BlockProcessorBuilder blockProcessorBuilder;
  private BlockValidatorBuilder blockValidatorBuilder;
  private BlockImporterBuilder blockImporterBuilder;

  private String name;
  private MiningBeneficiaryCalculator miningBeneficiaryCalculator;
  private PrivacyParameters privacyParameters;
  private PrivateTransactionProcessorBuilder privateTransactionProcessorBuilder;
  private PrivateTransactionValidatorBuilder privateTransactionValidatorBuilder;
  private WithdrawalsValidator withdrawalsValidator =
      new WithdrawalsValidator.ProhibitedWithdrawals();
  private WithdrawalsProcessor withdrawalsProcessor;
  private RequestsValidator requestsValidator = new ProhibitedRequestValidator();
  private RequestProcessorCoordinator requestProcessorCoordinator;
  protected BlockHashProcessor blockHashProcessor;
  private FeeMarket feeMarket = FeeMarket.legacy();
  private BadBlockManager badBlockManager;
  private PoWHasher powHasher = PoWHasher.ETHASH_LIGHT;
  private boolean isPoS = false;
  private boolean isReplayProtectionSupported = false;

  public ProtocolSpecBuilder gasCalculator(final Supplier<GasCalculator> gasCalculatorBuilder) {
    this.gasCalculatorBuilder = gasCalculatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder gasLimitCalculatorBuilder(
      final Function<FeeMarket, GasLimitCalculator> gasLimitCalculatorBuilder) {
    this.gasLimitCalculatorBuilder = gasLimitCalculatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder blockReward(final Wei blockReward) {
    this.blockReward = blockReward;
    return this;
  }

  public ProtocolSpecBuilder skipZeroBlockRewards(final boolean skipZeroBlockRewards) {
    this.skipZeroBlockRewards = skipZeroBlockRewards;
    return this;
  }

  public ProtocolSpecBuilder blockHeaderFunctions(final BlockHeaderFunctions blockHeaderFunctions) {
    this.blockHeaderFunctions = blockHeaderFunctions;
    return this;
  }

  public ProtocolSpecBuilder transactionReceiptFactory(
      final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory) {
    this.transactionReceiptFactory = transactionReceiptFactory;
    return this;
  }

  public ProtocolSpecBuilder difficultyCalculator(final DifficultyCalculator difficultyCalculator) {
    this.difficultyCalculator = difficultyCalculator;
    return this;
  }

  public ProtocolSpecBuilder evmBuilder(
      final BiFunction<GasCalculator, EvmConfiguration, EVM> evmBuilder) {
    this.evmBuilder = evmBuilder;
    return this;
  }

  public ProtocolSpecBuilder transactionValidatorFactoryBuilder(
      final TransactionValidatorFactoryBuilder transactionValidatorFactoryBuilder) {
    this.transactionValidatorFactoryBuilder = transactionValidatorFactoryBuilder;
    return this;
  }

  public ProtocolSpecBuilder blockHeaderValidatorBuilder(
      final Function<FeeMarket, BlockHeaderValidator.Builder> blockHeaderValidatorBuilder) {
    this.blockHeaderValidatorBuilder = blockHeaderValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder ommerHeaderValidatorBuilder(
      final Function<FeeMarket, BlockHeaderValidator.Builder> ommerHeaderValidatorBuilder) {
    this.ommerHeaderValidatorBuilder = ommerHeaderValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder blockBodyValidatorBuilder(
      final Function<ProtocolSchedule, BlockBodyValidator> blockBodyValidatorBuilder) {
    this.blockBodyValidatorBuilder = blockBodyValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder contractCreationProcessorBuilder(
      final Function<EVM, ContractCreationProcessor> contractCreationProcessorBuilder) {
    this.contractCreationProcessorBuilder = contractCreationProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder precompileContractRegistryBuilder(
      final Function<PrecompiledContractConfiguration, PrecompileContractRegistry>
          precompileContractRegistryBuilder) {
    this.precompileContractRegistryBuilder =
        precompiledContractConfiguration -> {
          final PrecompileContractRegistry registry =
              precompileContractRegistryBuilder.apply(precompiledContractConfiguration);
          if (precompiledContractConfiguration.getPrivacyParameters().isEnabled()) {
            MainnetPrecompiledContractRegistries.appendPrivacy(
                registry, precompiledContractConfiguration);
          }
          return registry;
        };
    return this;
  }

  public ProtocolSpecBuilder messageCallProcessorBuilder(
      final BiFunction<EVM, PrecompileContractRegistry, MessageCallProcessor>
          messageCallProcessorBuilder) {
    this.messageCallProcessorBuilder = messageCallProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder transactionProcessorBuilder(
      final TransactionProcessorBuilder transactionProcessorBuilder) {
    this.transactionProcessorBuilder = transactionProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder privateTransactionProcessorBuilder(
      final PrivateTransactionProcessorBuilder privateTransactionProcessorBuilder) {
    this.privateTransactionProcessorBuilder = privateTransactionProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder privateTransactionValidatorBuilder(
      final PrivateTransactionValidatorBuilder privateTransactionValidatorBuilder) {
    this.privateTransactionValidatorBuilder = privateTransactionValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder blockProcessorBuilder(
      final BlockProcessorBuilder blockProcessorBuilder) {
    this.blockProcessorBuilder = blockProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder blockImporterBuilder(final BlockImporterBuilder blockImporterBuilder) {
    this.blockImporterBuilder = blockImporterBuilder;
    return this;
  }

  public ProtocolSpecBuilder blockValidatorBuilder(
      final BlockValidatorBuilder blockValidatorBuilder) {
    this.blockValidatorBuilder = blockValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder miningBeneficiaryCalculator(
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator) {
    this.miningBeneficiaryCalculator = miningBeneficiaryCalculator;
    return this;
  }

  public ProtocolSpecBuilder name(final String name) {
    this.name = name;
    return this;
  }

  public ProtocolSpecBuilder privacyParameters(final PrivacyParameters privacyParameters) {
    this.privacyParameters = privacyParameters;
    return this;
  }

  public ProtocolSpecBuilder feeMarket(final FeeMarket feeMarket) {
    this.feeMarket = feeMarket;
    return this;
  }

  public ProtocolSpecBuilder badBlocksManager(final BadBlockManager badBlockManager) {
    this.badBlockManager = badBlockManager;
    return this;
  }

  public ProtocolSpecBuilder powHasher(final PoWHasher powHasher) {
    this.powHasher = powHasher;
    return this;
  }

  public ProtocolSpecBuilder evmConfiguration(final EvmConfiguration evmConfiguration) {
    this.evmConfiguration = evmConfiguration;
    return this;
  }

  public ProtocolSpecBuilder withdrawalsValidator(final WithdrawalsValidator withdrawalsValidator) {
    this.withdrawalsValidator = withdrawalsValidator;
    return this;
  }

  public ProtocolSpecBuilder withdrawalsProcessor(final WithdrawalsProcessor withdrawalsProcessor) {
    this.withdrawalsProcessor = withdrawalsProcessor;
    return this;
  }

  public ProtocolSpecBuilder requestsValidator(
      final RequestsValidator requestsValidatorCoordinator) {
    this.requestsValidator = requestsValidatorCoordinator;
    return this;
  }

  public ProtocolSpecBuilder requestProcessorCoordinator(
      final RequestProcessorCoordinator requestProcessorCoordinator) {
    this.requestProcessorCoordinator = requestProcessorCoordinator;
    return this;
  }

  public ProtocolSpecBuilder blockHashProcessor(final BlockHashProcessor blockHashProcessor) {
    this.blockHashProcessor = blockHashProcessor;
    return this;
  }

  public ProtocolSpecBuilder isPoS(final boolean isPoS) {
    this.isPoS = isPoS;
    return this;
  }

  public ProtocolSpecBuilder isReplayProtectionSupported(
      final boolean isReplayProtectionSupported) {
    this.isReplayProtectionSupported = isReplayProtectionSupported;
    return this;
  }

  public ProtocolSpec build(final ProtocolSchedule protocolSchedule) {
    checkNotNull(gasCalculatorBuilder, "Missing gasCalculator");
    checkNotNull(gasLimitCalculatorBuilder, "Missing gasLimitCalculatorBuilder");
    checkNotNull(evmBuilder, "Missing operation registry");
    checkNotNull(evmConfiguration, "Missing evm configuration");
    checkNotNull(transactionValidatorFactoryBuilder, "Missing transaction validator");
    checkNotNull(privateTransactionValidatorBuilder, "Missing private transaction validator");
    checkNotNull(contractCreationProcessorBuilder, "Missing contract creation processor");
    checkNotNull(precompileContractRegistryBuilder, "Missing precompile contract registry");
    checkNotNull(messageCallProcessorBuilder, "Missing message call processor");
    checkNotNull(transactionProcessorBuilder, "Missing transaction processor");
    checkNotNull(privateTransactionProcessorBuilder, "Missing private transaction processor");
    checkNotNull(blockHeaderValidatorBuilder, "Missing block header validator");
    checkNotNull(blockBodyValidatorBuilder, "Missing block body validator");
    checkNotNull(blockProcessorBuilder, "Missing block processor");
    checkNotNull(blockImporterBuilder, "Missing block importer");
    checkNotNull(blockValidatorBuilder, "Missing block validator");
    checkNotNull(blockHeaderFunctions, "Missing block hash function");
    checkNotNull(blockReward, "Missing block reward");
    checkNotNull(difficultyCalculator, "Missing difficulty calculator");
    checkNotNull(transactionReceiptFactory, "Missing transaction receipt factory");
    checkNotNull(name, "Missing name");
    checkNotNull(miningBeneficiaryCalculator, "Missing Mining Beneficiary Calculator");
    checkNotNull(protocolSchedule, "Missing protocol schedule");
    checkNotNull(privacyParameters, "Missing privacy parameters");
    checkNotNull(feeMarket, "Missing fee market");
    checkNotNull(badBlockManager, "Missing bad blocks manager");

    final GasCalculator gasCalculator = gasCalculatorBuilder.get();
    final GasLimitCalculator gasLimitCalculator = gasLimitCalculatorBuilder.apply(feeMarket);
    final EVM evm = evmBuilder.apply(gasCalculator, evmConfiguration);
    final PrecompiledContractConfiguration precompiledContractConfiguration =
        new PrecompiledContractConfiguration(gasCalculator, privacyParameters);
    final TransactionValidatorFactory transactionValidatorFactory =
        transactionValidatorFactoryBuilder.apply(evm, gasLimitCalculator, feeMarket);
    final ContractCreationProcessor contractCreationProcessor =
        contractCreationProcessorBuilder.apply(evm);
    final PrecompileContractRegistry precompileContractRegistry =
        precompileContractRegistryBuilder.apply(precompiledContractConfiguration);
    final MessageCallProcessor messageCallProcessor =
        messageCallProcessorBuilder.apply(evm, precompileContractRegistry);
    final MainnetTransactionProcessor transactionProcessor =
        transactionProcessorBuilder.apply(
            gasCalculator,
            feeMarket,
            transactionValidatorFactory,
            contractCreationProcessor,
            messageCallProcessor);

    final BlockHeaderValidator blockHeaderValidator =
        createBlockHeaderValidator(blockHeaderValidatorBuilder);

    final BlockHeaderValidator ommerHeaderValidator =
        createBlockHeaderValidator(ommerHeaderValidatorBuilder);

    final BlockBodyValidator blockBodyValidator = blockBodyValidatorBuilder.apply(protocolSchedule);

    BlockProcessor blockProcessor = createBlockProcessor(transactionProcessor, protocolSchedule);
    // Set private Tx Processor
    PrivateTransactionProcessor privateTransactionProcessor =
        createPrivateTransactionProcessor(
            transactionValidatorFactory,
            contractCreationProcessor,
            messageCallProcessor,
            precompileContractRegistry);

    if (privacyParameters.isEnabled()) {
      blockProcessor =
          new PrivacyBlockProcessor(
              blockProcessor,
              protocolSchedule,
              privacyParameters.getEnclave(),
              privacyParameters.getPrivateStateStorage(),
              privacyParameters.getPrivateWorldStateArchive(),
              privacyParameters.getPrivateStateRootResolver(),
              privacyParameters.getPrivateStateGenesisAllocator());
    }

    final BlockValidator blockValidator =
        blockValidatorBuilder.apply(
            blockHeaderValidator, blockBodyValidator, blockProcessor, badBlockManager);
    final BlockImporter blockImporter = blockImporterBuilder.apply(blockValidator);
    return new ProtocolSpec(
        name,
        evm,
        transactionValidatorFactory,
        transactionProcessor,
        privateTransactionProcessor,
        blockHeaderValidator,
        ommerHeaderValidator,
        blockBodyValidator,
        blockProcessor,
        blockImporter,
        blockValidator,
        blockHeaderFunctions,
        transactionReceiptFactory,
        difficultyCalculator,
        blockReward,
        miningBeneficiaryCalculator,
        precompileContractRegistry,
        skipZeroBlockRewards,
        gasCalculator,
        gasLimitCalculator,
        feeMarket,
        Optional.ofNullable(powHasher),
        withdrawalsValidator,
        Optional.ofNullable(withdrawalsProcessor),
        requestsValidator,
        Optional.ofNullable(requestProcessorCoordinator),
        blockHashProcessor,
        isPoS,
        isReplayProtectionSupported);
  }

  private PrivateTransactionProcessor createPrivateTransactionProcessor(
      final TransactionValidatorFactory transactionValidatorFactory,
      final AbstractMessageProcessor contractCreationProcessor,
      final AbstractMessageProcessor messageCallProcessor,
      final PrecompileContractRegistry precompileContractRegistry) {
    PrivateTransactionProcessor privateTransactionProcessor = null;
    if (privacyParameters.isEnabled()) {
      final PrivateTransactionValidator privateTransactionValidator =
          privateTransactionValidatorBuilder.apply();
      privateTransactionProcessor =
          privateTransactionProcessorBuilder.apply(
              transactionValidatorFactory,
              contractCreationProcessor,
              messageCallProcessor,
              privateTransactionValidator);

      if (privacyParameters.isPrivacyPluginEnabled()) {
        final PrivacyPluginPrecompiledContract privacyPluginPrecompiledContract =
            (PrivacyPluginPrecompiledContract) precompileContractRegistry.get(PLUGIN_PRIVACY);
        privacyPluginPrecompiledContract.setPrivateTransactionProcessor(
            privateTransactionProcessor);
      } else if (privacyParameters.isFlexiblePrivacyGroupsEnabled()) {
        final FlexiblePrivacyPrecompiledContract flexiblePrivacyPrecompiledContract =
            (FlexiblePrivacyPrecompiledContract) precompileContractRegistry.get(FLEXIBLE_PRIVACY);
        flexiblePrivacyPrecompiledContract.setPrivateTransactionProcessor(
            privateTransactionProcessor);
      } else {
        final PrivacyPrecompiledContract privacyPrecompiledContract =
            (PrivacyPrecompiledContract) precompileContractRegistry.get(DEFAULT_PRIVACY);
        privacyPrecompiledContract.setPrivateTransactionProcessor(privateTransactionProcessor);
      }
    }
    return privateTransactionProcessor;
  }

  private BlockProcessor createBlockProcessor(
      final MainnetTransactionProcessor transactionProcessor,
      final ProtocolSchedule protocolSchedule) {
    return blockProcessorBuilder.apply(
        transactionProcessor,
        transactionReceiptFactory,
        blockReward,
        miningBeneficiaryCalculator,
        skipZeroBlockRewards,
        protocolSchedule);
  }

  private BlockHeaderValidator createBlockHeaderValidator(
      final Function<FeeMarket, BlockHeaderValidator.Builder> blockHeaderValidatorBuilder) {
    return blockHeaderValidatorBuilder
        .apply(feeMarket)
        .difficultyCalculator(difficultyCalculator)
        .build();
  }

  public interface TransactionProcessorBuilder {
    MainnetTransactionProcessor apply(
        GasCalculator gasCalculator,
        FeeMarket feeMarket,
        TransactionValidatorFactory transactionValidatorFactory,
        ContractCreationProcessor contractCreationProcessor,
        MessageCallProcessor messageCallProcessor);
  }

  public interface PrivateTransactionProcessorBuilder {
    PrivateTransactionProcessor apply(
        TransactionValidatorFactory transactionValidatorFactory,
        AbstractMessageProcessor contractCreationProcessor,
        AbstractMessageProcessor messageCallProcessor,
        PrivateTransactionValidator privateTransactionValidator);
  }

  public interface PrivateTransactionValidatorBuilder {
    PrivateTransactionValidator apply();
  }

  public interface BlockProcessorBuilder {
    BlockProcessor apply(
        MainnetTransactionProcessor transactionProcessor,
        AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory,
        Wei blockReward,
        MiningBeneficiaryCalculator miningBeneficiaryCalculator,
        boolean skipZeroBlockRewards,
        ProtocolSchedule protocolSchedule);
  }

  public interface BlockValidatorBuilder {
    BlockValidator apply(
        BlockHeaderValidator blockHeaderValidator,
        BlockBodyValidator blockBodyValidator,
        BlockProcessor blockProcessor,
        BadBlockManager badBlockManager);
  }

  public interface BlockImporterBuilder {
    BlockImporter apply(BlockValidator blockValidator);
  }

  public interface TransactionValidatorFactoryBuilder {
    TransactionValidatorFactory apply(
        EVM evm, GasLimitCalculator gasLimitCalculator, FeeMarket feeMarket);
  }
}
