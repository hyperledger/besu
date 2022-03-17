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
package org.hyperledger.besu.ethereum.eth.manager.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
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
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionsMessageProcessor;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BufferedGetPooledTransactionsFromPeerFetcherTest {

  @Mock EthPeer ethPeer;
  @Mock PendingTransactionsMessageProcessor processor;
  @Mock TransactionPool transactionPool;
  @Mock EthContext ethContext;
  @Mock EthScheduler ethScheduler;
  @Mock MetricsSystem metricsSystem;

  @InjectMocks BufferedGetPooledTransactionsFromPeerFetcher fetcher;

  private final BlockDataGenerator generator = new BlockDataGenerator();

  @Before
  public void setup() {
    when(ethContext.getScheduler()).thenReturn(ethScheduler);
  }

  @Test
  public void requestTransactionShouldStartTaskWhenUnknownTransaction() {

    final Hash hash = generator.transaction().getHash();
    final List<Transaction> taskResult = Collections.singletonList(Transaction.builder().build());
    final AbstractPeerTask.PeerTaskResult<List<Transaction>> peerTaskResult =
        new AbstractPeerTask.PeerTaskResult<>(ethPeer, taskResult);
    when(ethScheduler.scheduleSyncWorkerTask(any(GetPooledTransactionsFromPeerTask.class)))
        .thenReturn(CompletableFuture.completedFuture(peerTaskResult));

    fetcher.addHash(hash);
    fetcher.requestTransactions();

    verify(ethScheduler).scheduleSyncWorkerTask(any(GetPooledTransactionsFromPeerTask.class));
    verifyNoMoreInteractions(ethScheduler);

    verify(transactionPool, times(1)).addRemoteTransactions(taskResult);
  }

  @Test
  public void requestTransactionShouldSplitRequestIntoSeveralTasks() {
    for (int i = 0; i < 257; i++) {
      fetcher.addHash(generator.transaction().getHash());
    }
    final AbstractPeerTask.PeerTaskResult<List<Transaction>> peerTaskResult =
        new AbstractPeerTask.PeerTaskResult<>(ethPeer, new ArrayList<>());
    when(ethScheduler.scheduleSyncWorkerTask(any(GetPooledTransactionsFromPeerTask.class)))
        .thenReturn(CompletableFuture.completedFuture(peerTaskResult));

    fetcher.requestTransactions();

    verify(ethScheduler, times(2))
        .scheduleSyncWorkerTask(any(GetPooledTransactionsFromPeerTask.class));
    verifyNoMoreInteractions(ethScheduler);
  }

  @Test
  public void requestTransactionShouldNotStartTaskWhenTransactionAlreadyInPool() {

    final Hash hash = generator.transaction().getHash();
    when(transactionPool.getTransactionByHash(hash))
        .thenReturn(Optional.of(Transaction.builder().build()));

    fetcher.addHash(hash);
    fetcher.requestTransactions();

    verifyNoInteractions(ethScheduler);
    verify(transactionPool, never()).addRemoteTransactions(anyList());
  }
}
