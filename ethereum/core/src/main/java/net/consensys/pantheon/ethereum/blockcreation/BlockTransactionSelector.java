package net.consensys.pantheon.ethereum.blockcreation;

import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.MutableWorldState;
import net.consensys.pantheon.ethereum.core.PendingTransactions;
import net.consensys.pantheon.ethereum.core.PendingTransactions.TransactionSelectionResult;
import net.consensys.pantheon.ethereum.core.ProcessableBlockHeader;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.core.TransactionReceipt;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.core.WorldUpdater;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockProcessor.TransactionReceiptFactory;
import net.consensys.pantheon.ethereum.mainnet.TransactionProcessor;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for extracting transactions from PendingTransactions and determining if the
 * transaction is suitable for inclusion in the block defined by the provided
 * ProcessableBlockHeader.
 *
 * <p>If a transaction is suitable for inclusion, the world state must be updated, and a receipt
 * generated.
 *
 * <p>The output from this class's exeuction will be:
 *
 * <ul>
 *   <li>A list of transactions to include in the block being constructed.
 *   <li>A list of receipts for inclusion in the block.
 *   <li>The root hash of the world state at the completion of transaction execution.
 *   <li>The amount of gas consumed when executing all transactions.
 * </ul>
 *
 * Once "used" this class must be discarded and another created. This class contains state which is
 * not cleared between executions of buildTransactionListForBlock().
 */
public class BlockTransactionSelector {

  private static final Logger LOG = LogManager.getLogger();
  private final Wei minTransactionGasPrice;

  private static final double MIN_BLOCK_OCCUPANCY_RATIO = 0.8;

  public static class TransactionSelectionResults {
    private final List<Transaction> transactions = Lists.newArrayList();
    private final List<TransactionReceipt> receipts = Lists.newArrayList();
    private long cumulativeGasUsed = 0;

    private void update(
        final Transaction transaction, final TransactionReceipt receipt, final long gasUsed) {
      transactions.add(transaction);
      receipts.add(receipt);
      cumulativeGasUsed += gasUsed;
    }

    public List<Transaction> getTransactions() {
      return transactions;
    }

    public List<TransactionReceipt> getReceipts() {
      return receipts;
    }

    public long getCumulativeGasUsed() {
      return cumulativeGasUsed;
    }
  }

  private final Supplier<Boolean> isCancelled;
  private final TransactionProcessor transactionProcessor;
  private final ProcessableBlockHeader processableBlockHeader;
  private final Blockchain blockchain;
  private final MutableWorldState worldState;
  private final PendingTransactions pendingTransactions;
  private final TransactionReceiptFactory transactionReceiptFactory;
  private final Address miningBeneficiary;

  private final TransactionSelectionResults transactionSelectionResult =
      new TransactionSelectionResults();

  public BlockTransactionSelector(
      final TransactionProcessor transactionProcessor,
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final PendingTransactions pendingTransactions,
      final ProcessableBlockHeader processableBlockHeader,
      final TransactionReceiptFactory transactionReceiptFactory,
      final Wei minTransactionGasPrice,
      final Supplier<Boolean> isCancelled,
      final Address miningBeneficiary) {
    this.transactionProcessor = transactionProcessor;
    this.blockchain = blockchain;
    this.worldState = worldState;
    this.pendingTransactions = pendingTransactions;
    this.processableBlockHeader = processableBlockHeader;
    this.transactionReceiptFactory = transactionReceiptFactory;
    this.isCancelled = isCancelled;
    this.minTransactionGasPrice = minTransactionGasPrice;
    this.miningBeneficiary = miningBeneficiary;
  }

  /*
  This function iterates over (potentially) all transactions in the PendingTransactions, this is a
  long running process.
  If running in a thread, it can be cancelled via the isCancelled supplier (which will result
  in this throwing an CancellationException).
   */
  public TransactionSelectionResults buildTransactionListForBlock() {
    pendingTransactions.selectTransactions(this::evaluateTransaction);
    return transactionSelectionResult;
  }

  /*
   * Passed into the PendingTransactions, and is called on each transaction until sufficient
   * transactions are found which fill a block worth of gas.
   *
   * This function will continue to be called until the block under construction is suitably
   * full (in terms of gasLimit) and the provided transaction's gasLimit does not fit within
   * the space remaining in the block.
   *
   */
  private TransactionSelectionResult evaluateTransaction(final Transaction transaction) {
    if (isCancelled.get()) {
      throw new CancellationException("Cancelled during transaction selection.");
    }

    if (transactionTooLargeForBlock(transaction)) {
      if (blockOccupancyAboveThreshold()) {
        return TransactionSelectionResult.COMPLETE_OPERATION;
      } else {
        return TransactionSelectionResult.CONTINUE;
      }
    }

    // If the gas price specified by the transaction is less than this node is willing to accept,
    // do not include it in the block.
    if (minTransactionGasPrice.compareTo(transaction.getGasPrice()) > 0) {
      return TransactionSelectionResult.DELETE_TRANSACTION_AND_CONTINUE;
    }

    final WorldUpdater worldStateUpdater = worldState.updater();

    final TransactionProcessor.Result result =
        transactionProcessor.processTransaction(
            blockchain, worldStateUpdater, processableBlockHeader, transaction, miningBeneficiary);

    if (!result.isInvalid()) {
      worldStateUpdater.commit();
      updateTransactionResultTracking(transaction, result);
    } else {
      // Remove invalid transactions from the transaction pool but continue looking for valid ones
      // as the block may not yet be full.
      return TransactionSelectionResult.DELETE_TRANSACTION_AND_CONTINUE;
    }
    return TransactionSelectionResult.CONTINUE;
  }

  /*
  Responsible for updating the state maintained between transaction validation (i.e. receipts,
  cumulative gas, world state root hash.).
   */
  private void updateTransactionResultTracking(
      final Transaction transaction, final TransactionProcessor.Result result) {
    final long gasUsedByTransaction = transaction.getGasLimit() - result.getGasRemaining();
    final long cumulativeGasUsed =
        transactionSelectionResult.cumulativeGasUsed + gasUsedByTransaction;

    transactionSelectionResult.update(
        transaction,
        transactionReceiptFactory.create(result, worldState, cumulativeGasUsed),
        gasUsedByTransaction);
  }

  private boolean transactionTooLargeForBlock(final Transaction transaction) {
    final long blockGasRemaining =
        processableBlockHeader.getGasLimit() - transactionSelectionResult.getCumulativeGasUsed();
    return (transaction.getGasLimit() > blockGasRemaining);
  }

  private boolean blockOccupancyAboveThreshold() {
    final double gasUsed = transactionSelectionResult.getCumulativeGasUsed();
    final double gasAvailable = processableBlockHeader.getGasLimit();

    return (gasUsed / gasAvailable) >= MIN_BLOCK_OCCUPANCY_RATIO;
  }
}
