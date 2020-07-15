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

import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.core.fees.TransactionGasBudgetCalculator;
import org.hyperledger.besu.ethereum.core.fees.TransactionPriceCalculator;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.OnChainPrivacyPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.PrivacyPrecompiledContract;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionValidator;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.GasCalculator;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class ProtocolSpecBuilder {
  private Supplier<GasCalculator> gasCalculatorBuilder;
  private Wei blockReward;
  private boolean skipZeroBlockRewards;
  private BlockHeaderFunctions blockHeaderFunctions;
  private AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  private DifficultyCalculator difficultyCalculator;
  private Function<GasCalculator, EVM> evmBuilder;
  private Function<GasCalculator, TransactionValidator> transactionValidatorBuilder;
  private BlockHeaderValidator.Builder blockHeaderValidatorBuilder;
  private BlockHeaderValidator.Builder ommerHeaderValidatorBuilder;
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
  private TransactionPriceCalculator transactionPriceCalculator =
      TransactionPriceCalculator.frontier();
  private Optional<EIP1559> eip1559 = Optional.empty();
  private TransactionGasBudgetCalculator gasBudgetCalculator =
      TransactionGasBudgetCalculator.frontier();

  public ProtocolSpecBuilder gasCalculator(final Supplier<GasCalculator> gasCalculatorBuilder) {
    this.gasCalculatorBuilder = gasCalculatorBuilder;
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

  public ProtocolSpecBuilder evmBuilder(final Function<GasCalculator, EVM> evmBuilder) {
    this.evmBuilder = evmBuilder;
    return this;
  }

  public ProtocolSpecBuilder transactionValidatorBuilder(
      final Function<GasCalculator, TransactionValidator> transactionValidatorBuilder) {
    this.transactionValidatorBuilder = transactionValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder blockHeaderValidatorBuilder(
      final BlockHeaderValidator.Builder blockHeaderValidatorBuilder) {
    this.blockHeaderValidatorBuilder = blockHeaderValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder ommerHeaderValidatorBuilder(
      final BlockHeaderValidator.Builder ommerHeaderValidatorBuilder) {
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
                registry, precompiledContractConfiguration, Account.DEFAULT_VERSION);
            MainnetPrecompiledContractRegistries.appendPrivacy(
                registry, precompiledContractConfiguration, 1);
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

  public ProtocolSpecBuilder transactionPriceCalculator(
      final TransactionPriceCalculator transactionPriceCalculator) {
    this.transactionPriceCalculator = transactionPriceCalculator;
    return this;
  }

  public ProtocolSpecBuilder eip1559(final Optional<EIP1559> eip1559) {
    this.eip1559 = eip1559;
    return this;
  }

  public ProtocolSpecBuilder gasBudgetCalculator(
      final TransactionGasBudgetCalculator gasBudgetCalculator) {
    this.gasBudgetCalculator = gasBudgetCalculator;
    return this;
  }

  public ProtocolSpec build(final ProtocolSchedule protocolSchedule) {
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
    checkNotNull(transactionPriceCalculator, "Missing transaction price calculator");
    checkNotNull(eip1559, "Missing eip1559 optional wrapper");

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

    final BlockHeaderValidator blockHeaderValidator =
        blockHeaderValidatorBuilder.difficultyCalculator(difficultyCalculator).build();

    final BlockHeaderValidator ommerHeaderValidator =
        ommerHeaderValidatorBuilder.difficultyCalculator(difficultyCalculator).build();
    final BlockBodyValidator blockBodyValidator = blockBodyValidatorBuilder.apply(protocolSchedule);

    BlockProcessor blockProcessor =
        blockProcessorBuilder.apply(
            transactionProcessor,
            transactionReceiptFactory,
            blockReward,
            miningBeneficiaryCalculator,
            skipZeroBlockRewards,
            gasBudgetCalculator);
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

      if (privacyParameters.isOnchainPrivacyGroupsEnabled()) {
        final OnChainPrivacyPrecompiledContract onChainPrivacyPrecompiledContract =
            (OnChainPrivacyPrecompiledContract)
                precompileContractRegistry.get(Address.ONCHAIN_PRIVACY, Account.DEFAULT_VERSION);
        onChainPrivacyPrecompiledContract.setPrivateTransactionProcessor(
            privateTransactionProcessor);
      } else {
        final PrivacyPrecompiledContract privacyPrecompiledContract =
            (PrivacyPrecompiledContract)
                precompileContractRegistry.get(Address.DEFAULT_PRIVACY, Account.DEFAULT_VERSION);
        privacyPrecompiledContract.setPrivateTransactionProcessor(privateTransactionProcessor);
      }

      blockProcessor =
          new PrivacyBlockProcessor(
              blockProcessor,
              protocolSchedule,
              privacyParameters.getEnclave(),
              privacyParameters.getPrivateStateStorage(),
              privacyParameters.getPrivateWorldStateArchive(),
              privacyParameters.getPrivateStateRootResolver());
    }

    final BlockValidator blockValidator =
        blockValidatorBuilder.apply(blockHeaderValidator, blockBodyValidator, blockProcessor);
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
        transactionPriceCalculator,
        eip1559,
        gasBudgetCalculator);
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
        AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory,
        Wei blockReward,
        MiningBeneficiaryCalculator miningBeneficiaryCalculator,
        boolean skipZeroBlockRewards,
        TransactionGasBudgetCalculator gasBudgetCalculator);
  }

  public interface BlockValidatorBuilder {
    BlockValidator apply(
        BlockHeaderValidator blockHeaderValidator,
        BlockBodyValidator blockBodyValidator,
        BlockProcessor blockProcessor);
  }

  public interface BlockImporterBuilder {
    BlockImporter apply(BlockValidator blockValidator);
  }
}
