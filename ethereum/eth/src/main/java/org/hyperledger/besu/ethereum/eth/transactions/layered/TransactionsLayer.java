/*
 * Copyright contributors to Hyperledger Besu.
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Predicate;

public interface TransactionsLayer {

  String name();

  void reset();

  Optional<Transaction> getByHash(Hash transactionHash);

  boolean contains(Transaction transaction);

  /**
   * Try to add a pending transaction to this layer. The {@code addReason} is used to discriminate
   * between a new tx that is added to the pool, or a tx that is already in the pool, but is moving
   * internally between layers, for example, due to a promotion or demotion. The distinction is
   * needed since we only need to send a notification for a new tx, and not when it is only an
   * internal move.
   *
   * @param pendingTransaction the tx to try to add to this layer
   * @param gap the nonce gap between the current sender nonce and the tx
   * @param addReason define if it is a new tx or an internal move
   * @return the result of the add operation
   */
  TransactionAddedResult add(PendingTransaction pendingTransaction, int gap, AddReason addReason);

  void remove(PendingTransaction pendingTransaction, RemovalReason reason);

  /**
   * Penalize a pending transaction. Penalization could be applied to notify the txpool that this
   * pending tx has some temporary issues that prevent it from being included in a block, and so it
   * should be de-prioritized in some ways, so it will be re-evaluated only after non penalized
   * pending txs. For example: if during the evaluation for block inclusion, the pending tx is
   * excluded because the sender has not enough balance to send it, this could be a transient issue
   * since later the sender could receive some funds, but in any case we penalize the pending tx, so
   * it is pushed down in the order of prioritized pending txs.
   *
   * @param penalizedTransaction the tx to penalize
   */
  void penalize(PendingTransaction penalizedTransaction);

  void blockAdded(
      FeeMarket feeMarket,
      BlockHeader blockHeader,
      final Map<Address, Long> maxConfirmedNonceBySender);

  List<PendingTransaction> getAll();

  List<PendingTransaction> getAllFor(Address sender);

  List<Transaction> getAllLocal();

  List<Transaction> getAllPriority();

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
      Predicate<PendingTransaction> promotionFilter,
      final long freeSpace,
      final int freeSlots,
      final int[] remainingPromotionsPerType);

  long subscribeToAdded(PendingTransactionAddedListener listener);

  void unsubscribeFromAdded(long id);

  long subscribeToDropped(PendingTransactionDroppedListener listener);

  void unsubscribeFromDropped(long id);

  PendingTransaction promoteFor(Address sender, long nonce, final int[] remainingPromotionsPerType);

  void notifyAdded(PendingTransaction pendingTransaction);

  long getCumulativeUsedSpace();

  String logStats();

  String logSender(Address sender);

  /** Describe why we are trying to add a tx to a layer. */
  enum AddReason {
    /** When adding a tx, that is not present in the pool. */
    NEW(true, true),
    /** When adding a tx as result of an internal move between layers. */
    MOVE(false, false),
    /** When adding a tx as result of a promotion from a lower layer. */
    PROMOTED(false, false);

    private final boolean sendNotification;
    private final boolean makeCopy;
    private final String label;

    AddReason(final boolean sendNotification, final boolean makeCopy) {
      this.sendNotification = sendNotification;
      this.makeCopy = makeCopy;
      this.label = name().toLowerCase(Locale.ROOT);
    }

    /**
     * Should we send add notification for this reason?
     *
     * @return true if notification should be sent
     */
    public boolean sendNotification() {
      return sendNotification;
    }

    /**
     * Should the layer make a copy of the pending tx before adding it, to avoid keeping reference
     * to potentially large underlying byte buffers?
     *
     * @return true is a copy is necessary
     */
    public boolean makeCopy() {
      return makeCopy;
    }

    public String label() {
      return label;
    }
  }

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
      this.label = name().toLowerCase(Locale.ROOT);
    }

    public String label() {
      return label;
    }
  }
}
