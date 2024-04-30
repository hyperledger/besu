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
package org.hyperledger.besu.ethereum.blockcreation.txselection;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionSelectionResults {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionSelectionResults.class);

  private final List<Transaction> selectedTransactions = Lists.newArrayList();
  private final Map<TransactionType, List<Transaction>> transactionsByType =
      new EnumMap<>(TransactionType.class);
  private final List<TransactionReceipt> receipts = Lists.newArrayList();

  /**
   * Access to this field needs to be guarded, since it is possible to read it while another
   * processing thread is writing, when the selection time is over.
   */
  private final Map<Transaction, TransactionSelectionResult> notSelectedTransactions =
      new ConcurrentHashMap<>();

  private long cumulativeGasUsed = 0;
  private long cumulativeBlobGasUsed = 0;

  void updateSelected(
      final Transaction transaction,
      final TransactionReceipt receipt,
      final long gasUsed,
      final long blobGasUsed) {
    selectedTransactions.add(transaction);
    transactionsByType
        .computeIfAbsent(transaction.getType(), type -> new ArrayList<>())
        .add(transaction);
    receipts.add(receipt);
    cumulativeGasUsed += gasUsed;
    cumulativeBlobGasUsed += blobGasUsed;
    LOG.atTrace()
        .setMessage(
            "New selected transaction {}, total transactions {}, cumulative gas used {}, cumulative blob gas used {}")
        .addArgument(transaction::toTraceLog)
        .addArgument(selectedTransactions::size)
        .addArgument(cumulativeGasUsed)
        .addArgument(cumulativeBlobGasUsed)
        .log();
  }

  public void updateNotSelected(
      final Transaction transaction, final TransactionSelectionResult res) {
    notSelectedTransactions.put(transaction, res);
  }

  public List<Transaction> getSelectedTransactions() {
    return selectedTransactions;
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

  public long getCumulativeBlobGasUsed() {
    return cumulativeBlobGasUsed;
  }

  public Map<Transaction, TransactionSelectionResult> getNotSelectedTransactions() {
    return Map.copyOf(notSelectedTransactions);
  }

  public void logSelectionStats() {
    if (LOG.isDebugEnabled()) {
      final var notSelectedTxs = getNotSelectedTransactions();
      final Map<TransactionSelectionResult, Long> notSelectedStats =
          notSelectedTxs.values().stream()
              .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

      LOG.debug(
          "Selection stats: Totals[Evaluated={}, Selected={}, NotSelected={}, Discarded={}]; Detailed[{}]",
          selectedTransactions.size() + notSelectedTxs.size(),
          selectedTransactions.size(),
          notSelectedTxs.size(),
          notSelectedStats.entrySet().stream()
              .filter(e -> e.getKey().discard())
              .map(Map.Entry::getValue)
              .mapToInt(Long::intValue)
              .sum(),
          notSelectedStats.entrySet().stream()
              .map(e -> e.getKey().toString() + "=" + e.getValue())
              .sorted()
              .collect(Collectors.joining(", ")));
    }
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
        && cumulativeBlobGasUsed == that.cumulativeBlobGasUsed
        && selectedTransactions.equals(that.selectedTransactions)
        && notSelectedTransactions.equals(that.notSelectedTransactions)
        && receipts.equals(that.receipts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        selectedTransactions,
        notSelectedTransactions,
        receipts,
        cumulativeGasUsed,
        cumulativeBlobGasUsed);
  }

  public String toTraceLog() {
    return "cumulativeGasUsed="
        + cumulativeGasUsed
        + ", cumulativeBlobGasUsed="
        + cumulativeBlobGasUsed
        + ", selectedTransactions="
        + selectedTransactions.stream()
            .map(Transaction::toTraceLog)
            .collect(Collectors.joining("; "))
        + ", notSelectedTransactions="
        + notSelectedTransactions.entrySet().stream()
            .map(e -> e.getValue() + ":" + e.getKey().toTraceLog())
            .collect(Collectors.joining(";"));
  }
}
