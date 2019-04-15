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
package tech.pegasys.pantheon.ethereum.eth.transactions;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.messages.TransactionsMessage;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;

public class TransactionsMessageProcessorTest {

  private final TransactionPool transactionPool = mock(TransactionPool.class);
  private final PeerTransactionTracker transactionTracker = mock(PeerTransactionTracker.class);
  private final EthPeer peer1 = mock(EthPeer.class);

  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final Transaction transaction1 = generator.transaction();
  private final Transaction transaction2 = generator.transaction();
  private final Transaction transaction3 = generator.transaction();

  private final TransactionsMessageProcessor messageHandler =
      new TransactionsMessageProcessor(transactionTracker, transactionPool);

  @Test
  public void shouldMarkAllReceivedTransactionsAsSeen() {
    messageHandler.processTransactionsMessage(
        peer1, TransactionsMessage.create(asList(transaction1, transaction2, transaction3)));

    verify(transactionTracker)
        .markTransactionsAsSeen(peer1, ImmutableSet.of(transaction1, transaction2, transaction3));
  }

  @Test
  public void shouldAddReceivedTransactionsToTransactionPool() {
    messageHandler.processTransactionsMessage(
        peer1, TransactionsMessage.create(asList(transaction1, transaction2, transaction3)));

    verify(transactionPool)
        .addRemoteTransactions(ImmutableSet.of(transaction1, transaction2, transaction3));
  }
}
