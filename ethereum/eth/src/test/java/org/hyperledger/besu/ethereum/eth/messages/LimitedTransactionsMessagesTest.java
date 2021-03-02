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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class LimitedTransactionsMessagesTest {

  private static final int TX_PAYLOAD_LIMIT = LimitedTransactionsMessages.LIMIT - 180;
  private static final int MAX_ADDITIONAL_BYTES = 5;
  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final Set<Transaction> sampleTxs = generator.transactions(1);
  private final TransactionsMessage sampleTransactionMessages =
      TransactionsMessage.create(sampleTxs);
  private final LimitedTransactionsMessages sampleLimitedTransactionsMessages =
      new LimitedTransactionsMessages(sampleTransactionMessages, sampleTxs);

  @Test
  public void createLimited() {
    final Set<Transaction> transactions = generator.transactions(6000);
    final Set<Transaction> remainingTransactions = new HashSet<>(transactions);

    final LimitedTransactionsMessages firstMessage =
        LimitedTransactionsMessages.createLimited(remainingTransactions);
    remainingTransactions.removeAll(firstMessage.getIncludedTransactions());

    final LimitedTransactionsMessages secondMessage =
        LimitedTransactionsMessages.createLimited(remainingTransactions);
    remainingTransactions.removeAll(secondMessage.getIncludedTransactions());

    assertThat(remainingTransactions.size()).isEqualTo(0);
    List.of(firstMessage, secondMessage).stream()
        .map(message -> message.getTransactionsMessage().getSize())
        .forEach(
            messageSize ->
                assertThat(messageSize)
                    .isLessThan(LimitedTransactionsMessages.LIMIT + MAX_ADDITIONAL_BYTES));

    final Set<Transaction> includedTransactions = new HashSet<>();
    includedTransactions.addAll(firstMessage.getIncludedTransactions());
    includedTransactions.addAll(secondMessage.getIncludedTransactions());
    assertThat(includedTransactions)
        .containsExactlyInAnyOrder(transactions.toArray(new Transaction[] {}));
  }

  @Test
  public void createLimitedWithTransactionsJustUnderTheLimit() {
    final Set<Transaction> transactions =
        Stream.generate(() -> generator.transaction(Bytes.wrap(new byte[TX_PAYLOAD_LIMIT])))
            .limit(3)
            .collect(Collectors.toUnmodifiableSet());
    final Set<Transaction> remainingTransactions = new HashSet<>(transactions);
    final Set<Transaction> includedTransactions = new HashSet<>();
    while (!remainingTransactions.isEmpty()) {
      final LimitedTransactionsMessages message =
          LimitedTransactionsMessages.createLimited(remainingTransactions);
      includedTransactions.addAll(message.getIncludedTransactions());
      assertThat(message.getIncludedTransactions().size()).isEqualTo(1);
      remainingTransactions.removeAll(message.getIncludedTransactions());
    }
    assertThat(includedTransactions)
        .containsExactlyInAnyOrder(transactions.toArray(new Transaction[] {}));
  }

  @Test
  public void createLimitedWithTransactionsJustUnderAndJustOverTheLimit() {
    final Set<Transaction> transactions =
        Set.of(
            generator.transaction(Bytes.wrap(new byte[TX_PAYLOAD_LIMIT])),
            // ensure the next transaction exceed the limit TX_PAYLOAD_LIMIT + 100
            generator.transaction(Bytes.wrap(new byte[TX_PAYLOAD_LIMIT + 100])),
            generator.transaction(Bytes.wrap(new byte[TX_PAYLOAD_LIMIT])));
    final Set<Transaction> remainingTransactions = new LinkedHashSet<>(transactions);

    final Set<Transaction> includedTransactions = new HashSet<>();
    while (!remainingTransactions.isEmpty()) {
      final LimitedTransactionsMessages message =
          LimitedTransactionsMessages.createLimited(remainingTransactions);
      includedTransactions.addAll(message.getIncludedTransactions());
      assertThat(message.getIncludedTransactions().size()).isEqualTo(1);
      remainingTransactions.removeAll(message.getIncludedTransactions());
    }
    assertThat(includedTransactions)
        .containsExactlyInAnyOrder(transactions.toArray(new Transaction[] {}));
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
