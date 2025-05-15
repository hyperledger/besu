/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.mainnet.parallelization;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;
import org.hyperledger.besu.ethereum.mainnet.parallelization.preload.Preloader;
import org.hyperledger.besu.ethereum.mainnet.systemcall.BlockProcessingContext;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainnetParallelBlockProcessor extends MainnetBlockProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(MainnetParallelBlockProcessor.class);

  private final Optional<Counter> confirmedParallelizedTransactionCounter;
  private final Optional<Counter> conflictingButCachedTransactionCounter;

  private static final int NCPU = Runtime.getRuntime().availableProcessors();
  private static final Executor executor = Executors.newFixedThreadPool(NCPU);

  private final Optional<Preloader> preloadService;

  public MainnetParallelBlockProcessor(
      final MainnetTransactionProcessor transactionProcessor,
      final TransactionReceiptFactory transactionReceiptFactory,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final boolean skipZeroBlockRewards,
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final Optional<Preloader> preloadService) {
    super(
        transactionProcessor,
        transactionReceiptFactory,
        blockReward,
        miningBeneficiaryCalculator,
        skipZeroBlockRewards,
        protocolSchedule);
    this.confirmedParallelizedTransactionCounter =
        Optional.of(
            metricsSystem.createCounter(
                BesuMetricCategory.BLOCK_PROCESSING,
                "parallelized_transactions_counter",
                "Counter for the number of parallelized transactions during block processing"));

    this.conflictingButCachedTransactionCounter =
        Optional.of(
            metricsSystem.createCounter(
                BesuMetricCategory.BLOCK_PROCESSING,
                "conflicted_transactions_counter",
                "Counter for the number of conflicted transactions during block processing"));

    this.preloadService = preloadService;
  }

  @Override
  protected TransactionProcessingResult getTransactionProcessingResult(
      final Optional<PreprocessingContext> preProcessingContext,
      final BlockProcessingContext blockProcessingContext,
      final WorldUpdater blockUpdater,
      final Optional<PrivateMetadataUpdater> privateMetadataUpdater,
      final Wei blobGasPrice,
      final Address miningBeneficiary,
      final Transaction transaction,
      final int location,
      final BlockHashLookup blockHashLookup) {

    TransactionProcessingResult transactionProcessingResult = null;

    if (preProcessingContext.isPresent()) {
      final ParallelizedPreProcessingContext parallelizedPreProcessingContext =
          (ParallelizedPreProcessingContext) preProcessingContext.get();
      transactionProcessingResult =
          parallelizedPreProcessingContext
              .parallelizedConcurrentTransactionProcessor()
              .applyParallelizedTransactionResult(
                  blockProcessingContext.getWorldState(),
                  miningBeneficiary,
                  transaction,
                  location,
                  confirmedParallelizedTransactionCounter,
                  conflictingButCachedTransactionCounter)
              .orElse(null);
    }

    if (transactionProcessingResult == null) {
      return super.getTransactionProcessingResult(
          preProcessingContext,
          blockProcessingContext,
          blockUpdater,
          privateMetadataUpdater,
          blobGasPrice,
          miningBeneficiary,
          transaction,
          location,
          blockHashLookup);
    } else {
      return transactionProcessingResult;
    }
  }

  @Override
  public BlockProcessingResult processBlock(
      final ProtocolContext protocolContext,
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final Block block) {
    // TODO: This is an issue when processing multiple blocks in parallel. Is it necessary / helpful? If so, solve, if not, remove
    if (preloadService.isPresent()) {
      preloadService.get().clearQueue();
    }

    final BlockProcessingResult blockProcessingResult =
        super.processBlock(
            protocolContext,
            blockchain,
            worldState,
            block,
            Optional.empty(),
            new ParallelTransactionPreprocessing(transactionProcessor, executor, preloadService));

    if (blockProcessingResult.isFailed()) {
      // Fallback to non-parallel processing if there is a block processing exception .
      LOG.info(
          "Parallel transaction processing failure. Falling back to non-parallel processing for block #{} ({})",
          block.getHeader().getNumber(),
          block.getHash());
      return super.processBlock(protocolContext, blockchain, worldState, block);
    }
    return blockProcessingResult;
  }

  record ParallelizedPreProcessingContext(
      ParallelizedConcurrentTransactionProcessor parallelizedConcurrentTransactionProcessor)
      implements PreprocessingContext {}

  public static class ParallelBlockProcessorBuilder
      implements ProtocolSpecBuilder.BlockProcessorBuilder {

    final MetricsSystem metricsSystem;
    final Optional<Preloader> preloadService;

    public ParallelBlockProcessorBuilder(final MetricsSystem metricsSystem, final Optional<Preloader> preloadService) {
      this.metricsSystem = metricsSystem;
      this.preloadService = preloadService;
    }

    @Override
    public BlockProcessor apply(
        final MainnetTransactionProcessor transactionProcessor,
        final TransactionReceiptFactory transactionReceiptFactory,
        final Wei blockReward,
        final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
        final boolean skipZeroBlockRewards,
        final ProtocolSchedule protocolSchedule) {
      return new MainnetParallelBlockProcessor(
          transactionProcessor,
          transactionReceiptFactory,
          blockReward,
          miningBeneficiaryCalculator,
          skipZeroBlockRewards,
          protocolSchedule,
          metricsSystem,
          preloadService);
    }
  }
}
