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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.DataGas;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionSelectionResult;
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
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * <p>The output from this class's execution will be:
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
  private final Supplier<Boolean> isCancelled;
  private final MainnetTransactionProcessor transactionProcessor;
  private final ProcessableBlockHeader processableBlockHeader;
  private final Blockchain blockchain;
  private final MutableWorldState worldState;
  private final PendingTransactions pendingTransactions;
  private final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  private final Address miningBeneficiary;
  private final FeeMarket feeMarket;
  private final GasCalculator gasCalculator;
  private final GasLimitCalculator gasLimitCalculator;

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
      final FeeMarket feeMarket,
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator) {
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
    this.gasCalculator = gasCalculator;
    this.gasLimitCalculator = gasLimitCalculator;
  }

  /*
  This function iterates over (potentially) all transactions in the PendingTransactions, this is a
  long running process.
  If running in a thread, it can be cancelled via the isCancelled supplier (which will result
  in this throwing an CancellationException).
   */
  public TransactionSelectionResults buildTransactionListForBlock() {
    LOG.debug("Transaction pool size {}", pendingTransactions.size());
    traceLambda(
        LOG, "Transaction pool content {}", () -> pendingTransactions.toTraceLog(false, false));
    pendingTransactions.selectTransactions(
        pendingTransaction -> evaluateTransaction(pendingTransaction, false));
    traceLambda(
        LOG, "Transaction selection result result {}", transactionSelectionResult::toTraceLog);
    return transactionSelectionResult;
  }

  /**
   * Evaluate the given transactions and return the result of that evaluation.
   *
   * @param transactions The set of transactions to evaluate.
   * @return The {@code TransactionSelectionResults} results of transaction evaluation.
   */
  public TransactionSelectionResults evaluateTransactions(final List<Transaction> transactions) {
    transactions.forEach(transaction -> evaluateTransaction(transaction, true));
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
      final Transaction transaction, final boolean reportFutureNonceTransactionsAsInvalid) {
    if (isCancelled.get()) {
      throw new CancellationException("Cancelled during transaction selection.");
    }

    if (transactionTooLargeForBlock(transaction)) {
      traceLambda(
          LOG, "Transaction {} too large to select for block creation", transaction::toTraceLog);
      if (blockOccupancyAboveThreshold()) {
        traceLambda(LOG, "Block occupancy above threshold, completing operation");
        return TransactionSelectionResult.COMPLETE_OPERATION;
      } else {
        return TransactionSelectionResult.CONTINUE;
      }
    }

    if (transactionCurrentPriceBelowMin(transaction)) {
      return TransactionSelectionResult.CONTINUE;
    }
    if (transactionDataPriceBelowMin(transaction)) {
      return TransactionSelectionResult.CONTINUE;
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
        return transactionSelectionResultForInvalidResult(transaction, validationResult);
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
              TransactionValidationParams.mining(),
              Wei.ZERO);
    }

    if (!effectiveResult.isInvalid()) {
      worldStateUpdater.commit();
      traceLambda(LOG, "Selected {} for block creation", transaction::toTraceLog);
      updateTransactionResultTracking(transaction, effectiveResult);
    } else {
      final var isIncorrectNonce = isIncorrectNonce(effectiveResult.getValidationResult());
      if (!isIncorrectNonce || reportFutureNonceTransactionsAsInvalid) {
        transactionSelectionResult.updateWithInvalidTransaction(
            transaction, effectiveResult.getValidationResult());
      }
      return transactionSelectionResultForInvalidResult(
          transaction, effectiveResult.getValidationResult());
    }
    return TransactionSelectionResult.CONTINUE;
  }

  private boolean transactionDataPriceBelowMin(final Transaction transaction) {
    if (transaction.getType().supportsBlob()) {
      if (transaction
          .getMaxFeePerDataGas()
          .orElseThrow()
          .lessThan(
              feeMarket.dataPrice(
                  processableBlockHeader.getExcessDataGas().orElse(DataGas.ZERO)))) {
        return true;
      }
    }
    return false;
  }

  private boolean transactionCurrentPriceBelowMin(final Transaction transaction) {
    // Here we only care about EIP1159 since for Frontier and local transactions the checks
    // that we do when accepting them in the pool are enough
    if (transaction.getType().supports1559FeeMarket()
        && !pendingTransactions.isLocalSender(transaction.getSender())) {

      // For EIP1559 transactions, the price is dynamic and depends on network conditions, so we can
      // only calculate at this time the current minimum price the transaction is willing to pay
      // and if it is above the minimum accepted by the node.
      // If below we do not delete the transaction, since when we added the transaction to the pool,
      // we assured sure that the maxFeePerGas is >= of the minimum price accepted by the node
      // and so the price of the transaction could satisfy this rule in the future
      final Wei currentMinTransactionGasPriceInBlock =
          feeMarket
              .getTransactionPriceCalculator()
              .price(transaction, processableBlockHeader.getBaseFee());
      if (minTransactionGasPrice.compareTo(currentMinTransactionGasPriceInBlock) > 0) {
        LOG.trace(
            "Current gas fee of {} is lower than configured minimum {}, skipping",
            transaction,
            minTransactionGasPrice);
        return true;
      }
    }
    return false;
  }

  private TransactionSelectionResult transactionSelectionResultForInvalidResult(
      final Transaction transaction,
      final ValidationResult<TransactionInvalidReason> invalidReasonValidationResult) {

    final var invalidReason = invalidReasonValidationResult.getInvalidReason();
    // If the invalid reason is transient, then leave the transaction in the pool and continue
    if (isTransientValidationError(invalidReason)) {
      traceLambda(
          LOG,
          "Transient validation error {} for transaction {} keeping it in the pool",
          invalidReason::toString,
          transaction::toTraceLog);
      return TransactionSelectionResult.CONTINUE;
    }
    // If the transaction was invalid for any other reason, delete it, and continue.
    traceLambda(
        LOG,
        "Delete invalid transaction {}, reason {}",
        transaction::toTraceLog,
        invalidReason::toString);
    return TransactionSelectionResult.DELETE_TRANSACTION_AND_CONTINUE;
  }

  private boolean isTransientValidationError(final TransactionInvalidReason invalidReason) {
    return invalidReason.equals(TransactionInvalidReason.GAS_PRICE_BELOW_CURRENT_BASE_FEE)
        || invalidReason.equals(TransactionInvalidReason.NONCE_TOO_HIGH);
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

    final long dataGasUsed = gasCalculator.dataGasCost(transaction.getBlobCount());

    transactionSelectionResult.update(
        transaction,
        transactionReceiptFactory.create(
            transaction.getType(), result, worldState, cumulativeGasUsed),
        gasUsedByTransaction,
        dataGasUsed);
  }

  private boolean isIncorrectNonce(final ValidationResult<TransactionInvalidReason> result) {
    return result.getInvalidReason().equals(TransactionInvalidReason.NONCE_TOO_HIGH);
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
    final long dataGasUsed = gasCalculator.dataGasCost(transaction.getBlobCount());

    if (dataGasUsed
        > gasLimitCalculator.currentDataGasLimit()
            - transactionSelectionResult.getCumulativeDataGasUsed()) {
      return true;
    }

    return transaction.getGasLimit() + dataGasUsed
        > processableBlockHeader.getGasLimit() - transactionSelectionResult.getCumulativeGasUsed();
  }

  private boolean blockOccupancyAboveThreshold() {
    final double gasAvailable = processableBlockHeader.getGasLimit();
    final double gasUsed = transactionSelectionResult.getCumulativeGasUsed();
    final double occupancyRatio = gasUsed / gasAvailable;
    LOG.trace(
        "Min block occupancy ratio {}, gas used {}, available {}, used/available {}",
        minBlockOccupancyRatio,
        gasUsed,
        gasAvailable,
        occupancyRatio);
    return occupancyRatio >= minBlockOccupancyRatio;
  }

  public static class TransactionValidationResult {
    private final Transaction transaction;
    private final ValidationResult<TransactionInvalidReason> validationResult;

    public TransactionValidationResult(
        final Transaction transaction,
        final ValidationResult<TransactionInvalidReason> validationResult) {
      this.transaction = transaction;
      this.validationResult = validationResult;
    }

    public Transaction getTransaction() {
      return transaction;
    }

    public ValidationResult<TransactionInvalidReason> getValidationResult() {
      return validationResult;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TransactionValidationResult that = (TransactionValidationResult) o;
      return Objects.equals(transaction, that.transaction)
          && Objects.equals(validationResult, that.validationResult);
    }

    @Override
    public int hashCode() {
      return Objects.hash(transaction, validationResult);
    }
  }

  public static class TransactionSelectionResults {

    private final Map<TransactionType, List<Transaction>> transactionsByType = new HashMap<>();
    private final List<TransactionReceipt> receipts = Lists.newArrayList();
    private final List<TransactionValidationResult> invalidTransactions = Lists.newArrayList();
    private long cumulativeGasUsed = 0;
    private long cumulativeDataGasUsed = 0;

    private void update(
        final Transaction transaction,
        final TransactionReceipt receipt,
        final long gasUsed,
        final long dataGasUsed) {
      transactionsByType
          .computeIfAbsent(transaction.getType(), type -> new ArrayList<>())
          .add(transaction);
      receipts.add(receipt);
      cumulativeGasUsed += gasUsed;
      cumulativeDataGasUsed += dataGasUsed;
      traceLambda(
          LOG,
          "New selected transaction {}, total transactions {}, cumulative gas used {}, cumulative data gas used {}",
          transaction::toTraceLog,
          () -> transactionsByType.values().stream().mapToInt(List::size).sum(),
          () -> cumulativeGasUsed,
          () -> cumulativeDataGasUsed);
    }

    private void updateWithInvalidTransaction(
        final Transaction transaction,
        final ValidationResult<TransactionInvalidReason> validationResult) {
      invalidTransactions.add(new TransactionValidationResult(transaction, validationResult));
    }

    public List<Transaction> getTransactions() {
      return streamAllTransactions().collect(Collectors.toList());
    }

    public List<Transaction> getTransactionsByType(final TransactionType type) {
      return transactionsByType.getOrDefault(type, List.of());
    }

    public List<TransactionReceipt> getReceipts() {
      return receipts;
    }

    public long getCumulativeGasUsed() {
      return cumulativeGasUsed;
    }

    public long getCumulativeDataGasUsed() {
      return cumulativeDataGasUsed;
    }

    public List<TransactionValidationResult> getInvalidTransactions() {
      return invalidTransactions;
    }

    private Stream<Transaction> streamAllTransactions() {
      return transactionsByType.values().stream().flatMap(List::stream);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TransactionSelectionResults that = (TransactionSelectionResults) o;
      return cumulativeGasUsed == that.cumulativeGasUsed
          && cumulativeDataGasUsed == that.cumulativeDataGasUsed
          && transactionsByType.equals(that.transactionsByType)
          && receipts.equals(that.receipts)
          && invalidTransactions.equals(that.invalidTransactions);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          transactionsByType,
          receipts,
          invalidTransactions,
          cumulativeGasUsed,
          cumulativeDataGasUsed);
    }

    public String toTraceLog() {
      return "cumulativeGasUsed="
          + cumulativeGasUsed
          + ", cumulativeDataGasUsed="
          + cumulativeDataGasUsed
          + ", transactions="
          + streamAllTransactions().map(Transaction::toTraceLog).collect(Collectors.joining("; "));
    }
  }
}
