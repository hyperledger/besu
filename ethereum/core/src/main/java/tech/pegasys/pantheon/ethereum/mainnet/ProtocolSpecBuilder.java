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
package tech.pegasys.pantheon.ethereum.mainnet;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.pantheon.ethereum.BlockValidator;
import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockProcessor.TransactionReceiptFactory;
import tech.pegasys.pantheon.ethereum.mainnet.precompiles.privacy.PrivacyPrecompiledContract;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionProcessor;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionValidator;
import tech.pegasys.pantheon.ethereum.vm.EVM;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class ProtocolSpecBuilder<T> {
  private Supplier<GasCalculator> gasCalculatorBuilder;
  private Wei blockReward;
  private boolean skipZeroBlockRewards;
  private BlockHeaderFunctions blockHeaderFunctions;
  private TransactionReceiptFactory transactionReceiptFactory;
  private DifficultyCalculator<T> difficultyCalculator;
  private Function<GasCalculator, EVM> evmBuilder;
  private Function<GasCalculator, TransactionValidator> transactionValidatorBuilder;
  private Function<DifficultyCalculator<T>, BlockHeaderValidator<T>> blockHeaderValidatorBuilder;
  private Function<DifficultyCalculator<T>, BlockHeaderValidator<T>> ommerHeaderValidatorBuilder;
  private Function<ProtocolSchedule<T>, BlockBodyValidator<T>> blockBodyValidatorBuilder;
  private BiFunction<GasCalculator, EVM, AbstractMessageProcessor> contractCreationProcessorBuilder;
  private Function<PrecompiledContractConfiguration, PrecompileContractRegistry>
      precompileContractRegistryBuilder;
  private BiFunction<EVM, PrecompileContractRegistry, AbstractMessageProcessor>
      messageCallProcessorBuilder;
  private TransactionProcessorBuilder transactionProcessorBuilder;
  private BlockProcessorBuilder blockProcessorBuilder;
  private BlockValidatorBuilder<T> blockValidatorBuilder;
  private BlockImporterBuilder<T> blockImporterBuilder;
  private String name;
  private MiningBeneficiaryCalculator miningBeneficiaryCalculator;
  private PrivacyParameters privacyParameters;
  private PrivateTransactionProcessorBuilder privateTransactionProcessorBuilder;
  private PrivateTransactionValidatorBuilder privateTransactionValidatorBuilder;

  public ProtocolSpecBuilder<T> gasCalculator(final Supplier<GasCalculator> gasCalculatorBuilder) {
    this.gasCalculatorBuilder = gasCalculatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder<T> blockReward(final Wei blockReward) {
    this.blockReward = blockReward;
    return this;
  }

  public ProtocolSpecBuilder<T> skipZeroBlockRewards(final boolean skipZeroBlockRewards) {
    this.skipZeroBlockRewards = skipZeroBlockRewards;
    return this;
  }

  public ProtocolSpecBuilder<T> blockHeaderFunctions(
      final BlockHeaderFunctions blockHeaderFunctions) {
    this.blockHeaderFunctions = blockHeaderFunctions;
    return this;
  }

  public ProtocolSpecBuilder<T> transactionReceiptFactory(
      final TransactionReceiptFactory transactionReceiptFactory) {
    this.transactionReceiptFactory = transactionReceiptFactory;
    return this;
  }

  public ProtocolSpecBuilder<T> difficultyCalculator(
      final DifficultyCalculator<T> difficultyCalculator) {
    this.difficultyCalculator = difficultyCalculator;
    return this;
  }

  public ProtocolSpecBuilder<T> evmBuilder(final Function<GasCalculator, EVM> evmBuilder) {
    this.evmBuilder = evmBuilder;
    return this;
  }

  public ProtocolSpecBuilder<T> transactionValidatorBuilder(
      final Function<GasCalculator, TransactionValidator> transactionValidatorBuilder) {
    this.transactionValidatorBuilder = transactionValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder<T> blockHeaderValidatorBuilder(
      final Function<DifficultyCalculator<T>, BlockHeaderValidator<T>>
          blockHeaderValidatorBuilder) {
    this.blockHeaderValidatorBuilder = blockHeaderValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder<T> ommerHeaderValidatorBuilder(
      final Function<DifficultyCalculator<T>, BlockHeaderValidator<T>>
          ommerHeaderValidatorBuilder) {
    this.ommerHeaderValidatorBuilder = ommerHeaderValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder<T> blockBodyValidatorBuilder(
      final Function<ProtocolSchedule<T>, BlockBodyValidator<T>> blockBodyValidatorBuilder) {
    this.blockBodyValidatorBuilder = blockBodyValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder<T> contractCreationProcessorBuilder(
      final BiFunction<GasCalculator, EVM, AbstractMessageProcessor>
          contractCreationProcessorBuilder) {
    this.contractCreationProcessorBuilder = contractCreationProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder<T> precompileContractRegistryBuilder(
      final Function<PrecompiledContractConfiguration, PrecompileContractRegistry>
          precompileContractRegistryBuilder) {
    this.precompileContractRegistryBuilder =
        (precompiledContractConfiguration) -> {
          final PrecompileContractRegistry registry =
              precompileContractRegistryBuilder.apply(precompiledContractConfiguration);
          if (precompiledContractConfiguration.getPrivacyParameters().isEnabled()) {
            MainnetPrecompiledContractRegistries.appendPrivacy(
                registry, precompiledContractConfiguration, Account.DEFAULT_VERSION);
            MainnetPrecompiledContractRegistries.appendPrivacy(
                registry, precompiledContractConfiguration, 1);
          }
          return registry;
        };
    return this;
  }

  public ProtocolSpecBuilder<T> messageCallProcessorBuilder(
      final BiFunction<EVM, PrecompileContractRegistry, AbstractMessageProcessor>
          messageCallProcessorBuilder) {
    this.messageCallProcessorBuilder = messageCallProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder<T> transactionProcessorBuilder(
      final TransactionProcessorBuilder transactionProcessorBuilder) {
    this.transactionProcessorBuilder = transactionProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder<T> privateTransactionProcessorBuilder(
      final PrivateTransactionProcessorBuilder privateTransactionProcessorBuilder) {
    this.privateTransactionProcessorBuilder = privateTransactionProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder<T> privateTransactionValidatorBuilder(
      final PrivateTransactionValidatorBuilder privateTransactionValidatorBuilder) {
    this.privateTransactionValidatorBuilder = privateTransactionValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder<T> blockProcessorBuilder(
      final BlockProcessorBuilder blockProcessorBuilder) {
    this.blockProcessorBuilder = blockProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder<T> blockImporterBuilder(
      final BlockImporterBuilder<T> blockImporterBuilder) {
    this.blockImporterBuilder = blockImporterBuilder;
    return this;
  }

  public ProtocolSpecBuilder<T> blockValidatorBuilder(
      final BlockValidatorBuilder<T> blockValidatorBuilder) {
    this.blockValidatorBuilder = blockValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder<T> miningBeneficiaryCalculator(
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator) {
    this.miningBeneficiaryCalculator = miningBeneficiaryCalculator;
    return this;
  }

  public ProtocolSpecBuilder<T> name(final String name) {
    this.name = name;
    return this;
  }

  public ProtocolSpecBuilder<T> privacyParameters(final PrivacyParameters privacyParameters) {
    this.privacyParameters = privacyParameters;
    return this;
  }

  public <R> ProtocolSpecBuilder<R> changeConsensusContextType(
      final Function<DifficultyCalculator<R>, BlockHeaderValidator<R>> blockHeaderValidatorBuilder,
      final Function<DifficultyCalculator<R>, BlockHeaderValidator<R>> ommerHeaderValidatorBuilder,
      final Function<ProtocolSchedule<R>, BlockBodyValidator<R>> blockBodyValidatorBuilder,
      final BlockValidatorBuilder<R> blockValidatorBuilder,
      final BlockImporterBuilder<R> blockImporterBuilder,
      final DifficultyCalculator<R> difficultyCalculator) {
    return new ProtocolSpecBuilder<R>()
        .gasCalculator(gasCalculatorBuilder)
        .evmBuilder(evmBuilder)
        .transactionValidatorBuilder(transactionValidatorBuilder)
        .privateTransactionValidatorBuilder(privateTransactionValidatorBuilder)
        .contractCreationProcessorBuilder(contractCreationProcessorBuilder)
        .privacyParameters(privacyParameters)
        .precompileContractRegistryBuilder(precompileContractRegistryBuilder)
        .messageCallProcessorBuilder(messageCallProcessorBuilder)
        .transactionProcessorBuilder(transactionProcessorBuilder)
        .privateTransactionProcessorBuilder(privateTransactionProcessorBuilder)
        .blockHeaderValidatorBuilder(blockHeaderValidatorBuilder)
        .ommerHeaderValidatorBuilder(ommerHeaderValidatorBuilder)
        .blockBodyValidatorBuilder(blockBodyValidatorBuilder)
        .blockProcessorBuilder(blockProcessorBuilder)
        .blockValidatorBuilder(blockValidatorBuilder)
        .blockImporterBuilder(blockImporterBuilder)
        .blockHeaderFunctions(blockHeaderFunctions)
        .blockReward(blockReward)
        .skipZeroBlockRewards(skipZeroBlockRewards)
        .difficultyCalculator(difficultyCalculator)
        .transactionReceiptFactory(transactionReceiptFactory)
        .miningBeneficiaryCalculator(miningBeneficiaryCalculator)
        .name(name);
  }

  public ProtocolSpec<T> build(final ProtocolSchedule<T> protocolSchedule) {
    checkNotNull(gasCalculatorBuilder, "Missing gasCalculator");
    checkNotNull(evmBuilder, "Missing operation registry");
    checkNotNull(transactionValidatorBuilder, "Missing transaction validator");
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

    final GasCalculator gasCalculator = gasCalculatorBuilder.get();
    final EVM evm = evmBuilder.apply(gasCalculator);
    final PrecompiledContractConfiguration precompiledContractConfiguration =
        new PrecompiledContractConfiguration(gasCalculator, privacyParameters);
    final TransactionValidator transactionValidator =
        transactionValidatorBuilder.apply(gasCalculator);
    final AbstractMessageProcessor contractCreationProcessor =
        contractCreationProcessorBuilder.apply(gasCalculator, evm);
    final PrecompileContractRegistry precompileContractRegistry =
        precompileContractRegistryBuilder.apply(precompiledContractConfiguration);
    final AbstractMessageProcessor messageCallProcessor =
        messageCallProcessorBuilder.apply(evm, precompileContractRegistry);
    final TransactionProcessor transactionProcessor =
        transactionProcessorBuilder.apply(
            gasCalculator, transactionValidator, contractCreationProcessor, messageCallProcessor);

    // Set private Tx Processor
    if (privacyParameters.isEnabled()) {
      final PrivateTransactionValidator privateTransactionValidator =
          privateTransactionValidatorBuilder.apply();
      final PrivateTransactionProcessor privateTransactionProcessor =
          privateTransactionProcessorBuilder.apply(
              gasCalculator,
              transactionValidator,
              contractCreationProcessor,
              messageCallProcessor,
              privateTransactionValidator);
      Address address = Address.privacyPrecompiled(privacyParameters.getPrivacyAddress());
      PrivacyPrecompiledContract privacyPrecompiledContract =
          (PrivacyPrecompiledContract)
              precompileContractRegistry.get(address, Account.DEFAULT_VERSION);
      privacyPrecompiledContract.setPrivateTransactionProcessor(privateTransactionProcessor);
    }

    final BlockHeaderValidator<T> blockHeaderValidator =
        blockHeaderValidatorBuilder.apply(difficultyCalculator);
    final BlockHeaderValidator<T> ommerHeaderValidator =
        ommerHeaderValidatorBuilder.apply(difficultyCalculator);
    final BlockBodyValidator<T> blockBodyValidator =
        blockBodyValidatorBuilder.apply(protocolSchedule);
    final BlockProcessor blockProcessor =
        blockProcessorBuilder.apply(
            transactionProcessor,
            transactionReceiptFactory,
            blockReward,
            miningBeneficiaryCalculator,
            skipZeroBlockRewards);
    final BlockValidator<T> blockValidator =
        blockValidatorBuilder.apply(blockHeaderValidator, blockBodyValidator, blockProcessor);
    final BlockImporter<T> blockImporter = blockImporterBuilder.apply(blockValidator);
    return new ProtocolSpec<>(
        name,
        evm,
        transactionValidator,
        transactionProcessor,
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
        gasCalculator);
  }

  public interface TransactionProcessorBuilder {
    TransactionProcessor apply(
        GasCalculator gasCalculator,
        TransactionValidator transactionValidator,
        AbstractMessageProcessor contractCreationProcessor,
        AbstractMessageProcessor messageCallProcessor);
  }

  public interface PrivateTransactionProcessorBuilder {
    PrivateTransactionProcessor apply(
        GasCalculator gasCalculator,
        TransactionValidator transactionValidator,
        AbstractMessageProcessor contractCreationProcessor,
        AbstractMessageProcessor messageCallProcessor,
        PrivateTransactionValidator privateTransactionValidator);
  }

  public interface PrivateTransactionValidatorBuilder {
    PrivateTransactionValidator apply();
  }

  public interface BlockProcessorBuilder {
    BlockProcessor apply(
        TransactionProcessor transactionProcessor,
        TransactionReceiptFactory transactionReceiptFactory,
        Wei blockReward,
        MiningBeneficiaryCalculator miningBeneficiaryCalculator,
        boolean skipZeroBlockRewards);
  }

  public interface BlockValidatorBuilder<T> {
    BlockValidator<T> apply(
        BlockHeaderValidator<T> blockHeaderValidator,
        BlockBodyValidator<T> blockBodyValidator,
        BlockProcessor blockProcessor);
  }

  public interface BlockImporterBuilder<T> {
    BlockImporter<T> apply(BlockValidator<T> blockValidator);
  }
}
