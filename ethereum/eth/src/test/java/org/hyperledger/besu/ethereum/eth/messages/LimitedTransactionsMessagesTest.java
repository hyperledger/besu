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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class LimitedTransactionsMessagesTest {

  private static final int TX_PAYLOAD_LIMIT = LimitedTransactionsMessages.LIMIT - 180;
  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final Set<Transaction> sampleTxs = generator.transactions(1);
  private final TransactionsMessage sampleTransactionMessages =
      TransactionsMessage.create(sampleTxs);
  private final LimitedTransactionsMessages sampleLimitedTransactionsMessages =
      new LimitedTransactionsMessages(sampleTransactionMessages, sampleTxs);

  @Test
  public void createLimited() {
    final Set<Transaction> txs = new HashSet<>(generator.transactions(6000));
    final LimitedTransactionsMessages firstMessage = LimitedTransactionsMessages.createLimited(txs);

    txs.removeAll(firstMessage.getIncludedTransactions());
    final LimitedTransactionsMessages secondMessage =
        LimitedTransactionsMessages.createLimited(txs);
    txs.removeAll(secondMessage.getIncludedTransactions());
    assertThat(txs.size()).isEqualTo(0);
    List.of(firstMessage, secondMessage).stream()
        .map(message -> message.getTransactionsMessage().getSize())
        .forEach(
            messageSize -> assertThat(messageSize).isLessThan(LimitedTransactionsMessages.LIMIT));
  }

  @Test
  public void createLimitedWithTransactionsJustUnderTheLimit() {
    final Set<Transaction> txs = new HashSet<>();
    txs.add(
        generator.transaction(TransactionType.FRONTIER, Bytes.wrap(new byte[TX_PAYLOAD_LIMIT])));
    txs.add(
        generator.transaction(TransactionType.FRONTIER, Bytes.wrap(new byte[TX_PAYLOAD_LIMIT])));
    txs.add(
        generator.transaction(TransactionType.FRONTIER, Bytes.wrap(new byte[TX_PAYLOAD_LIMIT])));
    while (!txs.isEmpty()) {
      final LimitedTransactionsMessages message = LimitedTransactionsMessages.createLimited(txs);
      assertThat(message.getIncludedTransactions().size()).isEqualTo(1);
      txs.removeAll(message.getIncludedTransactions());
    }
  }

  @Test
  public void createLimitedWithTransactionsJustUnderAndJustOverTheLimit() {
    final Set<Transaction> txs = new LinkedHashSet<>();

    txs.add(
        generator.transaction(TransactionType.FRONTIER, Bytes.wrap(new byte[TX_PAYLOAD_LIMIT])));
    // ensure the next transaction exceed the limit TX_PAYLOAD_LIMIT + 100
    txs.add(
        generator.transaction(
            TransactionType.FRONTIER, Bytes.wrap(new byte[TX_PAYLOAD_LIMIT + 100])));
    txs.add(
        generator.transaction(TransactionType.FRONTIER, Bytes.wrap(new byte[TX_PAYLOAD_LIMIT])));
    while (!txs.isEmpty()) {
      final LimitedTransactionsMessages message = LimitedTransactionsMessages.createLimited(txs);
      assertThat(message.getIncludedTransactions().size()).isEqualTo(1);
      txs.removeAll(message.getIncludedTransactions());
    }
  }

  @Test
  public void getTransactionsMessage() {
    assertThat(sampleLimitedTransactionsMessages.getTransactionsMessage())
        .isEqualTo(sampleTransactionMessages);
  }

  @Test
  public void getIncludedTransactions() {
    assertThat(sampleLimitedTransactionsMessages.getIncludedTransactions()).isEqualTo(sampleTxs);
  }
}
