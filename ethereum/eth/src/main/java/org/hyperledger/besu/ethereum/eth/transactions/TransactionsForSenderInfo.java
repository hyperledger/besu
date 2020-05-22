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

import java.util.Iterator;
import java.util.OptionalLong;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.LongStream;

class TransactionsForSenderInfo {
  private final SortedMap<Long, PendingTransactions.TransactionInfo> transactionsInfos;
  private final Queue<Long> gaps = new PriorityQueue<>();

  TransactionsForSenderInfo() {
    transactionsInfos = new TreeMap<>();
  }

  void addTransactionToTrack(
      final long nonce, final PendingTransactions.TransactionInfo transactionInfo) {
    synchronized (transactionsInfos) {
      if (!transactionsInfos.isEmpty()) {
        final long highestNonce = transactionsInfos.lastKey();
        if (nonce > (highestNonce + 1)) {
          LongStream.range(highestNonce + 1, nonce).forEach(gaps::add);
        }
      }
      transactionsInfos.put(nonce, transactionInfo);
    }
  }

  void updateGaps() {
    synchronized (transactionsInfos) {
      final Iterator<Long> nonceIterator = transactionsInfos.keySet().iterator();
      long previousNonce = -1;
      while (nonceIterator.hasNext()) {
        final long currentNonce = nonceIterator.next();
        LongStream.range(previousNonce + 1, currentNonce).forEach(gaps::add);
        previousNonce = currentNonce;
      }
    }
  }

  SortedMap<Long, PendingTransactions.TransactionInfo> getTransactionsInfos() {
    return transactionsInfos;
  }

  OptionalLong maybeNextGap() {
    synchronized (transactionsInfos) {
      return gaps.isEmpty() ? OptionalLong.empty() : OptionalLong.of(gaps.poll());
    }
  }
}
