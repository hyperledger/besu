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

import java.util.HashSet;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;

public final class LimitedTransactionsMessages {

  static final int LIMIT = 1048576;

  private final TransactionsMessage transactionsMessage;
  private final Set<Transaction> includedTransactions;

  public LimitedTransactionsMessages(
      final TransactionsMessage transactionsMessage, final Set<Transaction> includedTransactions) {
    this.transactionsMessage = transactionsMessage;
    this.includedTransactions = includedTransactions;
  }

  public static LimitedTransactionsMessages createLimited(
      final Iterable<Transaction> transactions) {
    final Set<Transaction> includedTransactions = new HashSet<>();
    final BytesValueRLPOutput message = new BytesValueRLPOutput();
    int messageSize = 0;
    message.startList();
    for (final Transaction transaction : transactions) {
      final BytesValueRLPOutput encodedTransaction = new BytesValueRLPOutput();
      transaction.writeTo(encodedTransaction);
      final Bytes encodedBytes = encodedTransaction.encoded();
      if (messageSize != 0 // always at least one message
          && messageSize + encodedBytes.size() > LIMIT) {
        break;
      }
      message.writeRaw(encodedBytes);
      includedTransactions.add(transaction);
      // Check if last transaction to add to the message
      messageSize += encodedBytes.size();
    }
    message.endList();
    return new LimitedTransactionsMessages(
        new TransactionsMessage(message.encoded()), includedTransactions);
  }

  public final TransactionsMessage getTransactionsMessage() {
    return transactionsMessage;
  }

  public final Set<Transaction> getIncludedTransactions() {
    return includedTransactions;
  }
}
