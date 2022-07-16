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
package org.hyperledger.besu.ethereum.blockcreation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionSelectionResult;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger LOG = LoggerFactory.getLogger(BlockTransactionSelector.class);

  private final Wei minTransactionGasPrice;
  private final Double minBlockOccupancyRatio;

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
  private final MainnetTransactionProcessor transactionProcessor;
  private final ProcessableBlockHeader processableBlockHeader;
  private final Blockchain blockchain;
  private final MutableWorldState worldState;
  private final AbstractPendingTransactionsSorter pendingTransactions;
  private final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  private final Address miningBeneficiary;
  private final FeeMarket feeMarket;

  private final TransactionSelectionResults transactionSelectionResult =
      new TransactionSelectionResults();

  public BlockTransactionSelector(
      final MainnetTransactionProcessor transactionProcessor,
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final AbstractPendingTransactionsSorter pendingTransactions,
      final ProcessableBlockHeader processableBlockHeader,
      final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory,
      final Wei minTransactionGasPrice,
      final Double minBlockOccupancyRatio,
      final Supplier<Boolean> isCancelled,
      final Address miningBeneficiary,
      final FeeMarket feeMarket) {
    this.transactionProcessor = transactionProcessor;
    this.blockchain = blockchain;
    this.worldState = worldState;
    this.pendingTransactions = pendingTransactions;
    this.processableBlockHeader = processableBlockHeader;
    this.transactionReceiptFactory = transactionReceiptFactory;
    this.isCancelled = isCancelled;
    this.minTransactionGasPrice = minTransactionGasPrice;
    this.minBlockOccupancyRatio = minBlockOccupancyRatio;
    this.miningBeneficiary = miningBeneficiary;
    this.feeMarket = feeMarket;
  }

  /*
  This function iterates over (potentially) all transactions in the PendingTransactions, this is a
  long running process.
  If running in a thread, it can be cancelled via the isCancelled supplier (which will result
  in this throwing an CancellationException).
   */
  public TransactionSelectionResults buildTransactionListForBlock() {
    pendingTransactions.selectTransactions(
        pendingTransaction -> evaluateTransaction(pendingTransaction));
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
      LOG.trace("{} too large to select for block creation", transaction);
      if (blockOccupancyAboveThreshold()) {
        return TransactionSelectionResult.COMPLETE_OPERATION;
      } else {
        return TransactionSelectionResult.CONTINUE;
      }
    }

    // If the gas price specified by the transaction is less than this node is willing to accept,
    // do not include it in the block.
    final Wei actualMinTransactionGasPriceInBlock =
        feeMarket
            .getTransactionPriceCalculator()
            .price(transaction, processableBlockHeader.getBaseFee());
    if (minTransactionGasPrice.compareTo(actualMinTransactionGasPriceInBlock) > 0) {
      LOG.warn(
          "Gas fee of {} lower than configured minimum {}, deleting",
          transaction,
          minTransactionGasPrice);
      return TransactionSelectionResult.DELETE_TRANSACTION_AND_CONTINUE;
    }

    final WorldUpdater worldStateUpdater = worldState.updater();
    final BlockHashLookup blockHashLookup = new BlockHashLookup(processableBlockHeader, blockchain);
    final boolean isGoQuorumPrivateTransaction =
        transaction.isGoQuorumPrivateTransaction(
            transactionProcessor.getTransactionValidator().getGoQuorumCompatibilityMode());

    TransactionProcessingResult effectiveResult;

    if (isGoQuorumPrivateTransaction) {
      final ValidationResult<TransactionInvalidReason> validationResult =
          validateTransaction(processableBlockHeader, transaction, worldStateUpdater);
      if (!validationResult.isValid()) {
        LOG.warn(
            "Invalid transaction: {}. Block {} Transaction {}",
            validationResult.getErrorMessage(),
            processableBlockHeader.getParentHash().toHexString(),
            transaction.getHash().toHexString());
        return transactionSelectionResultForInvalidResult(validationResult);
      } else {
        // valid GoQuorum private tx, we need to hand craft the receipt and increment the nonce
        effectiveResult = publicResultForWhenWeHaveAPrivateTransaction(transaction);
        worldStateUpdater.getOrCreate(transaction.getSender()).getMutable().incrementNonce();
      }
    } else {
      effectiveResult =
          transactionProcessor.processTransaction(
              blockchain,
              worldStateUpdater,
              processableBlockHeader,
              transaction,
              miningBeneficiary,
              blockHashLookup,
              false,
              TransactionValidationParams.mining());
    }

    if (!effectiveResult.isInvalid()) {
      worldStateUpdater.commit();
      LOG.trace("Selected {} for block creation", transaction);
      updateTransactionResultTracking(transaction, effectiveResult);
    } else {
      return transactionSelectionResultForInvalidResult(effectiveResult.getValidationResult());
    }
    return TransactionSelectionResult.CONTINUE;
  }

  private TransactionSelectionResult transactionSelectionResultForInvalidResult(
      final ValidationResult<TransactionInvalidReason> invalidReasonValidationResult) {
    // If the transaction has an incorrect nonce, leave it in the pool and continue
    if (invalidReasonValidationResult
        .getInvalidReason()
        .equals(TransactionInvalidReason.INCORRECT_NONCE)) {
      return TransactionSelectionResult.CONTINUE;
    }
    // If the transaction was invalid for any other reason, delete it, and continue.
    return TransactionSelectionResult.DELETE_TRANSACTION_AND_CONTINUE;
  }

  private ValidationResult<TransactionInvalidReason> validateTransaction(
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final WorldUpdater publicWorldStateUpdater) {
    final TransactionValidationParams transactionValidationParams =
        TransactionValidationParams.processingBlock();
    final MainnetTransactionValidator transactionValidator =
        transactionProcessor.getTransactionValidator();
    ValidationResult<TransactionInvalidReason> validationResult =
        transactionValidator.validate(
            transaction, blockHeader.getBaseFee(), transactionValidationParams);
    if (!validationResult.isValid()) {
      return validationResult;
    }

    final Address senderAddress = transaction.getSender();

    final EvmAccount sender = publicWorldStateUpdater.getOrCreate(senderAddress);
    validationResult =
        transactionValidator.validateForSender(transaction, sender, transactionValidationParams);

    return validationResult;
  }

  /*
  Responsible for updating the state maintained between transaction validation (i.e. receipts,
  cumulative gas, world state root hash.).
   */
  private void updateTransactionResultTracking(
      final Transaction transaction, final TransactionProcessingResult result) {
    final boolean isGoQuorumPrivateTransaction =
        transaction.isGoQuorumPrivateTransaction(
            transactionProcessor.getTransactionValidator().getGoQuorumCompatibilityMode());

    final long gasUsedByTransaction =
        isGoQuorumPrivateTransaction ? 0 : transaction.getGasLimit() - result.getGasRemaining();

    final long cumulativeGasUsed =
        transactionSelectionResult.getCumulativeGasUsed() + gasUsedByTransaction;

    transactionSelectionResult.update(
        transaction,
        transactionReceiptFactory.create(
            transaction.getType(), result, worldState, cumulativeGasUsed),
        gasUsedByTransaction);
  }

  private TransactionProcessingResult publicResultForWhenWeHaveAPrivateTransaction(
      final Transaction transaction) {
    return TransactionProcessingResult.successful(
        Collections.emptyList(),
        0,
        transaction.getGasLimit(),
        Bytes.EMPTY,
        ValidationResult.valid());
  }

  private boolean transactionTooLargeForBlock(final Transaction transaction) {
    return transaction.getGasLimit()
        > processableBlockHeader.getGasLimit() - transactionSelectionResult.getCumulativeGasUsed();
  }

  private boolean blockOccupancyAboveThreshold() {
    final double gasAvailable = processableBlockHeader.getGasLimit();
    final double gasUsed = transactionSelectionResult.getCumulativeGasUsed();
    return (gasUsed / gasAvailable) >= minBlockOccupancyRatio;
  }
}
