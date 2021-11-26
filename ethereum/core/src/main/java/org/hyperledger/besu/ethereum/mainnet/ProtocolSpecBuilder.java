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
import org.hyperledger.besu.ethereum.core.GoQuorumPrivacyParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.FlexiblePrivacyPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.PrivacyPluginPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.PrivacyPrecompiledContract;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionValidator;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class ProtocolSpecBuilder {
  private Supplier<GasCalculator> gasCalculatorBuilder;
  private GasLimitCalculator gasLimitCalculator;
  private Wei blockReward;
  private boolean skipZeroBlockRewards;
  private BlockHeaderFunctions blockHeaderFunctions;
  private AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  private DifficultyCalculator difficultyCalculator;
  private EvmConfiguration evmConfiguration;
  private BiFunction<GasCalculator, EvmConfiguration, EVM> evmBuilder;
  private Function<GasCalculator, MainnetTransactionValidator> transactionValidatorBuilder;
  private Function<FeeMarket, BlockHeaderValidator.Builder> blockHeaderValidatorBuilder;
  private Function<FeeMarket, BlockHeaderValidator.Builder> ommerHeaderValidatorBuilder;
  private Function<ProtocolSchedule, BlockBodyValidator> blockBodyValidatorBuilder;
  private BiFunction<GasCalculator, EVM, AbstractMessageProcessor> contractCreationProcessorBuilder;
  private Function<PrecompiledContractConfiguration, PrecompileContractRegistry>
      precompileContractRegistryBuilder;
  private BiFunction<EVM, PrecompileContractRegistry, AbstractMessageProcessor>
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
  private FeeMarket feeMarket = FeeMarket.legacy();
  private BadBlockManager badBlockManager;
  private PoWHasher powHasher = PoWHasher.ETHASH_LIGHT;

  public ProtocolSpecBuilder gasCalculator(final Supplier<GasCalculator> gasCalculatorBuilder) {
    this.gasCalculatorBuilder = gasCalculatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder gasLimitCalculator(final GasLimitCalculator gasLimitCalculator) {
    this.gasLimitCalculator = gasLimitCalculator;
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

  public ProtocolSpecBuilder transactionValidatorBuilder(
      final Function<GasCalculator, MainnetTransactionValidator> transactionValidatorBuilder) {
    this.transactionValidatorBuilder = transactionValidatorBuilder;
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
      final BiFunction<GasCalculator, EVM, AbstractMessageProcessor>
          contractCreationProcessorBuilder) {
    this.contractCreationProcessorBuilder = contractCreationProcessorBuilder;
    return this;
  }

  public ProtocolSpecBuilder precompileContractRegistryBuilder(
      final Function<PrecompiledContractConfiguration, PrecompileContractRegistry>
          precompileContractRegistryBuilder) {
    this.precompileContractRegistryBuilder =
        (precompiledContractConfiguration) -> {
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
      final BiFunction<EVM, PrecompileContractRegistry, AbstractMessageProcessor>
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

  public ProtocolSpec build(final ProtocolSchedule protocolSchedule) {
    checkNotNull(gasCalculatorBuilder, "Missing gasCalculator");
    checkNotNull(gasLimitCalculator, "Missing gasLimitCalculator");
    checkNotNull(evmBuilder, "Missing operation registry");
    checkNotNull(evmConfiguration, "Missing evm configuration");
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
    checkNotNull(feeMarket, "Missing fee market");
    checkNotNull(badBlockManager, "Missing bad blocks manager");

    final GasCalculator gasCalculator = gasCalculatorBuilder.get();
    final EVM evm = evmBuilder.apply(gasCalculator, evmConfiguration);
    final PrecompiledContractConfiguration precompiledContractConfiguration =
        new PrecompiledContractConfiguration(gasCalculator, privacyParameters);
    final MainnetTransactionValidator transactionValidator =
        transactionValidatorBuilder.apply(gasCalculator);
    final AbstractMessageProcessor contractCreationProcessor =
        contractCreationProcessorBuilder.apply(gasCalculator, evm);
    final PrecompileContractRegistry precompileContractRegistry =
        precompileContractRegistryBuilder.apply(precompiledContractConfiguration);
    final AbstractMessageProcessor messageCallProcessor =
        messageCallProcessorBuilder.apply(evm, precompileContractRegistry);
    final MainnetTransactionProcessor transactionProcessor =
        transactionProcessorBuilder.apply(
            gasCalculator, transactionValidator, contractCreationProcessor, messageCallProcessor);

    final BlockHeaderValidator blockHeaderValidator =
        blockHeaderValidatorBuilder
            .apply(feeMarket)
            .difficultyCalculator(difficultyCalculator)
            .build();

    final BlockHeaderValidator ommerHeaderValidator =
        ommerHeaderValidatorBuilder
            .apply(feeMarket)
            .difficultyCalculator(difficultyCalculator)
            .build();
    final BlockBodyValidator blockBodyValidator = blockBodyValidatorBuilder.apply(protocolSchedule);

    BlockProcessor blockProcessor =
        blockProcessorBuilder.apply(
            transactionProcessor,
            transactionReceiptFactory,
            blockReward,
            miningBeneficiaryCalculator,
            skipZeroBlockRewards,
            privacyParameters.getGoQuorumPrivacyParameters());
    // Set private Tx Processor
    PrivateTransactionProcessor privateTransactionProcessor = null;
    if (privacyParameters.isEnabled()) {
      final PrivateTransactionValidator privateTransactionValidator =
          privateTransactionValidatorBuilder.apply();
      privateTransactionProcessor =
          privateTransactionProcessorBuilder.apply(
              gasCalculator,
              transactionValidator,
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
            blockHeaderValidator,
            blockBodyValidator,
            blockProcessor,
            badBlockManager,
            privacyParameters.getGoQuorumPrivacyParameters());
    final BlockImporter blockImporter = blockImporterBuilder.apply(blockValidator);
    return new ProtocolSpec(
        name,
        evm,
        transactionValidator,
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
        badBlockManager,
        Optional.ofNullable(powHasher));
  }

  public interface TransactionProcessorBuilder {
    MainnetTransactionProcessor apply(
        GasCalculator gasCalculator,
        MainnetTransactionValidator transactionValidator,
        AbstractMessageProcessor contractCreationProcessor,
        AbstractMessageProcessor messageCallProcessor);
  }

  public interface PrivateTransactionProcessorBuilder {
    PrivateTransactionProcessor apply(
        GasCalculator gasCalculator,
        MainnetTransactionValidator transactionValidator,
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
        Optional<GoQuorumPrivacyParameters> goQuorumPrivacyParameters);
  }

  public interface BlockValidatorBuilder {
    BlockValidator apply(
        BlockHeaderValidator blockHeaderValidator,
        BlockBodyValidator blockBodyValidator,
        BlockProcessor blockProcessor,
        BadBlockManager badBlockManager,
        Optional<GoQuorumPrivacyParameters> goQuorumPrivacyParameters);
  }

  public interface BlockImporterBuilder {
    BlockImporter apply(BlockValidator blockValidator);
  }
}
