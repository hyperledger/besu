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
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;
import org.hyperledger.besu.evm.operation.BlockHashOperation;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.List;
import java.util.Optional;

public class MainnetParallelBlockProcessor extends MainnetBlockProcessor {

  private final Optional<MetricsSystem> metricsSystem;
  private final Optional<Counter> confirmedParallelizedTransactionCounter;
  private final Optional<Counter> conflictingButCachedTransactionCounter;

  public MainnetParallelBlockProcessor(
      final MainnetTransactionProcessor transactionProcessor,
      final TransactionReceiptFactory transactionReceiptFactory,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final boolean skipZeroBlockRewards,
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem) {
    super(
        transactionProcessor,
        transactionReceiptFactory,
        blockReward,
        miningBeneficiaryCalculator,
        skipZeroBlockRewards,
        protocolSchedule);
    this.metricsSystem = Optional.of(metricsSystem);
    this.confirmedParallelizedTransactionCounter =
        Optional.of(
            this.metricsSystem
                .get()
                .createCounter(
                    BesuMetricCategory.BLOCK_PROCESSING,
                    "parallelized_transactions_counter",
                    "Counter for the number of parallelized transactions during block processing"));

    this.conflictingButCachedTransactionCounter =
        Optional.of(
            this.metricsSystem
                .get()
                .createCounter(
                    BesuMetricCategory.BLOCK_PROCESSING,
                    "conflicted_transactions_counter",
                    "Counter for the number of conflicted transactions during block processing"));
  }

  @Override
  protected Optional<PreprocessingContext> runBlockPreProcessing(
      final MutableWorldState worldState,
      final PrivateMetadataUpdater privateMetadataUpdater,
      final BlockHeader blockHeader,
      final List<Transaction> transactions,
      final Address miningBeneficiary,
      final BlockHashOperation.BlockHashLookup blockHashLookup,
      final Wei blobGasPrice) {
    if ((worldState instanceof DiffBasedWorldState)) {
      ParallelizedConcurrentTransactionProcessor parallelizedConcurrentTransactionProcessor =
          new ParallelizedConcurrentTransactionProcessor(transactionProcessor);
      // runAsyncBlock, if activated, facilitates the  non-blocking parallel execution of
      // transactions in the background through an optimistic strategy.
      parallelizedConcurrentTransactionProcessor.runAsyncBlock(
          worldState,
          blockHeader,
          transactions,
          miningBeneficiary,
          blockHashLookup,
          blobGasPrice,
          privateMetadataUpdater);
      return Optional.of(
          new ParallelizedPreProcessingContext(parallelizedConcurrentTransactionProcessor));
    }
    return Optional.empty();
  }

  @Override
  protected TransactionProcessingResult getTransactionProcessingResult(
      final Optional<PreprocessingContext> preProcessingContext,
      final MutableWorldState worldState,
      final WorldUpdater blockUpdater,
      final PrivateMetadataUpdater privateMetadataUpdater,
      final BlockHeader blockHeader,
      final Wei blobGasPrice,
      final Address miningBeneficiary,
      final Transaction transaction,
      final int location,
      final BlockHashOperation.BlockHashLookup blockHashLookup) {

    TransactionProcessingResult transactionProcessingResult = null;

    if (preProcessingContext.isPresent()) {
      final ParallelizedPreProcessingContext parallelizedPreProcessingContext =
          (ParallelizedPreProcessingContext) preProcessingContext.get();
      transactionProcessingResult =
          parallelizedPreProcessingContext
              .getParallelizedConcurrentTransactionProcessor()
              .applyParallelizedTransactionResult(
                  worldState,
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
          worldState,
          blockUpdater,
          privateMetadataUpdater,
          blockHeader,
          blobGasPrice,
          miningBeneficiary,
          transaction,
          location,
          blockHashLookup);
    } else {
      return transactionProcessingResult;
    }
  }

  static class ParallelizedPreProcessingContext implements PreprocessingContext {
    final ParallelizedConcurrentTransactionProcessor parallelizedConcurrentTransactionProcessor;

    public ParallelizedPreProcessingContext(
        final ParallelizedConcurrentTransactionProcessor
            parallelizedConcurrentTransactionProcessor) {
      this.parallelizedConcurrentTransactionProcessor = parallelizedConcurrentTransactionProcessor;
    }

    public ParallelizedConcurrentTransactionProcessor
        getParallelizedConcurrentTransactionProcessor() {
      return parallelizedConcurrentTransactionProcessor;
    }
  }

  public static class ParallelBlockProcessorBuilder
      implements ProtocolSpecBuilder.BlockProcessorBuilder {

    final MetricsSystem metricsSystem;

    public ParallelBlockProcessorBuilder(final MetricsSystem metricsSystem) {
      this.metricsSystem = metricsSystem;
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
          metricsSystem);
    }
  }
}
