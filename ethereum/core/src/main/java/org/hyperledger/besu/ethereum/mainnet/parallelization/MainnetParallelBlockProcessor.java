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

import java.util.Optional;

public class MainnetParallelBlockProcessor extends MainnetBlockProcessor {

  private final ParallelizedConcurrentTransactionProcessor parallelTransactionProcessor;
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
    super(transactionProcessor, transactionReceiptFactory, blockReward,
        miningBeneficiaryCalculator, skipZeroBlockRewards, protocolSchedule);
    this.metricsSystem = Optional.of(metricsSystem);
    this.parallelTransactionProcessor =
        new ParallelizedConcurrentTransactionProcessor(transactionProcessor);
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
  protected TransactionProcessingResult getTransactionProcessingResult(
      final MutableWorldState worldState,
      final BlockHeader blockHeader,
      final PrivateMetadataUpdater privateMetadataUpdater,
      final Address miningBeneficiary,
      final Transaction transaction,
      final int blockTxNumber,
      final WorldUpdater blockUpdater,
      final BlockHashOperation.BlockHashLookup blockHashLookup,
      final Wei blobGasPrice) {


    TransactionProcessingResult transactionProcessingResult = null;

    if ((worldState instanceof DiffBasedWorldState)) {
      transactionProcessingResult = parallelTransactionProcessor
          .applyParallelizedTransactionResult(worldState, miningBeneficiary, transaction,
              blockTxNumber, confirmedParallelizedTransactionCounter, conflictingButCachedTransactionCounter)
          .orElse(null);
    }

    if (transactionProcessingResult != null) {
      return transactionProcessingResult;
    }
    return super.getTransactionProcessingResult(
        worldState, blockHeader, privateMetadataUpdater, miningBeneficiary, transaction,
        blockTxNumber, blockUpdater, blockHashLookup, blobGasPrice);

  }

  public static class ParallelBlockProcessorBuilder implements ProtocolSpecBuilder.BlockProcessorBuilder {

    final MetricsSystem metricsSystem;

    public ParallelBlockProcessorBuilder(
        final MetricsSystem metricsSystem) {
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
