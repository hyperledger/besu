package org.hyperledger.besu.ethereum.blockcreation.evaluation;

import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;

import org.hyperledger.besu.plugin.services.txselection.TransactionSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockSizeSelector implements TransactionSelector {

  private final GasCalculator gasCalculator;
  private final GasLimitCalculator gasLimitCalculator;
  private final Double minBlockOccupancyRatio;
  private final ProcessableBlockHeader processableBlockHeader;

  private static final Logger LOG = LoggerFactory.getLogger(BlockSizeSelector.class);
  private final TransactionSelectionResults transactionSelectionResults;

  public BlockSizeSelector(final BlockSelectionContext context) {
    gasCalculator = context.gasCalculator();
    gasLimitCalculator = context.gasLimitCalculator();
    minBlockOccupancyRatio = context.minBlockOccupancyRatio();
    processableBlockHeader = context.processableBlockHeader();
    transactionSelectionResults = context.getTransactionSelectionResults();
  }

  @Override
  public TransactionSelectionResult evaluate(
      final Transaction transaction) {
    if (transactionTooLargeForBlock(transaction, transactionSelectionResults)) {
      LOG.atTrace()
          .setMessage("Transaction {} too large to select for block creation")
          .addArgument(transaction::toTraceLog)
          .log();
      if (blockOccupancyAboveThreshold(transactionSelectionResults)) {
        LOG.trace("Block occupancy above threshold, completing operation");
        return TransactionSelectionResult.BLOCK_OCCUPANCY_ABOVE_THRESHOLD;
      } else if (blockFull(transactionSelectionResults)) {
        LOG.trace("Block full, completing operation");
        return TransactionSelectionResult.BLOCK_FULL;
      } else {
        return TransactionSelectionResult.TX_TOO_LARGE_FOR_REMAINING_GAS;
      }
    }
    return TransactionSelectionResult.SELECTED;
  }

  private boolean transactionTooLargeForBlock(
      final Transaction transaction,
      final TransactionSelectionResults transactionSelectionResults) {
    final long blobGasUsed = gasCalculator.blobGasCost(transaction.getBlobCount());

    if (blobGasUsed
        > gasLimitCalculator.currentBlobGasLimit()
            - transactionSelectionResults.getCumulativeBlobGasUsed()) {
      return true;
    }

    return transaction.getGasLimit() + blobGasUsed
        > processableBlockHeader.getGasLimit() - transactionSelectionResults.getCumulativeGasUsed();
  }

  private boolean blockOccupancyAboveThreshold(
      final TransactionSelectionResults transactionSelectionResults) {
    final long gasAvailable = processableBlockHeader.getGasLimit();

    final long gasUsed = transactionSelectionResults.getCumulativeGasUsed();
    final long gasRemaining = gasAvailable - gasUsed;
    final double occupancyRatio = (double) gasUsed / (double) gasAvailable;

    LOG.trace(
        "Min block occupancy ratio {}, gas used {}, available {}, remaining {}, used/available {}",
        minBlockOccupancyRatio,
        gasUsed,
        gasAvailable,
        gasRemaining,
        occupancyRatio);

    return occupancyRatio >= minBlockOccupancyRatio;
  }

  private boolean blockFull(final TransactionSelectionResults transactionSelectionResults) {
    final long gasAvailable = processableBlockHeader.getGasLimit();
    final long gasUsed = transactionSelectionResults.getCumulativeGasUsed();

    final long gasRemaining = gasAvailable - gasUsed;

    if (gasRemaining < gasCalculator.getMinimumTransactionCost()) {
      LOG.trace(
          "Block full, remaining gas {} is less than minimum transaction gas cost {}",
          gasRemaining,
          gasCalculator.getMinimumTransactionCost());
      return true;
    }
    return false;
  }
}
