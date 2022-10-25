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

import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionInfo;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountState;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TransactionsForSenderInfo {
  private final NavigableMap<Long, TransactionInfo> transactionsInfos;
  private long senderNonce;
  private long minNonceDistance = Long.MAX_VALUE;

  public TransactionsForSenderInfo(final Optional<Account> maybeSenderAccount) {
    this.transactionsInfos = new TreeMap<>();
    this.senderNonce = maybeSenderAccount.map(AccountState::getNonce).orElse(0L);
  }

  public void addTransactionToTrack(
      final TransactionInfo transactionInfo, final Optional<Account> maybeSenderAccount) {
    synchronized (transactionsInfos) {
      transactionsInfos.put(transactionInfo.getNonce(), transactionInfo);
      updateSenderNonce(maybeSenderAccount.map(AccountState::getNonce).orElse(0L));
    }
  }

  public void removeTrackedTransactionInfo(
      final TransactionInfo txInfo, final boolean addedToBlock) {
    synchronized (transactionsInfos) {
      if (addedToBlock) {
        transactionsInfos.remove(txInfo.getNonce());
        updateSenderNonce(txInfo.getNonce());
      } else {
        // check the value when removing, because it could have been replaced
        transactionsInfos.remove(txInfo.getNonce(), txInfo);
      }
    }
  }

  public long getSenderNonce() {
    return senderNonce;
  }

  public long getMinNonceDistance() {
    return minNonceDistance;
  }

  private void updateSenderNonce(final long nonce) {
    if (nonce > senderNonce) {
      senderNonce = nonce;
      updateMinNonceDistance();
    }
  }

  private void updateMinNonceDistance() {
    var firstEntry = transactionsInfos.firstEntry();
    if (firstEntry != null) {
      minNonceDistance = firstEntry.getKey() - senderNonce;
    } else {
      minNonceDistance = Long.MAX_VALUE;
    }
  }

  private long findNonceGap() {
    // find first gap
    long expectedValue = senderNonce;
    for (final Long nonce : transactionsInfos.keySet()) {
      if (expectedValue == nonce) {
        // no gap, keep moving
        expectedValue++;
      } else {
        break;
      }
    }
    return expectedValue;
  }

  public OptionalLong maybeNextNonce() {
    if (transactionsInfos.isEmpty()) {
      return OptionalLong.empty();
    } else {
      return OptionalLong.of(findNonceGap());
    }
  }

  public Optional<TransactionInfo> maybeLastTx() {
    return Optional.ofNullable(transactionsInfos.lastEntry()).map(Map.Entry::getValue);
  }

  public int transactionCount() {
    return transactionsInfos.size();
  }

  public Stream<TransactionInfo> streamTransactionInfos() {
    return transactionsInfos.values().stream();
  }

  public TransactionInfo getTransactionInfoForNonce(final long nonce) {
    return transactionsInfos.get(nonce);
  }

  public String toTraceLog() {
    return "{"
        + "senderNonce "
        + senderNonce
        + "minNonceDistance "
        + minNonceDistance
        + ", transactions "
        + transactionsInfos.entrySet().stream()
            .map(e -> "(" + e.getKey() + ")" + e.getValue().toTraceLog())
            .collect(Collectors.joining("; "))
        + '}';
  }
}
