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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Test;

public class LimitedTransactionsMessagesTest {

  private static final int LIMIT = 1048576;

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
    assertEquals(5219, firstMessage.getIncludedTransactions().size());
    txs.removeAll(firstMessage.getIncludedTransactions());
    assertEquals(781, txs.size());
    final LimitedTransactionsMessages secondMessage =
        LimitedTransactionsMessages.createLimited(txs);
    assertEquals(781, secondMessage.getIncludedTransactions().size());
    txs.removeAll(secondMessage.getIncludedTransactions());
    assertEquals(0, txs.size());
    assertTrue(
        (firstMessage.getTransactionsMessage().getSize()
                + secondMessage.getTransactionsMessage().getSize())
            < 2 * LIMIT);
  }

  @Test
  public void createLimitedWithFirstTransactionExceedingLimit() {
    final Set<Transaction> txs = new HashSet<>();
    txs.add(generator.transaction(BytesValue.wrap(new byte[LIMIT - 180])));
    txs.add(generator.transaction(BytesValue.wrap(new byte[LIMIT - 180])));
    txs.add(generator.transaction(BytesValue.wrap(new byte[LIMIT - 180])));
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
  public void createLimitedWithFirstTransactionExceedingLimit_2() {
    final Set<Transaction> txs = new LinkedHashSet<>();

    txs.add(generator.transaction(BytesValue.wrap(new byte[LIMIT - 180])));
    txs.add(generator.transaction(BytesValue.wrap(new byte[LIMIT + 100 - 180])));
    txs.add(generator.transaction(BytesValue.wrap(new byte[LIMIT - 180])));
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
