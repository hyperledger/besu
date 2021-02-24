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

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.EvmAccount;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.core.fees.TransactionGasBudgetCalculator;
import org.hyperledger.besu.ethereum.core.fees.TransactionPriceCalculator;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionSelectionResult;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

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
  private final Double minBlockOccupancyRatio;

  public static class TransactionSelectionResults {

    private final List<Transaction> transactions = Lists.newArrayList();
    private final List<TransactionReceipt> receipts = Lists.newArrayList();
    private long frontierCumulativeGasUsed = 0;
    private long eip1559CumulativeGasUsed = 0;

    private void update(
        final Transaction transaction, final TransactionReceipt receipt, final long gasUsed) {
      transactions.add(transaction);
      receipts.add(receipt);
      if (ExperimentalEIPs.eip1559Enabled
          && transaction.getType().equals(TransactionType.EIP1559)) {
        eip1559CumulativeGasUsed += gasUsed;
      } else {
        frontierCumulativeGasUsed += gasUsed;
      }
    }

    public List<Transaction> getTransactions() {
      return transactions;
    }

    public List<TransactionReceipt> getReceipts() {
      return receipts;
    }

    public long getFrontierCumulativeGasUsed() {
      return frontierCumulativeGasUsed;
    }

    public long getEip1559CumulativeGasUsed() {
      return eip1559CumulativeGasUsed;
    }

    public long getTotalCumulativeGasUsed() {
      return frontierCumulativeGasUsed + eip1559CumulativeGasUsed;
    }
  }

  private final Supplier<Boolean> isCancelled;
  private final MainnetTransactionProcessor transactionProcessor;
  private final ProcessableBlockHeader processableBlockHeader;
  private final Blockchain blockchain;
  private final MutableWorldState worldState;
  private final PendingTransactions pendingTransactions;
  private final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  private final Address miningBeneficiary;
  private final TransactionPriceCalculator transactionPriceCalculator;
  private final TransactionGasBudgetCalculator transactionGasBudgetCalculator;
  private final Optional<EIP1559> eip1559;

  private final TransactionSelectionResults transactionSelectionResult =
      new TransactionSelectionResults();

  public BlockTransactionSelector(
      final MainnetTransactionProcessor transactionProcessor,
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final PendingTransactions pendingTransactions,
      final ProcessableBlockHeader processableBlockHeader,
      final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory,
      final Wei minTransactionGasPrice,
      final Double minBlockOccupancyRatio,
      final Supplier<Boolean> isCancelled,
      final Address miningBeneficiary,
      final TransactionPriceCalculator transactionPriceCalculator,
      final TransactionGasBudgetCalculator transactionGasBudgetCalculator,
      final Optional<EIP1559> eip1559) {
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
    this.transactionPriceCalculator = transactionPriceCalculator;
    this.transactionGasBudgetCalculator = transactionGasBudgetCalculator;
    this.eip1559 = eip1559;
  }

  /*
  This function iterates over (potentially) all transactions in the PendingTransactions, this is a
  long running process.
  If running in a thread, it can be cancelled via the isCancelled supplier (which will result
  in this throwing an CancellationException).
   */
  public TransactionSelectionResults buildTransactionListForBlock(
      final long blockNumber, final long gasLimit) {
    pendingTransactions.selectTransactions(
        pendingTransaction -> evaluateTransaction(blockNumber, gasLimit, pendingTransaction));
    return transactionSelectionResult;
  }

  /**
   * Evaluate the given transactions and return the result of that evaluation.
   *
   * @param blockNumber The block number.
   * @param gasLimit The gas limit.
   * @param transactions The set of transactions to evaluate.
   * @return The {@code TransactionSelectionResults} results of transaction evaluation.
   */
  public TransactionSelectionResults evaluateTransactions(
      final long blockNumber, final long gasLimit, final List<Transaction> transactions) {
    transactions.forEach(transaction -> evaluateTransaction(blockNumber, gasLimit, transaction));
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
  private TransactionSelectionResult evaluateTransaction(
      final long blockNumber, final long gasLimit, final Transaction transaction) {
    if (isCancelled.get()) {
      throw new CancellationException("Cancelled during transaction selection.");
    }

    if (transactionTooLargeForBlock(blockNumber, gasLimit, transaction)) {
      if (blockOccupancyAboveThreshold()) {
        return TransactionSelectionResult.COMPLETE_OPERATION;
      } else {
        return TransactionSelectionResult.CONTINUE;
      }
    }

    // If the gas price specified by the transaction is less than this node is willing to accept,
    // do not include it in the block.
    final Wei actualMinTransactionGasPriceInBlock =
        transactionPriceCalculator.price(transaction, processableBlockHeader.getBaseFee());
    if (minTransactionGasPrice.compareTo(actualMinTransactionGasPriceInBlock) > 0) {
      return TransactionSelectionResult.DELETE_TRANSACTION_AND_CONTINUE;
    }

    final WorldUpdater worldStateUpdater = worldState.updater();
    final BlockHashLookup blockHashLookup = new BlockHashLookup(processableBlockHeader, blockchain);

    TransactionProcessingResult effectiveResult;

    if (transaction.isGoQuorumPrivateTransaction()) {
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
    final MainnetTransactionValidator transactionValidator =
        transactionProcessor.getTransactionValidator();
    ValidationResult<TransactionInvalidReason> validationResult =
        transactionValidator.validate(transaction, blockHeader.getBaseFee());
    if (!validationResult.isValid()) {
      return validationResult;
    }

    final Address senderAddress = transaction.getSender();

    final EvmAccount sender = publicWorldStateUpdater.getOrCreate(senderAddress);
    validationResult =
        transactionValidator.validateForSender(
            transaction, sender, TransactionValidationParams.processingBlock());

    return validationResult;
  }

  /*
  Responsible for updating the state maintained between transaction validation (i.e. receipts,
  cumulative gas, world state root hash.).
   */
  private void updateTransactionResultTracking(
      final Transaction transaction, final TransactionProcessingResult result) {
    final long gasUsedByTransaction =
        transaction.isGoQuorumPrivateTransaction()
            ? 0
            : transaction.getGasLimit() - result.getGasRemaining();

    final long cumulativeGasUsed;
    if (ExperimentalEIPs.eip1559Enabled && eip1559.isPresent()) {
      cumulativeGasUsed =
          transactionSelectionResult.getTotalCumulativeGasUsed() + gasUsedByTransaction;
    } else {
      cumulativeGasUsed =
          transactionSelectionResult.getFrontierCumulativeGasUsed() + gasUsedByTransaction;
    }

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

  private boolean transactionTooLargeForBlock(
      final long blockNumber, final long gasLimit, final Transaction transaction) {

    final long blockGasRemaining;
    if (ExperimentalEIPs.eip1559Enabled && eip1559.isPresent()) {
      switch (transaction.getType()) {
        case EIP1559:
          return !transactionGasBudgetCalculator.hasBudget(
              transaction,
              blockNumber,
              gasLimit,
              transactionSelectionResult.eip1559CumulativeGasUsed);
        case FRONTIER:
          return !transactionGasBudgetCalculator.hasBudget(
              transaction,
              blockNumber,
              gasLimit,
              transactionSelectionResult.frontierCumulativeGasUsed);
        default:
          throw new IllegalStateException(
              String.format(
                  "Developer error. Supported transaction type %s doesn't have a block gas budget calculator",
                  transaction.getType()));
      }
    } else {
      blockGasRemaining =
          processableBlockHeader.getGasLimit()
              - transactionSelectionResult.getFrontierCumulativeGasUsed();
      return transaction.getGasLimit() > blockGasRemaining;
    }
  }

  private boolean blockOccupancyAboveThreshold() {
    final double gasUsed, gasAvailable;
    gasAvailable = processableBlockHeader.getGasLimit();

    if (ExperimentalEIPs.eip1559Enabled && eip1559.isPresent()) {
      gasUsed = transactionSelectionResult.getTotalCumulativeGasUsed();
    } else {
      gasUsed = transactionSelectionResult.getFrontierCumulativeGasUsed();
    }
    return (gasUsed / gasAvailable) >= minBlockOccupancyRatio;
  }
}
