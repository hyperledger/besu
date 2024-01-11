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

import static java.util.Comparator.comparing;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.transactions.BlobCache;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.function.BiFunction;

/**
 * Holds the current set of pending transactions with the ability to iterate them based on priority
 * for mining or look-up by hash.
 *
 * <p>This class is safe for use across multiple threads.
 */
public class GasPricePrioritizedTransactions extends AbstractPrioritizedTransactions {

  public GasPricePrioritizedTransactions(
      final TransactionPoolConfiguration poolConfig,
      final TransactionsLayer nextLayer,
      final TransactionPoolMetrics metrics,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester,
      final BlobCache blobCache) {
    super(poolConfig, nextLayer, metrics, transactionReplacementTester, blobCache);
  }

  @Override
  protected int compareByFee(final PendingTransaction pt1, final PendingTransaction pt2) {
    return comparing(PendingTransaction::hasPriority)
        .thenComparing(PendingTransaction::getGasPrice)
        .thenComparing(PendingTransaction::getSequence)
        .compare(pt1, pt2);
  }

  @Override
  protected void internalBlockAdded(final BlockHeader blockHeader, final FeeMarket feeMarket) {
    // no-op
  }

  @Override
  protected boolean promotionFilter(final PendingTransaction pendingTransaction) {
    return true;
  }

  @Override
  public String internalLogStats() {
    if (orderByFee.isEmpty()) {
      return "GasPrice Prioritized: Empty";
    }

    return "GasPrice Prioritized: "
        + "count: "
        + pendingTransactions.size()
        + " space used: "
        + spaceUsed
        + " unique senders: "
        + txsBySender.size()
        + ", highest fee tx: "
        + orderByFee.last().getTransaction().getGasPrice().get().toHumanReadableString()
        + ", lowest fee tx: "
        + orderByFee.first().getTransaction().getGasPrice().get().toHumanReadableString();
  }
}
