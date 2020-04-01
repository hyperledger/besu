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
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

public class LimitedNewPooledTransactionHashesMessagesTest {

  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final List<Hash> sampleTxs =
      generator.transactions(1).stream().map(Transaction::getHash).collect(Collectors.toList());
  private final NewPooledTransactionHashesMessage sampleTransactionMessages =
      NewPooledTransactionHashesMessage.create(sampleTxs);
  private final LimitedNewPooledTransactionHashesMessages sampleLimitedTransactionsMessages =
      new LimitedNewPooledTransactionHashesMessages(sampleTransactionMessages, sampleTxs);

  @Test
  public void createLimited() {
    final List<Hash> txs =
        generator.transactions(6000).stream()
            .map(Transaction::getHash)
            .collect(Collectors.toList());
    final LimitedNewPooledTransactionHashesMessages firstMessage =
        LimitedNewPooledTransactionHashesMessages.createLimited(txs);
    assertThat(firstMessage.getIncludedTransactions().size()).isEqualTo(4096);

    txs.removeAll(firstMessage.getIncludedTransactions());
    assertThat(txs.size()).isEqualTo(6000 - 4096);
    final LimitedNewPooledTransactionHashesMessages secondMessage =
        LimitedNewPooledTransactionHashesMessages.createLimited(txs);
    assertThat(secondMessage.getIncludedTransactions().size()).isEqualTo(6000 - 4096);
    txs.removeAll(secondMessage.getIncludedTransactions());
    assertThat(txs.size()).isEqualTo(0);
    assertThat(
            firstMessage.getTransactionsMessage().getSize()
                + secondMessage.getTransactionsMessage().getSize())
        .isLessThan(2 * LimitedTransactionsMessages.LIMIT);
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
