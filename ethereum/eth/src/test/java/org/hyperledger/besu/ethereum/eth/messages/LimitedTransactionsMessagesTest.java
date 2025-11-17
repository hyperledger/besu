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
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class LimitedTransactionsMessagesTest {
  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final Set<Transaction> sampleTxs = generator.transactions(1);
  private final TransactionsMessage sampleTransactionMessages =
      TransactionsMessage.create(sampleTxs);
  private final LimitedTransactionsMessages sampleLimitedTransactionsMessages =
      new LimitedTransactionsMessages(sampleTransactionMessages, sampleTxs);
  private final int maxTransactionsMessageSize =
      EthProtocolConfiguration.DEFAULT.getMaxTransactionsMessageSize();
  private final int safeTxPayloadSize = maxTransactionsMessageSize / 2 + 1;

  @Test
  public void createLimited() {
    final Set<Transaction> transactions = generator.transactions(6000);
    final Set<Transaction> remainingTransactions = new HashSet<>(transactions);

    final LimitedTransactionsMessages firstMessage =
        LimitedTransactionsMessages.createLimited(
            remainingTransactions, maxTransactionsMessageSize);
    remainingTransactions.removeAll(firstMessage.getIncludedTransactions());

    final LimitedTransactionsMessages secondMessage =
        LimitedTransactionsMessages.createLimited(
            remainingTransactions, maxTransactionsMessageSize);
    remainingTransactions.removeAll(secondMessage.getIncludedTransactions());

    assertThat(remainingTransactions.size()).isEqualTo(0);
    List.of(firstMessage, secondMessage).stream()
        .map(message -> message.getTransactionsMessage().getSize())
        .forEach(messageSize -> assertThat(messageSize).isLessThan(maxTransactionsMessageSize));

    final Set<Transaction> includedTransactions = new HashSet<>();
    includedTransactions.addAll(firstMessage.getIncludedTransactions());
    includedTransactions.addAll(secondMessage.getIncludedTransactions());
    assertThat(includedTransactions)
        .containsExactlyInAnyOrder(transactions.toArray(new Transaction[] {}));
  }

  @Test
  public void createLimitedWithTransactionsJustUnderTheLimit() {
    final Set<Transaction> transactions =
        Stream.generate(() -> generator.transaction(Bytes.wrap(new byte[safeTxPayloadSize])))
            .limit(3)
            .collect(Collectors.toUnmodifiableSet());
    final Set<Transaction> remainingTransactions = new HashSet<>(transactions);
    final Set<Transaction> includedTransactions = new HashSet<>();
    while (!remainingTransactions.isEmpty()) {
      final LimitedTransactionsMessages message =
          LimitedTransactionsMessages.createLimited(
              remainingTransactions, maxTransactionsMessageSize);
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
