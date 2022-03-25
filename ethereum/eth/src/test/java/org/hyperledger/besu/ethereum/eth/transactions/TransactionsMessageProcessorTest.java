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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.TransactionsMessage;
import org.hyperledger.besu.metrics.StubMetricsSystem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TransactionsMessageProcessorTest {

  @Mock private TransactionPool transactionPool;
  @Mock private PeerTransactionTracker transactionTracker;
  @Mock private EthPeer peer1;

  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final Transaction transaction1 = generator.transaction();
  private final Transaction transaction2 = generator.transaction();
  private final Transaction transaction3 = generator.transaction();

  private TransactionsMessageProcessor messageHandler;
  private StubMetricsSystem metricsSystem;

  @Before
  public void setup() {
    metricsSystem = new StubMetricsSystem();

    messageHandler =
        new TransactionsMessageProcessor(transactionTracker, transactionPool, metricsSystem);
  }

  @Test
  public void shouldMarkAllReceivedTransactionsAsSeen() {
    messageHandler.processTransactionsMessage(
        peer1,
        TransactionsMessage.create(asList(transaction1, transaction2, transaction3)),
        now(),
        ofMinutes(1));

    verify(transactionTracker)
        .markTransactionsAsSeen(peer1, asList(transaction1, transaction2, transaction3));
  }

  @Test
  public void shouldAddReceivedTransactionsToTransactionPool() {
    messageHandler.processTransactionsMessage(
        peer1,
        TransactionsMessage.create(asList(transaction1, transaction2, transaction3)),
        now(),
        ofMinutes(1));
    verify(transactionPool).addRemoteTransactions(asList(transaction1, transaction2, transaction3));
  }

  @Test
  public void shouldNotMarkReceivedExpiredTransactionsAsSeen() {
    messageHandler.processTransactionsMessage(
        peer1,
        TransactionsMessage.create(asList(transaction1, transaction2, transaction3)),
        now().minus(ofMinutes(1)),
        ofMillis(1));
    verifyNoInteractions(transactionTracker);
    assertThat(metricsSystem.getCounterValue("transactions_messages_skipped_total")).isEqualTo(1);
  }

  @Test
  public void shouldNotAddReceivedTransactionsToTransactionPoolIfExpired() {
    messageHandler.processTransactionsMessage(
        peer1,
        TransactionsMessage.create(asList(transaction1, transaction2, transaction3)),
        now().minus(ofMinutes(1)),
        ofMillis(1));
    verifyNoInteractions(transactionPool);
    assertThat(metricsSystem.getCounterValue("transactions_messages_skipped_total")).isEqualTo(1);
  }
}
