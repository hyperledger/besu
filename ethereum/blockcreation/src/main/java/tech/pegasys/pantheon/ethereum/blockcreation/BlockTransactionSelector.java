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
package tech.pegasys.pantheon.ethereum.blockcreation;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.ProcessableBlockHeader;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions.TransactionSelectionResult;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockProcessor.TransactionReceiptFactory;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionProcessor;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidationParams;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.vm.BlockHashLookup;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

import com.google.common.collect.Lists;

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

  /**
   * Evaluate the given transactions and return the result of that evaluation.
   *
   * @param transactions The set of transactions to evaluate.
   * @return The {@code TransactionSelectionResults} results of transaction evaluation.
   */
  public TransactionSelectionResults evaluateTransactions(final List<Transaction> transactions) {
    transactions.forEach(this::evaluateTransaction);
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
    final BlockHashLookup blockHashLookup = new BlockHashLookup(processableBlockHeader, blockchain);

    final TransactionProcessor.Result result =
        transactionProcessor.processTransaction(
            blockchain,
            worldStateUpdater,
            processableBlockHeader,
            transaction,
            miningBeneficiary,
            blockHashLookup,
            false,
            TransactionValidationParams.mining());

    if (!result.isInvalid()) {
      worldStateUpdater.commit();
      updateTransactionResultTracking(transaction, result);
    } else {
      // If the transaction has an incorrect nonce, leave it in the pool and continue
      if (result
          .getValidationResult()
          .getInvalidReason()
          .equals(TransactionInvalidReason.INCORRECT_NONCE)) {
        return TransactionSelectionResult.CONTINUE;
      }
      // If the transaction was invalid for any other reason, delete it, and continue.
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
