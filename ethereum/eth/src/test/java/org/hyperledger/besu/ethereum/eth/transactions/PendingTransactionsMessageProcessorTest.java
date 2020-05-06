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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.NewPooledTransactionHashesMessage;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PendingTransactionsMessageProcessorTest {

  @Mock private TransactionPool transactionPool;
  @Mock private PeerPendingTransactionTracker transactionTracker;
  @Mock private Counter totalSkippedTransactionsMessageCounter;
  @Mock private EthPeer peer1;
  @Mock private SyncState syncState;
  @InjectMocks private PendingTransactionsMessageProcessor messageHandler;

  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final Hash hash1 = generator.transaction().getHash();
  private final Hash hash2 = generator.transaction().getHash();
  private final Hash hash3 = generator.transaction().getHash();

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
}
