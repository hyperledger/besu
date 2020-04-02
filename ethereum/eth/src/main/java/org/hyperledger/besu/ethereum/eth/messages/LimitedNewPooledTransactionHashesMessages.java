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

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public final class LimitedNewPooledTransactionHashesMessages {

  static final int MAX_COUNT = 4096;

  private final NewPooledTransactionHashesMessage transactionsMessage;
  private final List<Hash> includedTransactions;

  public LimitedNewPooledTransactionHashesMessages(
      final NewPooledTransactionHashesMessage transactionsMessage,
      final List<Hash> includedTransactions) {
    this.transactionsMessage = transactionsMessage;
    this.includedTransactions = includedTransactions;
  }

  public static LimitedNewPooledTransactionHashesMessages createLimited(
      final Iterable<Hash> hashes) {
    final List<Hash> includedTransactions = new ArrayList<>();
    final BytesValueRLPOutput message = new BytesValueRLPOutput();
    int count = 0;
    message.startList();
    for (final Hash txHash : hashes) {
      final BytesValueRLPOutput encodedHashes = new BytesValueRLPOutput();
      encodedHashes.writeBytes(txHash);
      Bytes encodedBytes = encodedHashes.encoded();

      message.writeRLPUnsafe(encodedBytes);
      includedTransactions.add(txHash);
      // Check if last transaction to add to the message
      count++;
      if (count >= MAX_COUNT) {
        break;
      }
    }
    message.endList();
    return new LimitedNewPooledTransactionHashesMessages(
        new NewPooledTransactionHashesMessage(message.encoded()), includedTransactions);
  }

  public final NewPooledTransactionHashesMessage getTransactionsMessage() {
    return transactionsMessage;
  }

  public final List<Hash> getIncludedTransactions() {
    return includedTransactions;
  }
}
