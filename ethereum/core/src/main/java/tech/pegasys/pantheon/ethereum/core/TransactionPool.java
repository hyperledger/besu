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
package tech.pegasys.pantheon.ethereum.core;

import static java.util.Collections.singletonList;
import static org.apache.logging.log4j.LogManager.getLogger;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.Logger;

/**
 * Maintains the set of pending transactions received from JSON-RPC or other nodes. Transactions are
 * removed automatically when they are included in a block on the canonical chain and re-added if a
 * re-org removes them from the canonical chain again.
 *
 * <p>This class is safe for use across multiple threads.
 */
public class TransactionPool implements BlockAddedObserver {
  private static final Logger LOG = getLogger();
  private final PendingTransactions pendingTransactions;
  private final ProtocolSchedule<?> protocolSchedule;
  private final ProtocolContext<?> protocolContext;
  private final TransactionBatchAddedListener transactionBatchAddedListener;
  private Optional<AccountFilter> accountFilter = Optional.empty();

  public TransactionPool(
      final PendingTransactions pendingTransactions,
      final ProtocolSchedule<?> protocolSchedule,
      final ProtocolContext<?> protocolContext,
      final TransactionBatchAddedListener transactionBatchAddedListener) {
    this.pendingTransactions = pendingTransactions;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.transactionBatchAddedListener = transactionBatchAddedListener;
  }

  public ValidationResult<TransactionInvalidReason> addLocalTransaction(
      final Transaction transaction) {
    final ValidationResult<TransactionInvalidReason> validationResult =
        validateTransaction(transaction);

    validationResult.ifValid(
        () -> {
          final boolean added = pendingTransactions.addLocalTransaction(transaction);
          if (added) {
            transactionBatchAddedListener.onTransactionsAdded(singletonList(transaction));
          }
        });
    return validationResult;
  }

  public void addRemoteTransactions(final Collection<Transaction> transactions) {
    final Set<Transaction> addedTransactions = new HashSet<>();
    for (final Transaction transaction : sortByNonce(transactions)) {
      final ValidationResult<TransactionInvalidReason> validationResult =
          validateTransaction(transaction);
      if (validationResult.isValid()) {
        final boolean added = pendingTransactions.addRemoteTransaction(transaction);
        if (added) {
          addedTransactions.add(transaction);
        }
      } else {
        LOG.trace(
            "Validation failed ({}) for transaction {}. Discarding.",
            validationResult.getInvalidReason(),
            transaction);
      }
    }
    if (!addedTransactions.isEmpty()) {
      transactionBatchAddedListener.onTransactionsAdded(addedTransactions);
    }
  }

  // Sort transactions by nonce to ensure we import sequences of transactions correctly
  private List<Transaction> sortByNonce(final Collection<Transaction> transactions) {
    final List<Transaction> sortedTransactions = new ArrayList<>(transactions);
    sortedTransactions.sort(Comparator.comparing(Transaction::getNonce));
    return sortedTransactions;
  }

  public void addTransactionListener(final PendingTransactionListener listener) {
    pendingTransactions.addTransactionListener(listener);
  }

  public void addTransactionDroppedListener(final PendingTransactionDroppedListener listener) {
    pendingTransactions.addTransactionDroppedListener(listener);
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event, final Blockchain blockchain) {
    event.getAddedTransactions().forEach(pendingTransactions::transactionAddedToBlock);
    addRemoteTransactions(event.getRemovedTransactions());
  }

  private TransactionValidator getTransactionValidator() {
    return protocolSchedule
        .getByBlockNumber(protocolContext.getBlockchain().getChainHeadBlockNumber())
        .getTransactionValidator();
  }

  public PendingTransactions getPendingTransactions() {
    return pendingTransactions;
  }

  private ValidationResult<TransactionInvalidReason> validateTransaction(
      final Transaction transaction) {
    final ValidationResult<TransactionInvalidReason> basicValidationResult =
        getTransactionValidator().validate(transaction);
    if (!basicValidationResult.isValid()) {
      return basicValidationResult;
    }

    final String sender = transaction.getSender().toString();
    if (accountIsNotPermitted(sender)) {
      return ValidationResult.invalid(
          TransactionInvalidReason.TX_SENDER_NOT_AUTHORIZED,
          String.format("Sender %s is not on the Account Whitelist", sender));
    }

    final BlockHeader chainHeadBlockHeader = getChainHeadBlockHeader();
    if (transaction.getGasLimit() > chainHeadBlockHeader.getGasLimit()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.EXCEEDS_BLOCK_GAS_LIMIT,
          String.format(
              "Transaction gas limit of %s exceeds block gas limit of %s",
              transaction.getGasLimit(), chainHeadBlockHeader.getGasLimit()));
    }

    return protocolContext
        .getWorldStateArchive()
        .get(chainHeadBlockHeader.getStateRoot())
        .map(
            worldState -> {
              final Account senderAccount = worldState.get(transaction.getSender());
              return getTransactionValidator().validateForSender(transaction, senderAccount, true);
            })
        .orElseGet(() -> ValidationResult.invalid(CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE));
  }

  private boolean accountIsNotPermitted(final String account) {
    return accountFilter.map(c -> !c.permitted(account)).orElse(false);
  }

  private BlockHeader getChainHeadBlockHeader() {
    final MutableBlockchain blockchain = protocolContext.getBlockchain();
    return blockchain.getBlockHeader(blockchain.getChainHeadHash()).get();
  }

  public interface TransactionBatchAddedListener {

    void onTransactionsAdded(Iterable<Transaction> transactions);
  }

  public void setAccountFilter(final AccountFilter accountFilter) {
    this.accountFilter = Optional.of(accountFilter);
  }
}
