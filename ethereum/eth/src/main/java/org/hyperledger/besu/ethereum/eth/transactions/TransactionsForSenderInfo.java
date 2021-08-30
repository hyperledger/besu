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

package org.hyperledger.besu.ethereum.eth.transactions;

import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionInfo;

import java.util.NavigableMap;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.stream.Stream;

public class TransactionsForSenderInfo {
  private final NavigableMap<Long, AbstractPendingTransactionsSorter.TransactionInfo>
      transactionsInfos;
  private OptionalLong nextGap = OptionalLong.empty();

  public TransactionsForSenderInfo() {
    transactionsInfos = new TreeMap<>();
  }

  public void addTransactionToTrack(
      final long nonce, final AbstractPendingTransactionsSorter.TransactionInfo transactionInfo) {
    synchronized (transactionsInfos) {
      if (!transactionsInfos.isEmpty()) {
        final long expectedNext = transactionsInfos.lastKey() + 1;
        if (nonce > (expectedNext) && nextGap.isEmpty()) {
          nextGap = OptionalLong.of(expectedNext);
        }
      }
      transactionsInfos.put(nonce, transactionInfo);
      if (nonce == nextGap.orElse(-1)) {
        findGap();
      }
    }
  }

  public void removeTrackedTransaction(final long nonce) {
    transactionsInfos.remove(nonce);
    synchronized (transactionsInfos) {
      if (!transactionsInfos.isEmpty() && nonce != transactionsInfos.firstKey()) {
        findGap();
      }
    }
  }

  private void findGap() {
    // find first gap
    long expectedValue = transactionsInfos.firstKey();
    for (final Long nonce : transactionsInfos.keySet()) {
      if (expectedValue == nonce) {
        // no gap, keep moving
        expectedValue++;
      } else {
        nextGap = OptionalLong.of(expectedValue);
        return;
      }
    }
    nextGap = OptionalLong.empty();
  }

  public OptionalLong maybeNextNonce() {
    if (transactionsInfos.isEmpty()) {
      return OptionalLong.empty();
    } else {
      return nextGap.isEmpty() ? OptionalLong.of(transactionsInfos.lastKey() + 1) : nextGap;
    }
  }

  public Stream<TransactionInfo> streamTransactionInfos() {
    return transactionsInfos.values().stream();
  }

  public TransactionInfo getTransactionInfoForNonce(final long nonce) {
    return transactionsInfos.get(nonce);
  }
}
