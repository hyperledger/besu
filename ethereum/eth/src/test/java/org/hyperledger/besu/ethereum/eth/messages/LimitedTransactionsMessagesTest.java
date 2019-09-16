/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.eth.messages;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

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
    final Set<Transaction> txs = generator.transactions(6000);
    final LimitedTransactionsMessages firstMessage = LimitedTransactionsMessages.createLimited(txs);
    assertThat(firstMessage.getIncludedTransactions().size()).isBetween(4990, 5250);

    txs.removeAll(firstMessage.getIncludedTransactions());
    assertThat(txs.size()).isBetween(700, 800);
    final LimitedTransactionsMessages secondMessage =
        LimitedTransactionsMessages.createLimited(txs);
    assertThat(secondMessage.getIncludedTransactions().size()).isBetween(700, 800);
    txs.removeAll(secondMessage.getIncludedTransactions());
    assertThat(txs.size()).isEqualTo(0);
    assertThat(
            firstMessage.getTransactionsMessage().getSize()
                + secondMessage.getTransactionsMessage().getSize())
        .isLessThan(2 * LimitedTransactionsMessages.LIMIT);
  }

  @Test
  public void createLimitedWithTransactionsJustUnderTheLimit() {
    final Set<Transaction> txs = new HashSet<>();
    txs.add(generator.transaction(BytesValue.wrap(new byte[TX_PAYLOAD_LIMIT])));
    txs.add(generator.transaction(BytesValue.wrap(new byte[TX_PAYLOAD_LIMIT])));
    txs.add(generator.transaction(BytesValue.wrap(new byte[TX_PAYLOAD_LIMIT])));
    final LimitedTransactionsMessages firstMessage = LimitedTransactionsMessages.createLimited(txs);
    assertThat(firstMessage.getIncludedTransactions().size()).isEqualTo(2);
    txs.removeAll(firstMessage.getIncludedTransactions());
    assertThat(txs.size()).isEqualTo(1);
    final LimitedTransactionsMessages secondMessage =
        LimitedTransactionsMessages.createLimited(txs);
    assertThat(secondMessage.getIncludedTransactions().size()).isEqualTo(1);
    txs.removeAll(secondMessage.getIncludedTransactions());
    assertThat(txs.size()).isEqualTo(0);
  }

  @Test
  public void createLimitedWithTransactionsJustUnderAndJustOverTheLimit() {
    final Set<Transaction> txs = new LinkedHashSet<>();

    txs.add(generator.transaction(BytesValue.wrap(new byte[TX_PAYLOAD_LIMIT])));
    // ensure the next transaction exceed the limit TX_PAYLOAD_LIMIT + 100
    txs.add(generator.transaction(BytesValue.wrap(new byte[TX_PAYLOAD_LIMIT + 100])));
    txs.add(generator.transaction(BytesValue.wrap(new byte[TX_PAYLOAD_LIMIT])));
    final LimitedTransactionsMessages firstMessage = LimitedTransactionsMessages.createLimited(txs);
    assertThat(firstMessage.getIncludedTransactions().size()).isEqualTo(1);
    txs.removeAll(firstMessage.getIncludedTransactions());
    assertThat(txs.size()).isEqualTo(2);
    final LimitedTransactionsMessages secondMessage =
        LimitedTransactionsMessages.createLimited(txs);
    assertThat(secondMessage.getIncludedTransactions().size()).isEqualTo(1);
    txs.removeAll(secondMessage.getIncludedTransactions());
    assertThat(txs.size()).isEqualTo(1);
    final LimitedTransactionsMessages thirdMessage = LimitedTransactionsMessages.createLimited(txs);
    assertThat(thirdMessage.getIncludedTransactions().size()).isEqualTo(1);
    txs.removeAll(thirdMessage.getIncludedTransactions());
    assertThat(txs.size()).isEqualTo(0);
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
