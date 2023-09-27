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

import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.INVALIDATED;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.PROMOTED;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class SparseTransactions extends AbstractTransactionsLayer {
  private final NavigableSet<PendingTransaction> sparseEvictionOrder =
      new TreeSet<>(
          Comparator.comparing(PendingTransaction::hasPriority)
              .thenComparing(PendingTransaction::getSequence));
  private final Map<Address, Integer> gapBySender = new HashMap<>();
  private final List<Set<Address>> orderByGap;

  public SparseTransactions(
      final TransactionPoolConfiguration poolConfig,
      final TransactionsLayer nextLayer,
      final TransactionPoolMetrics metrics,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {
    super(poolConfig, nextLayer, transactionReplacementTester, metrics);
    orderByGap = new ArrayList<>(poolConfig.getMaxFutureBySender());
    IntStream.range(0, poolConfig.getMaxFutureBySender())
        .forEach(i -> orderByGap.add(new HashSet<>()));
  }

  @Override
  public String name() {
    return "sparse";
  }

  @Override
  protected long cacheFreeSpace() {
    return poolConfig.getPendingTransactionsLayerMaxCapacityBytes() - getLayerSpaceUsed();
  }

  @Override
  protected boolean gapsAllowed() {
    return true;
  }

  @Override
  public void reset() {
    super.reset();
    sparseEvictionOrder.clear();
    gapBySender.clear();
    orderByGap.forEach(Set::clear);
  }

  @Override
  protected TransactionAddedResult canAdd(
      final PendingTransaction pendingTransaction, final int gap) {
    gapBySender.compute(
        pendingTransaction.getSender(),
        (sender, currGap) -> {
          if (currGap == null) {
            orderByGap.get(gap).add(sender);
            return gap;
          }
          if (pendingTransaction.getNonce() < txsBySender.get(sender).firstKey()) {
            orderByGap.get(currGap).remove(sender);
            orderByGap.get(gap).add(sender);
            return gap;
          }
          return currGap;
        });

    return TransactionAddedResult.ADDED;
  }

  @Override
  protected void internalAdd(
      final NavigableMap<Long, PendingTransaction> senderTxs, final PendingTransaction addedTx) {
    sparseEvictionOrder.add(addedTx);
  }

  @Override
  protected int maxTransactionsNumber() {
    return Integer.MAX_VALUE;
  }

  @Override
  protected void internalReplaced(final PendingTransaction replacedTx) {
    sparseEvictionOrder.remove(replacedTx);
  }

  @Override
  protected void internalBlockAdded(final BlockHeader blockHeader, final FeeMarket feeMarket) {}

  /**
   * We only want to promote transactions that have gap == 0, so there will be no gap in the prev
   * layers. A promoted transaction is removed from this layer, and the gap data is updated for its
   * sender.
   *
   * @param promotionFilter the prev layer's promotion filter
   * @param freeSpace max amount of memory promoted txs can occupy
   * @param freeSlots max number of promoted txs
   * @return a list of transactions promoted to the prev layer
   */
  @Override
  public List<PendingTransaction> promote(
      final Predicate<PendingTransaction> promotionFilter,
      final long freeSpace,
      final int freeSlots) {
    long accumulatedSpace = 0;
    final List<PendingTransaction> promotedTxs = new ArrayList<>();

    final var zeroGapSenders = orderByGap.get(0);

    search:
    for (final var sender : zeroGapSenders) {
      final var senderSeqTxs = getSequentialSubset(txsBySender.get(sender));

      for (final var candidateTx : senderSeqTxs.values()) {

        if (promotionFilter.test(candidateTx)) {
          accumulatedSpace += candidateTx.memorySize();
          if (promotedTxs.size() < freeSlots && accumulatedSpace <= freeSpace) {
            promotedTxs.add(candidateTx);
          } else {
            // no room for more txs the search is over exit the loops
            break search;
          }
        } else {
          // skip remaining txs for this sender
          break;
        }
      }
    }

    // remove promoted txs from this layer
    promotedTxs.forEach(
        promotedTx -> {
          final var sender = promotedTx.getSender();
          final var senderTxs = txsBySender.get(sender);
          senderTxs.remove(promotedTx.getNonce());
          processRemove(senderTxs, promotedTx.getTransaction(), PROMOTED);
          if (senderTxs.isEmpty()) {
            txsBySender.remove(sender);
            orderByGap.get(0).remove(sender);
            gapBySender.remove(sender);
          } else {
            final long firstNonce = senderTxs.firstKey();
            final int newGap = (int) (firstNonce - (promotedTx.getNonce() + 1));
            if (newGap != 0) {
              updateGap(sender, 0, newGap);
            }
          }
        });

    if (!promotedTxs.isEmpty()) {
      // since we removed some txs we can try to promote from next layer
      promoteTransactions();
    }

    return promotedTxs;
  }

  private NavigableMap<Long, PendingTransaction> getSequentialSubset(
      final NavigableMap<Long, PendingTransaction> senderTxs) {
    long lastSequentialNonce = senderTxs.firstKey();
    for (final long nonce : senderTxs.tailMap(lastSequentialNonce, false).keySet()) {
      if (nonce == lastSequentialNonce + 1) {
        ++lastSequentialNonce;
      } else {
        break;
      }
    }
    return senderTxs.headMap(lastSequentialNonce, true);
  }

  @Override
  public void remove(final PendingTransaction invalidatedTx, final RemovalReason reason) {

    final var senderTxs = txsBySender.get(invalidatedTx.getSender());
    if (senderTxs != null && senderTxs.containsKey(invalidatedTx.getNonce())) {
      // gaps are allowed here then just remove
      senderTxs.remove(invalidatedTx.getNonce());
      processRemove(senderTxs, invalidatedTx.getTransaction(), reason);
      if (senderTxs.isEmpty()) {
        txsBySender.remove(invalidatedTx.getSender());
      }
    } else {
      nextLayer.remove(invalidatedTx, reason);
    }
  }

  @Override
  protected void internalConfirmed(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final Address sender,
      final long maxConfirmedNonce,
      final PendingTransaction highestNonceRemovedTx) {

    if (highestNonceRemovedTx != null) {
      final int currGap = gapBySender.get(sender);
      final int newGap = (int) (senderTxs.firstKey() - (highestNonceRemovedTx.getNonce() + 1));
      if (currGap != newGap) {
        updateGap(sender, currGap, newGap);
      }
    } else {
      final int currGap = gapBySender.get(sender);
      final int newGap = (int) (senderTxs.firstKey() - (maxConfirmedNonce + 1));
      if (newGap < currGap) {
        updateGap(sender, currGap, newGap);
      }
    }
  }

  @Override
  protected void internalEvict(
      final NavigableMap<Long, PendingTransaction> lessReadySenderTxs,
      final PendingTransaction evictedTx) {
    sparseEvictionOrder.remove(evictedTx);

    if (lessReadySenderTxs.isEmpty()) {
      deleteGap(evictedTx.getSender());
    }
  }

  @Override
  protected void internalRemove(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final PendingTransaction removedTx,
      final RemovalReason removalReason) {

    sparseEvictionOrder.remove(removedTx);

    final Address sender = removedTx.getSender();

    if (senderTxs != null && !senderTxs.isEmpty()) {
      final int deltaGap = (int) (senderTxs.firstKey() - removedTx.getNonce());
      if (deltaGap > 0) {
        final int currGap = gapBySender.get(sender);
        final int newGap;
        if (removalReason.equals(INVALIDATED)) {
          newGap = currGap + deltaGap;
        } else {
          newGap = deltaGap - 1;
        }
        if (currGap != newGap) {
          updateGap(sender, currGap, newGap);
        }
      }

    } else {
      deleteGap(sender);
    }
  }

  private void deleteGap(final Address sender) {
    orderByGap.get(gapBySender.remove(sender)).remove(sender);
  }

  @Override
  protected PendingTransaction getEvictable() {
    return sparseEvictionOrder.first();
  }

  @Override
  protected boolean promotionFilter(final PendingTransaction pendingTransaction) {
    return false;
  }

  @Override
  public Stream<PendingTransaction> stream() {
    return sparseEvictionOrder.descendingSet().stream();
  }

  @Override
  public OptionalLong getNextNonceFor(final Address sender) {
    final Integer gap = gapBySender.get(sender);
    if (gap != null && gap == 0) {
      final var senderTxs = txsBySender.get(sender);
      var currNonce = senderTxs.firstKey();
      for (final var nextNonce : senderTxs.keySet()) {
        if (nextNonce > currNonce + 1) {
          break;
        }
        currNonce = nextNonce;
      }
      return OptionalLong.of(currNonce + 1);
    }
    return OptionalLong.empty();
  }

  @Override
  public OptionalLong getCurrentNonceFor(final Address sender) {
    final var senderTxs = txsBySender.get(sender);
    if (senderTxs != null) {
      final var gap = gapBySender.get(sender);
      return OptionalLong.of(senderTxs.firstKey() - gap);
    }
    return nextLayer.getCurrentNonceFor(sender);
  }

  @Override
  protected void internalNotifyAdded(
      final NavigableMap<Long, PendingTransaction> senderTxs,
      final PendingTransaction pendingTransaction) {
    final Address sender = pendingTransaction.getSender();
    final Integer currGap = gapBySender.get(sender);
    if (currGap != null) {
      final int newGap = (int) (senderTxs.firstKey() - (pendingTransaction.getNonce() + 1));
      if (newGap < currGap) {
        updateGap(sender, currGap, newGap);
      }
    }
  }

  @Override
  public String logSender(final Address sender) {
    final var senderTxs = txsBySender.get(sender);
    return name()
        + "["
        + (Objects.isNull(senderTxs)
            ? "Empty"
            : "gap(" + gapBySender.get(sender) + ") " + senderTxs.keySet())
        + "] "
        + nextLayer.logSender(sender);
  }

  @Override
  public String internalLogStats() {
    if (sparseEvictionOrder.isEmpty()) {
      return "Sparse: Empty";
    }

    final Transaction newest = sparseEvictionOrder.last().getTransaction();
    final Transaction oldest = sparseEvictionOrder.first().getTransaction();

    return "Sparse: "
        + "count="
        + pendingTransactions.size()
        + ", space used: "
        + spaceUsed
        + ", unique senders: "
        + txsBySender.size()
        + ", oldest [gap: "
        + gapBySender.get(oldest.getSender())
        + ", max fee:"
        + oldest.getMaxGasPrice().toHumanReadableString()
        + ", hash: "
        + oldest.getHash()
        + "], newest [gap: "
        + gapBySender.get(newest.getSender())
        + ", max fee: "
        + newest.getMaxGasPrice().toHumanReadableString()
        + ", hash: "
        + newest.getHash()
        + "]";
  }

  private void updateGap(final Address sender, final int currGap, final int newGap) {
    orderByGap.get(currGap).remove(sender);
    orderByGap.get(newGap).add(sender);
    gapBySender.put(sender, newGap);
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
                    final long prevLayerMaxNonce = txsByNonce.lastKey();
                    assert prevLayerMaxNonce < firstTx.getNonce()
                        : "first nonce is not greater than previous layer last nonce";

                    final int gap = (int) (firstTx.getNonce() - (prevLayerMaxNonce + 1));
                    assert gapBySender.get(firstTx.getSender()).equals(gap) : "gap mismatch";
                    assert orderByGap.get(gap).contains(firstTx.getSender())
                        : "orderByGap sender not found";

                    return txsByNonce;
                  });

              long prevNonce = firstTx.getNonce();

              while (itNonce.hasNext()) {
                final long currNonce = itNonce.next().getKey();
                assert prevNonce < currNonce : "non incremental nonce";
                prevNonce = currNonce;
              }
            });
  }
}
