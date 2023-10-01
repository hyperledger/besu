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
package org.hyperledger.besu.ethereum.eth.transactions.layered;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionAddedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionDroppedListener;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Predicate;

public interface TransactionsLayer {

  String name();

  void reset();

  Optional<Transaction> getByHash(Hash transactionHash);

  boolean contains(Transaction transaction);

  List<PendingTransaction> getAll();

  TransactionAddedResult add(PendingTransaction pendingTransaction, int gap);

  void remove(PendingTransaction pendingTransaction, RemovalReason reason);

  void blockAdded(
      FeeMarket feeMarket,
      BlockHeader blockHeader,
      final Map<Address, Long> maxConfirmedNonceBySender);

  List<Transaction> getAllLocal();

  int count();

  OptionalLong getNextNonceFor(Address sender);

  /**
   * Get the sender nonce has seen by this and the following layers
   *
   * @param sender the sender for which retrieve the txpool
   * @return either the sender nonce or empty if the sender is unknown to this or the following
   *     layers
   */
  OptionalLong getCurrentNonceFor(Address sender);

  List<PendingTransaction> promote(
      Predicate<PendingTransaction> promotionFilter, final long freeSpace, final int freeSlots);

  long subscribeToAdded(PendingTransactionAddedListener listener);

  void unsubscribeFromAdded(long id);

  long subscribeToDropped(PendingTransactionDroppedListener listener);

  void unsubscribeFromDropped(long id);

  PendingTransaction promoteFor(Address sender, long nonce);

  void notifyAdded(PendingTransaction pendingTransaction);

  long getCumulativeUsedSpace();

  String logStats();

  String logSender(Address sender);

  List<PendingTransaction> getAllFor(Address sender);

  enum RemovalReason {
    CONFIRMED,
    CROSS_LAYER_REPLACED,
    EVICTED,
    DROPPED,
    FOLLOW_INVALIDATED,
    INVALIDATED,
    PROMOTED,
    REPLACED,
    RECONCILED,
    BELOW_BASE_FEE;

    private final String label;

    RemovalReason() {
      this.label = name().toLowerCase();
    }

    public String label() {
      return label;
    }
  }
}
