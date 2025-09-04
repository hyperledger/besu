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

import org.hyperledger.besu.config.BlobSchedule;
import org.hyperledger.besu.datatypes.HardforkId;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessListFactory;
import org.hyperledger.besu.ethereum.mainnet.blockhash.PreExecutionProcessor;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.mainnet.requests.ProhibitedRequestValidator;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestProcessorCoordinator;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestsValidator;
import org.hyperledger.besu.ethereum.mainnet.transactionpool.TransactionPoolPreProcessor;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class ProtocolSpecBuilder {
  private Supplier<GasCalculator> gasCalculatorBuilder;
  private GasLimitCalculatorBuilder gasLimitCalculatorBuilder;
  private Wei blockReward;
  private boolean skipZeroBlockRewards;

  private BlockHeaderFunctions blockHeaderFunctions;
  private AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  private DifficultyCalculator difficultyCalculator;
  private EvmConfiguration evmConfiguration;
  private BiFunction<GasCalculator, EvmConfiguration, EVM> evmBuilder;
  private TransactionValidatorFactoryBuilder transactionValidatorFactoryBuilder;
  private BlockHeaderValidatorBuilder blockHeaderValidatorBuilder;
  private BlockHeaderValidatorBuilder ommerHeaderValidatorBuilder;
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

  private HardforkId hardforkId;
  private MiningBeneficiaryCalculator miningBeneficiaryCalculator;
  private WithdrawalsValidator withdrawalsValidator =
      new WithdrawalsValidator.ProhibitedWithdrawals();
  private WithdrawalsProcessor withdrawalsProcessor;
  private RequestsValidator requestsValidator = new ProhibitedRequestValidator();
  private RequestProcessorCoordinator requestProcessorCoordinator;
  protected PreExecutionProcessor preExecutionProcessor;
  private FeeMarketBuilder feeMarketBuilder = (__) -> FeeMarket.legacy();
  private BlobSchedule blobSchedule = new BlobSchedule.NoBlobSchedule();
  private BadBlockManager badBlockManager;
  private PoWHasher powHasher = PoWHasher.ETHASH_LIGHT;
  private boolean isPoS = false;
  private boolean isReplayProtectionSupported = false;
  private boolean isBlockAccessListEnabled = false;
  private TransactionPoolPreProcessor transactionPoolPreProcessor;
  private BlockAccessListFactory blockAccessListFactory;

  public ProtocolSpecBuilder gasCalculator(final Supplier<GasCalculator> gasCalculatorBuilder) {
    this.gasCalculatorBuilder = gasCalculatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder gasLimitCalculatorBuilder(
      final GasLimitCalculatorBuilder gasLimitCalculatorBuilder) {
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
      final BlockHeaderValidatorBuilder blockHeaderValidatorBuilder) {
    this.blockHeaderValidatorBuilder = blockHeaderValidatorBuilder;
    return this;
  }

  public ProtocolSpecBuilder ommerHeaderValidatorBuilder(
      final BlockHeaderValidatorBuilder ommerHeaderValidatorBuilder) {
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

  public ProtocolSpecBuilder hardforkId(final HardforkId hardforkId) {
    this.hardforkId = hardforkId;
    return this;
  }

  public ProtocolSpecBuilder feeMarketBuilder(final FeeMarketBuilder feeMarketBuilder) {
    this.feeMarketBuilder = feeMarketBuilder;
    return this;
  }

  public ProtocolSpecBuilder blobSchedule(final BlobSchedule blobSchedule) {
    this.blobSchedule = blobSchedule;
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

  public ProtocolSpecBuilder preExecutionProcessor(
      final PreExecutionProcessor preExecutionProcessor) {
    this.preExecutionProcessor = preExecutionProcessor;
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

  public ProtocolSpecBuilder transactionPoolPreProcessor(
      final TransactionPoolPreProcessor transactionPoolPreProcessor) {
    this.transactionPoolPreProcessor = transactionPoolPreProcessor;
    return this;
  }

  public ProtocolSpecBuilder isBlockAccessListEnabled(final boolean isBlockAccessListEnabled) {
    this.isBlockAccessListEnabled = isBlockAccessListEnabled;
    return this;
  }

  public ProtocolSpecBuilder blockAccessListFactory(
      final BlockAccessListFactory blockAccessListFactory) {
    this.blockAccessListFactory = blockAccessListFactory;
    return this;
  }

  public ProtocolSpec build(final ProtocolSchedule protocolSchedule) {
    checkNotNull(gasCalculatorBuilder, "Missing gasCalculator");
    checkNotNull(gasLimitCalculatorBuilder, "Missing gasLimitCalculatorBuilder");
    checkNotNull(evmBuilder, "Missing operation registry");
    checkNotNull(evmConfiguration, "Missing evm configuration");
    checkNotNull(transactionValidatorFactoryBuilder, "Missing transaction validator");
    checkNotNull(contractCreationProcessorBuilder, "Missing contract creation processor");
    checkNotNull(precompileContractRegistryBuilder, "Missing precompile contract registry");
    checkNotNull(messageCallProcessorBuilder, "Missing message call processor");
    checkNotNull(transactionProcessorBuilder, "Missing transaction processor");
    checkNotNull(blockHeaderValidatorBuilder, "Missing block header validator");
    checkNotNull(blockBodyValidatorBuilder, "Missing block body validator");
    checkNotNull(blockProcessorBuilder, "Missing block processor");
    checkNotNull(blockImporterBuilder, "Missing block importer");
    checkNotNull(blockValidatorBuilder, "Missing block validator");
    checkNotNull(blockHeaderFunctions, "Missing block hash function");
    checkNotNull(blockReward, "Missing block reward");
    checkNotNull(difficultyCalculator, "Missing difficulty calculator");
    checkNotNull(transactionReceiptFactory, "Missing transaction receipt factory");
    checkNotNull(hardforkId, "Missing hardfork id");
    checkNotNull(miningBeneficiaryCalculator, "Missing Mining Beneficiary Calculator");
    checkNotNull(protocolSchedule, "Missing protocol schedule");
    checkNotNull(feeMarketBuilder, "Missing fee market");
    checkNotNull(badBlockManager, "Missing bad blocks manager");
    checkNotNull(blobSchedule, "Missing blob schedule");

    final FeeMarket feeMarket = feeMarketBuilder.apply(blobSchedule);
    final GasCalculator gasCalculator = gasCalculatorBuilder.get();
    final GasLimitCalculator gasLimitCalculator =
        gasLimitCalculatorBuilder.apply(feeMarket, gasCalculator, blobSchedule);
    final EVM evm = evmBuilder.apply(gasCalculator, evmConfiguration);
    final PrecompiledContractConfiguration precompiledContractConfiguration =
        new PrecompiledContractConfiguration(gasCalculator);
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
        createBlockHeaderValidator(
            blockHeaderValidatorBuilder, feeMarket, gasCalculator, gasLimitCalculator);

    final BlockHeaderValidator ommerHeaderValidator =
        createBlockHeaderValidator(
            ommerHeaderValidatorBuilder, feeMarket, gasCalculator, gasLimitCalculator);

    final BlockBodyValidator blockBodyValidator = blockBodyValidatorBuilder.apply(protocolSchedule);

    BlockProcessor blockProcessor = createBlockProcessor(transactionProcessor, protocolSchedule);

    final BlockValidator blockValidator =
        blockValidatorBuilder.apply(blockHeaderValidator, blockBodyValidator, blockProcessor);
    final BlockImporter blockImporter = blockImporterBuilder.apply(blockValidator);

    BlockAccessListFactory finalBalFactory = blockAccessListFactory;
    if (finalBalFactory == null && isBlockAccessListEnabled) {
      // If blockAccessListFactory was not set, but block access lists were enabled via CLI,
      // blockAccessListFactory must be created.
      finalBalFactory = new BlockAccessListFactory(true, false);
    } else if (finalBalFactory != null
        && isBlockAccessListEnabled
        && !finalBalFactory.isCliActivated()) {
      // If blockAccessListFactory was set, we want to make sure its `cliActivated` flag respects
      // isBlockAccessListEnabled.
      finalBalFactory =
          new BlockAccessListFactory(isBlockAccessListEnabled, finalBalFactory.isForkActivated());
    }

    return new ProtocolSpec(
        hardforkId,
        evm,
        transactionValidatorFactory,
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
        gasCalculator,
        gasLimitCalculator,
        feeMarket,
        Optional.ofNullable(powHasher),
        withdrawalsValidator,
        Optional.ofNullable(withdrawalsProcessor),
        requestsValidator,
        Optional.ofNullable(requestProcessorCoordinator),
        preExecutionProcessor,
        isPoS,
        isReplayProtectionSupported,
        Optional.ofNullable(transactionPoolPreProcessor),
        Optional.ofNullable(finalBalFactory));
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
      final BlockHeaderValidatorBuilder blockHeaderValidatorBuilder,
      final FeeMarket feeMarket,
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator) {
    return blockHeaderValidatorBuilder
        .apply(feeMarket, gasCalculator, gasLimitCalculator)
        .difficultyCalculator(difficultyCalculator)
        .build();
  }

  @FunctionalInterface
  public interface TransactionProcessorBuilder {
    MainnetTransactionProcessor apply(
        GasCalculator gasCalculator,
        FeeMarket feeMarket,
        TransactionValidatorFactory transactionValidatorFactory,
        ContractCreationProcessor contractCreationProcessor,
        MessageCallProcessor messageCallProcessor);
  }

  @FunctionalInterface
  public interface BlockProcessorBuilder {
    BlockProcessor apply(
        MainnetTransactionProcessor transactionProcessor,
        AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory,
        Wei blockReward,
        MiningBeneficiaryCalculator miningBeneficiaryCalculator,
        boolean skipZeroBlockRewards,
        ProtocolSchedule protocolSchedule);
  }

  @FunctionalInterface
  public interface BlockValidatorBuilder {
    BlockValidator apply(
        BlockHeaderValidator blockHeaderValidator,
        BlockBodyValidator blockBodyValidator,
        BlockProcessor blockProcessor);
  }

  @FunctionalInterface
  public interface FeeMarketBuilder {
    FeeMarket apply(BlobSchedule blobSchedule);
  }

  @FunctionalInterface
  public interface BlockHeaderValidatorBuilder {
    BlockHeaderValidator.Builder apply(
        FeeMarket feeMarket, GasCalculator gasCalculator, GasLimitCalculator gasLimitCalculator);
  }

  @FunctionalInterface
  public interface GasLimitCalculatorBuilder {
    GasLimitCalculator apply(
        FeeMarket feeMarket, GasCalculator gasCalculator, BlobSchedule blobSchedule);
  }

  @FunctionalInterface
  public interface BlockImporterBuilder {
    BlockImporter apply(BlockValidator blockValidator);
  }

  @FunctionalInterface
  public interface TransactionValidatorFactoryBuilder {
    TransactionValidatorFactory apply(
        EVM evm, GasLimitCalculator gasLimitCalculator, FeeMarket feeMarket);
  }
}
