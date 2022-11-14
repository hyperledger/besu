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
package org.hyperledger.besu.ethereum.eth.transactions.sorter;

import static java.util.Comparator.comparing;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Clock;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Holds the current set of pending transactions with the ability to iterate them based on priority
 * for mining or look-up by hash.
 *
 * <p>This class is safe for use across multiple threads.
 */
public class GasPricePendingTransactionsSorter extends AbstractPendingTransactionsSorter {

  private final NavigableSet<PendingTransaction> prioritizedTransactions =
      new TreeSet<>(
          comparing(PendingTransaction::isReceivedFromLocalSource)
              .thenComparing(PendingTransaction::getGasPrice)
              .thenComparing(PendingTransaction::getAddedToPoolAt)
              .thenComparing(PendingTransaction::getSequence)
              .reversed());

  public GasPricePendingTransactionsSorter(
      final TransactionPoolConfiguration poolConfig,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final Supplier<BlockHeader> chainHeadHeaderSupplier) {
    super(poolConfig, clock, metricsSystem, chainHeadHeaderSupplier);
  }

  @Override
  public void manageBlockAdded(final Block block) {
    // nothing to do
  }

  @Override
  protected Iterator<PendingTransaction> prioritizedTransactions() {
    return prioritizedTransactions.iterator();
  }

  @Override
  protected void prioritizeTransaction(final PendingTransaction pendingTx) {
    prioritizedTransactions.add(pendingTx);
  }

  @Override
  protected void removePrioritizedTransaction(final PendingTransaction removedPendingTx) {
    prioritizedTransactions.remove(removedPendingTx);
  }

  @Override
  protected PendingTransaction getLeastPriorityTransaction() {
    return prioritizedTransactions.last();
  }
}
