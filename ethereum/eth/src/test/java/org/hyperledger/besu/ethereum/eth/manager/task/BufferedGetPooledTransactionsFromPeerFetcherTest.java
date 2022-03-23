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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.hyperledger.besu.ethereum.eth.transactions.PeerTransactionTracker;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.metrics.StubMetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BufferedGetPooledTransactionsFromPeerFetcherTest {

  @Mock EthPeer ethPeer;
  @Mock TransactionPool transactionPool;
  @Mock EthContext ethContext;
  @Mock EthScheduler ethScheduler;

  private final BlockDataGenerator generator = new BlockDataGenerator();

  private BufferedGetPooledTransactionsFromPeerFetcher fetcher;
  private StubMetricsSystem metricsSystem;
  private PeerTransactionTracker transactionTracker;

  @Before
  public void setup() {
    metricsSystem = new StubMetricsSystem();
    transactionTracker = new PeerTransactionTracker();
    when(ethContext.getScheduler()).thenReturn(ethScheduler);

    fetcher =
        new BufferedGetPooledTransactionsFromPeerFetcher(
            ethContext, ethPeer, transactionPool, transactionTracker, metricsSystem);
  }

  @Test
  public void requestTransactionShouldStartTaskWhenUnknownTransaction() {

    final Transaction transaction = generator.transaction();
    final Hash hash = transaction.getHash();
    final List<Transaction> taskResult = List.of(transaction);
    final AbstractPeerTask.PeerTaskResult<List<Transaction>> peerTaskResult =
        new AbstractPeerTask.PeerTaskResult<>(ethPeer, taskResult);
    when(ethScheduler.scheduleSyncWorkerTask(any(GetPooledTransactionsFromPeerTask.class)))
        .thenReturn(CompletableFuture.completedFuture(peerTaskResult));

    fetcher.addHashes(List.of(hash));
    fetcher.requestTransactions();

    verify(ethScheduler).scheduleSyncWorkerTask(any(GetPooledTransactionsFromPeerTask.class));
    verifyNoMoreInteractions(ethScheduler);

    verify(transactionPool, times(1)).addRemoteTransactions(taskResult);
    assertThat(transactionTracker.hasSeenTransaction(hash)).isTrue();
  }

  @Test
  public void requestTransactionShouldSplitRequestIntoSeveralTasks() {
    fetcher.addHashes(
        IntStream.range(0, 257)
            .mapToObj(unused -> generator.transaction().getHash())
            .collect(Collectors.toList()));

    final AbstractPeerTask.PeerTaskResult<List<Transaction>> peerTaskResult =
        new AbstractPeerTask.PeerTaskResult<>(ethPeer, List.of());
    when(ethScheduler.scheduleSyncWorkerTask(any(GetPooledTransactionsFromPeerTask.class)))
        .thenReturn(CompletableFuture.completedFuture(peerTaskResult));

    fetcher.requestTransactions();

    verify(ethScheduler, times(2))
        .scheduleSyncWorkerTask(any(GetPooledTransactionsFromPeerTask.class));
    verifyNoMoreInteractions(ethScheduler);
  }

  @Test
  public void requestTransactionShouldNotStartTaskWhenTransactionAlreadySeen() {

    final Transaction transaction = generator.transaction();
    final Hash hash = transaction.getHash();
    transactionTracker.markTransactionHashesAsSeen(ethPeer, List.of(hash));

    fetcher.addHashes(List.of(hash));
    fetcher.requestTransactions();

    verifyNoInteractions(ethScheduler);
    verify(transactionPool, never()).addRemoteTransactions(List.of(transaction));
    assertThat(metricsSystem.getCounterValue("remote_already_seen_total", "hashes")).isEqualTo(1);
  }
}
