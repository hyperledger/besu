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
package org.hyperledger.besu.ethereum.eth.messages;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.HashSet;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LimitedTransactionsMessages {
  private static final Logger LOG = LoggerFactory.getLogger(LimitedTransactionsMessages.class);

  private final TransactionsMessage transactionsMessage;
  private final Set<Transaction> includedTransactions;

  LimitedTransactionsMessages(
      final TransactionsMessage transactionsMessage, final Set<Transaction> includedTransactions) {
    this.transactionsMessage = transactionsMessage;
    this.includedTransactions = includedTransactions;
  }

  public static LimitedTransactionsMessages createLimited(
      final Set<Transaction> transactions, final int maxTransactionsMessageSize) {
    final Set<Transaction> includedTransactions = HashSet.newHashSet(transactions.size());
    final BytesValueRLPOutput message = new BytesValueRLPOutput();
    int estimatedMsgSize = RLP.MAX_PREFIX_SIZE;
    message.startList();
    for (final Transaction transaction : transactions) {
      final BytesValueRLPOutput encodedTransaction = new BytesValueRLPOutput();
      transaction.writeTo(encodedTransaction);
      final Bytes encodedBytes = encodedTransaction.encoded();
      if (estimatedMsgSize + encodedBytes.size() > maxTransactionsMessageSize) {
        break;
      }
      message.writeRaw(encodedBytes);
      includedTransactions.add(transaction);
      // Check if last transaction to add to the message
      estimatedMsgSize += encodedBytes.size();
    }
    message.endList();
    LOG.atTrace()
        .setMessage(
            "Transactions message created with {} txs included out of {} txs available, message size {}")
        .addArgument(includedTransactions::size)
        .addArgument(transactions::size)
        .addArgument(message::encodedSize)
        .log();
    return new LimitedTransactionsMessages(
        new TransactionsMessage(message.encoded()), includedTransactions);
  }

  public TransactionsMessage getTransactionsMessage() {
    return transactionsMessage;
  }

  public Set<Transaction> getIncludedTransactions() {
    return includedTransactions;
  }
}
