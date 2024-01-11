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

import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.EVICTED;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.FOLLOW_INVALIDATED;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.eth.transactions.BlobCache;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;

import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;

public abstract class AbstractSequentialTransactionsLayer extends AbstractTransactionsLayer {

  public AbstractSequentialTransactionsLayer(
      final TransactionPoolConfiguration poolConfig,
      final TransactionsLayer nextLayer,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester,
      final TransactionPoolMetrics metrics,
      final BlobCache blobCache) {
    super(poolConfig, nextLayer, transactionReplacementTester, metrics, blobCache);
  }

  @Override
  public void remove(final PendingTransaction invalidatedTx, final RemovalReason reason) {
    nextLayer.remove(invalidatedTx, reason);

    final var senderTxs = txsBySender.get(invalidatedTx.getSender());
    final long invalidNonce = invalidatedTx.getNonce();
    if (senderTxs != null && invalidNonce <= senderTxs.lastKey()) {
      // on sequential layers we need to push to next layer all the txs following the invalid one,
      // even if it belongs to a previous layer

      if (senderTxs.remove(invalidNonce) != null) {
        // invalid tx removed in this layer
        processRemove(senderTxs, invalidatedTx.getTransaction(), reason);
      }

      // push following to next layer
      pushDown(senderTxs, invalidNonce, 1);

      if (senderTxs.isEmpty()) {
        txsBySender.remove(invalidatedTx.getSender());
      }
    }
  }

  private void pushDown(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final long afterNonce,
      final int gap) {
    senderTxs.tailMap(afterNonce, false).values().stream().toList().stream()
        .peek(
            txToRemove -> {
              senderTxs.remove(txToRemove.getNonce());
              processRemove(senderTxs, txToRemove.getTransaction(), FOLLOW_INVALIDATED);
            })
        .forEach(followingTx -> nextLayer.add(followingTx, gap));
  }

  @Override
  protected boolean gapsAllowed() {
    return false;
  }

  @Override
  protected void internalConfirmed(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final Address sender,
      final long maxConfirmedNonce,
      final PendingTransaction highestNonceRemovedTx) {
    // no -op
  }

  @Override
  protected void internalEvict(
      final NavigableMap<Long, PendingTransaction> senderTxs, final PendingTransaction evictedTx) {
    internalRemove(senderTxs, evictedTx, EVICTED);
  }

  @Override
  public OptionalLong getNextNonceFor(final Address sender) {
    final OptionalLong nextLayerRes = nextLayer.getNextNonceFor(sender);
    if (nextLayerRes.isEmpty()) {
      final var senderTxs = txsBySender.get(sender);
      if (senderTxs != null) {
        return OptionalLong.of(senderTxs.lastKey() + 1);
      }
    }
    return nextLayerRes;
  }

  @Override
  public OptionalLong getCurrentNonceFor(final Address sender) {
    final var senderTxs = txsBySender.get(sender);
    if (senderTxs != null) {
      return OptionalLong.of(senderTxs.firstKey());
    }
    return nextLayer.getCurrentNonceFor(sender);
  }

  @Override
  protected void internalNotifyAdded(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final PendingTransaction pendingTransaction) {
    // no-op
  }

  protected boolean hasExpectedNonce(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final PendingTransaction pendingTransaction,
      final long gap) {
    if (senderTxs == null) {
      return gap == 0;
    }

    // true if prepend or append
    return (senderTxs.lastKey() + 1) == pendingTransaction.getNonce()
        || (senderTxs.firstKey() - 1) == pendingTransaction.getNonce();
  }

  @Override
  protected void internalConsistencyCheck(
      final Map<Address, TreeMap<Long, PendingTransaction>> prevLayerTxsBySender) {
    txsBySender.values().stream()
        .filter(senderTxs -> senderTxs.size() > 1)
        .map(NavigableMap::entrySet)
        .map(Set::iterator)
        .forEach(
            itNonce -> {
              PendingTransaction firstTx = itNonce.next().getValue();

              prevLayerTxsBySender.computeIfPresent(
                  firstTx.getSender(),
                  (sender, txsByNonce) -> {
                    assert txsByNonce.lastKey() + 1 == firstTx.getNonce()
                        : "first nonce is not sequential with previous layer last nonce";
                    return txsByNonce;
                  });

              long prevNonce = firstTx.getNonce();

              while (itNonce.hasNext()) {
                final long currNonce = itNonce.next().getKey();
                assert prevNonce + 1 == currNonce : "non sequential nonce";
                prevNonce = currNonce;
              }
            });
  }
}
