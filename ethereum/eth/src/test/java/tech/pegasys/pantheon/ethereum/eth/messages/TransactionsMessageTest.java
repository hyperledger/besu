/*
 * Copyright 2018 ConsenSys AG.
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

import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.RawMessage;
import tech.pegasys.pantheon.ethereum.testutil.BlockDataGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.netty.buffer.ByteBuf;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class TransactionsMessageTest {

  @Test
  public void transactionRoundTrip() throws IOException {
    // Setup list of transactions
    final int txCount = 20;
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final List<Transaction> transactions = new ArrayList<>();
    for (int i = 0; i < txCount; ++i) {
      transactions.add(gen.transaction());
    }

    // Create TransactionsMessage
    final MessageData initialMessage = TransactionsMessage.create(transactions);
    // Read message into a generic RawMessage
    final ByteBuf rawBuffer = NetworkMemoryPool.allocate(initialMessage.getSize());
    initialMessage.writeTo(rawBuffer);
    final MessageData raw = new RawMessage(EthPV62.TRANSACTIONS, rawBuffer);
    // Transform back to a TransactionsMessage from RawMessage
    final TransactionsMessage message = TransactionsMessage.readFrom(raw);

    // Check that transactions match original inputs after transformations
    try {
      final Iterator<Transaction> readTransactions = message.transactions(Transaction::readFrom);
      for (int i = 0; i < txCount; ++i) {
        Assertions.assertThat(readTransactions.next()).isEqualTo(transactions.get(i));
      }
      Assertions.assertThat(readTransactions.hasNext()).isFalse();
    } finally {
      message.release();
      initialMessage.release();
      raw.release();
    }
  }
}
