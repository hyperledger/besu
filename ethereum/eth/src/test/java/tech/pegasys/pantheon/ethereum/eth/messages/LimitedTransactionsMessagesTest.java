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
package tech.pegasys.pantheon.ethereum.eth.messages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static tech.pegasys.pantheon.ethereum.eth.messages.LimitedTransactionsMessages.LIMIT;

import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Test;

public class LimitedTransactionsMessagesTest {

  private static final int TX_PAYLOAD_LIMIT = LIMIT - 180;
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
    assertEquals(0, txs.size());
    assertTrue(
        (firstMessage.getTransactionsMessage().getSize()
                + secondMessage.getTransactionsMessage().getSize())
            < 2 * LIMIT);
  }

  @Test
  public void createLimitedWithTransactionsJustUnderTheLimit() {
    final Set<Transaction> txs = new HashSet<>();
    txs.add(generator.transaction(BytesValue.wrap(new byte[TX_PAYLOAD_LIMIT])));
    txs.add(generator.transaction(BytesValue.wrap(new byte[TX_PAYLOAD_LIMIT])));
    txs.add(generator.transaction(BytesValue.wrap(new byte[TX_PAYLOAD_LIMIT])));
    final LimitedTransactionsMessages firstMessage = LimitedTransactionsMessages.createLimited(txs);
    assertEquals(2, firstMessage.getIncludedTransactions().size());
    txs.removeAll(firstMessage.getIncludedTransactions());
    assertEquals(1, txs.size());
    final LimitedTransactionsMessages secondMessage =
        LimitedTransactionsMessages.createLimited(txs);
    assertEquals(1, secondMessage.getIncludedTransactions().size());
    txs.removeAll(secondMessage.getIncludedTransactions());
    assertEquals(0, txs.size());
  }

  @Test
  public void createLimitedWithTransactionsJustUnderAndJustOverTheLimit() {
    final Set<Transaction> txs = new LinkedHashSet<>();

    txs.add(generator.transaction(BytesValue.wrap(new byte[TX_PAYLOAD_LIMIT])));
    // ensure the next transaction exceed the limit TX_PAYLOAD_LIMIT + 100
    txs.add(generator.transaction(BytesValue.wrap(new byte[TX_PAYLOAD_LIMIT + 100])));
    txs.add(generator.transaction(BytesValue.wrap(new byte[TX_PAYLOAD_LIMIT])));
    final LimitedTransactionsMessages firstMessage = LimitedTransactionsMessages.createLimited(txs);
    assertEquals(1, firstMessage.getIncludedTransactions().size());
    txs.removeAll(firstMessage.getIncludedTransactions());
    assertEquals(2, txs.size());
    final LimitedTransactionsMessages secondMessage =
        LimitedTransactionsMessages.createLimited(txs);
    assertEquals(1, secondMessage.getIncludedTransactions().size());
    txs.removeAll(secondMessage.getIncludedTransactions());
    assertEquals(1, txs.size());
    final LimitedTransactionsMessages thirdMessage = LimitedTransactionsMessages.createLimited(txs);
    assertEquals(1, thirdMessage.getIncludedTransactions().size());
    txs.removeAll(thirdMessage.getIncludedTransactions());
    assertEquals(0, txs.size());
  }

  @Test
  public void getTransactionsMessage() {
    assertEquals(
        sampleTransactionMessages, sampleLimitedTransactionsMessages.getTransactionsMessage());
  }

  @Test
  public void getIncludedTransactions() {
    assertEquals(sampleTxs, sampleLimitedTransactionsMessages.getIncludedTransactions());
  }
}
