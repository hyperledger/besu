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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofMinutes;
import static java.time.Instant.now;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.TransactionsMessage;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TransactionsMessageProcessorTest {

  @Mock private TransactionPool transactionPool;
  @Mock private PeerTransactionTracker transactionTracker;
  @Mock private Counter totalSkippedTransactionsMessageCounter;
  @Mock private EthPeer peer1;
  @InjectMocks private TransactionsMessageProcessor messageHandler;

  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final Transaction transaction1 = generator.transaction();
  private final Transaction transaction2 = generator.transaction();
  private final Transaction transaction3 = generator.transaction();

  @Test
  public void shouldMarkAllReceivedTransactionsAsSeen() {
    messageHandler.processTransactionsMessage(
        peer1,
        TransactionsMessage.create(asList(transaction1, transaction2, transaction3)),
        now(),
        ofMinutes(1));

    verify(transactionTracker)
        .markTransactionsAsSeen(peer1, ImmutableSet.of(transaction1, transaction2, transaction3));
  }

  @Test
  public void shouldAddReceivedTransactionsToTransactionPool() {
    messageHandler.processTransactionsMessage(
        peer1,
        TransactionsMessage.create(asList(transaction1, transaction2, transaction3)),
        now(),
        ofMinutes(1));
    verify(transactionPool)
        .addRemoteTransactions(ImmutableSet.of(transaction1, transaction2, transaction3));
  }

  @Test
  public void shouldNotMarkReceivedExpiredTransactionsAsSeen() {
    messageHandler.processTransactionsMessage(
        peer1,
        TransactionsMessage.create(asList(transaction1, transaction2, transaction3)),
        now().minus(ofMinutes(1)),
        ofMillis(1));
    verifyZeroInteractions(transactionTracker);
    verify(totalSkippedTransactionsMessageCounter).inc(1);
  }

  @Test
  public void shouldNotAddReceivedTransactionsToTransactionPoolIfExpired() {
    messageHandler.processTransactionsMessage(
        peer1,
        TransactionsMessage.create(asList(transaction1, transaction2, transaction3)),
        now().minus(ofMinutes(1)),
        ofMillis(1));
    verifyZeroInteractions(transactionPool);
    verify(totalSkippedTransactionsMessageCounter).inc(1);
  }
}
