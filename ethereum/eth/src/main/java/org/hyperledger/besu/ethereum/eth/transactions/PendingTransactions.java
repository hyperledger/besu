/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.eth.transactions;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.evm.account.Account;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

public interface PendingTransactions {

  void reset();

  void evictOldTransactions();

  List<Transaction> getLocalTransactions();

  TransactionAddedStatus addRemoteTransaction(
      final Transaction transaction, final Optional<Account> maybeSenderAccount);

  TransactionAddedStatus addLocalTransaction(
      final Transaction transaction, final Optional<Account> maybeSenderAccount);

  void removeTransaction(final Transaction transaction);

  void transactionAddedToBlock(final Transaction transaction);

  void selectTransactions(final TransactionSelector selector);

  long maxSize();

  int size();

  boolean containsTransaction(final Hash transactionHash);

  Optional<Transaction> getTransactionByHash(final Hash transactionHash);

  Set<PendingTransaction> getPendingTransactions();

  long subscribePendingTransactions(final PendingTransactionListener listener);

  void unsubscribePendingTransactions(final long id);

  long subscribeDroppedTransactions(final PendingTransactionDroppedListener listener);

  void unsubscribeDroppedTransactions(final long id);

  OptionalLong getNextNonceForSender(final Address sender);

  void manageBlockAdded(final Block block);

  String toTraceLog(final boolean withTransactionsBySender, final boolean withLowestInvalidNonce);

  List<Transaction> signalInvalidAndGetDependentTransactions(final Transaction transaction);

  boolean isLocalSender(final Address sender);

  enum TransactionSelectionResult {
    DELETE_TRANSACTION_AND_CONTINUE,
    CONTINUE,
    COMPLETE_OPERATION
  }

  @FunctionalInterface
  interface TransactionSelector {
    TransactionSelectionResult evaluateTransaction(final Transaction transaction);
  }
}
