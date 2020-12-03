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
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class TransactionsMessageTest {

  @Test
  public void transactionRoundTrip() {
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
    final MessageData raw = new RawMessage(EthPV62.TRANSACTIONS, initialMessage.getData());
    // Transform back to a TransactionsMessage from RawMessage
    final TransactionsMessage message = TransactionsMessage.readFrom(raw);

    // Check that transactions match original inputs after transformations
    assertThat(message.transactions()).isEqualTo(transactions);
  }
}
