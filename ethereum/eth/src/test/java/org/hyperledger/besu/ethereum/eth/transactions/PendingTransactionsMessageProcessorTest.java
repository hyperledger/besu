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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.messages.NewPooledTransactionHashesMessage;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionsMessageProcessor.FetcherCreatorTask;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PendingTransactionsMessageProcessorTest {

  @Mock private TransactionPool transactionPool;
  @Mock private TransactionPoolConfiguration transactionPoolConfiguration;
  @Mock private PeerPendingTransactionTracker transactionTracker;
  @Mock private Counter totalSkippedTransactionsMessageCounter;
  @Mock private EthPeer peer1;
  @Mock private MetricsSystem metricsSystem;
  @Mock private SyncState syncState;
  @Mock private EthContext ethContext;
  @Mock private EthScheduler ethScheduler;

  private PendingTransactionsMessageProcessor messageHandler;

  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final Hash hash1 = generator.transaction().getHash();
  private final Hash hash2 = generator.transaction().getHash();
  private final Hash hash3 = generator.transaction().getHash();

  @Before
  public void setup() {
    when(transactionPoolConfiguration.getEth65TrxAnnouncedBufferingPeriod())
        .thenReturn(Duration.ofMillis(500));
    messageHandler =
        new PendingTransactionsMessageProcessor(
            transactionTracker,
            transactionPool,
            transactionPoolConfiguration,
            totalSkippedTransactionsMessageCounter,
            ethContext,
            metricsSystem,
            syncState);
    when(ethContext.getScheduler()).thenReturn(ethScheduler);
  }

  @Test
  public void shouldMarkAllReceivedTransactionsAsSeen() {
    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(asList(hash1, hash2, hash3)),
        now(),
        ofMinutes(1));

    verify(transactionTracker)
        .markTransactionsHashesAsSeen(peer1, Arrays.asList(hash1, hash2, hash3));
    verifyNoMoreInteractions(transactionTracker);
  }

  @Test
  public void shouldAddInitiatedRequestingTransactions() {
    when(syncState.isInSync(anyLong())).thenReturn(true);

    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(asList(hash1, hash2, hash3)),
        now(),
        ofMinutes(1));

    verify(transactionPool).addTransactionHash(hash1);
    verify(transactionPool).addTransactionHash(hash2);
    verify(transactionPool).addTransactionHash(hash3);
    verify(transactionPool).getTransactionByHash(hash1);
    verify(transactionPool).getTransactionByHash(hash2);
    verify(transactionPool).getTransactionByHash(hash3);
    verifyNoMoreInteractions(transactionPool);
  }

  @Test
  public void shouldNotAddAlreadyPresentTransactions() {
    when(syncState.isInSync(anyLong())).thenReturn(true);

    when(transactionPool.getTransactionByHash(hash1))
        .thenReturn(Optional.of(Transaction.builder().build()));
    when(transactionPool.getTransactionByHash(hash2))
        .thenReturn(Optional.of(Transaction.builder().build()));

    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(asList(hash1, hash2, hash3)),
        now(),
        ofMinutes(1));

    verify(transactionPool).addTransactionHash(hash3);
    verify(transactionPool).getTransactionByHash(hash1);
    verify(transactionPool).getTransactionByHash(hash2);
    verify(transactionPool).getTransactionByHash(hash3);
    verifyNoMoreInteractions(transactionPool);
  }

  @Test
  public void shouldNotAddInitiatedRequestingTransactionsWhemOutOfSync() {
    when(syncState.isInSync(anyLong())).thenReturn(false);

    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(asList(hash1, hash2, hash3)),
        now(),
        ofMinutes(1));
    verifyNoInteractions(transactionPool);
  }

  @Test
  public void shouldNotMarkReceivedExpiredTransactionsAsSeen() {
    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(asList(hash1, hash2, hash3)),
        now().minus(ofMinutes(1)),
        ofMillis(1));
    verifyNoInteractions(transactionTracker);
    verify(totalSkippedTransactionsMessageCounter).inc(1);
    verifyNoMoreInteractions(totalSkippedTransactionsMessageCounter);
  }

  @Test
  public void shouldNotAddReceivedTransactionsToTransactionPoolIfExpired() {
    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(asList(hash1, hash2, hash3)),
        now().minus(ofMinutes(1)),
        ofMillis(1));
    verifyNoInteractions(transactionPool);
    verify(totalSkippedTransactionsMessageCounter).inc(1);
    verifyNoMoreInteractions(totalSkippedTransactionsMessageCounter);
  }

  @Test
  public void shouldScheduleGetPooledTransactionsTaskWhenNewTransactionAdded() {
    when(syncState.isInSync(anyLong())).thenReturn(true);

    final EthScheduler ethScheduler = mock(EthScheduler.class);
    when(ethContext.getScheduler()).thenReturn(ethScheduler);
    when(transactionPool.addTransactionHash(hash1)).thenReturn(true);

    messageHandler.processNewPooledTransactionHashesMessage(
        peer1, NewPooledTransactionHashesMessage.create(asList(hash1, hash2)), now(), ofMinutes(1));

    verify(ethScheduler, times(1))
        .scheduleFutureTask(any(FetcherCreatorTask.class), any(Duration.class));
  }

  @Test
  public void shouldNotScheduleGetPooledTransactionsTaskTwice() {
    when(syncState.isInSync(anyLong())).thenReturn(true);

    when(transactionPool.addTransactionHash(hash1)).thenReturn(true);
    when(transactionPool.addTransactionHash(hash2)).thenReturn(true);

    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(Collections.singletonList(hash1)),
        now(),
        ofMinutes(1));

    messageHandler.processNewPooledTransactionHashesMessage(
        peer1,
        NewPooledTransactionHashesMessage.create(Collections.singletonList(hash2)),
        now(),
        ofMinutes(1));

    verify(ethScheduler, times(1))
        .scheduleFutureTask(any(FetcherCreatorTask.class), any(Duration.class));
  }
}
