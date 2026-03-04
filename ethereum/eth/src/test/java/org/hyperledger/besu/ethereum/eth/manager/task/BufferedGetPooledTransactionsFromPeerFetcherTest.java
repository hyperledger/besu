/*
 * Copyright contributors to Besu.
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
import static org.hyperledger.besu.ethereum.eth.manager.task.BufferedGetPooledTransactionsFromPeerFetcher.MAX_HASHES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetPooledTransactionsFromPeerTask;
import org.hyperledger.besu.ethereum.eth.transactions.PeerTransactionTracker;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BufferedGetPooledTransactionsFromPeerFetcherTest {

  private @Mock EthPeer ethPeer;
  private @Mock TransactionPool transactionPool;
  private @Mock EthContext ethContext;
  private @Mock EthPeers ethPeers;
  private @Mock PeerTaskExecutor peerTaskExecutor;

  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final EthScheduler ethScheduler = new DeterministicEthScheduler();

  private BufferedGetPooledTransactionsFromPeerFetcher fetcher;
  private PeerTransactionTracker transactionTracker;

  @BeforeEach
  public void setup() {
    when(ethContext.getEthPeers()).thenReturn(ethPeers);
    when(ethContext.getScheduler()).thenReturn(ethScheduler);
    transactionTracker =
        new PeerTransactionTracker(TransactionPoolConfiguration.DEFAULT, ethPeers, ethScheduler);
    when(ethContext.getPeerTaskExecutor()).thenReturn(peerTaskExecutor);
    fetcher =
        new BufferedGetPooledTransactionsFromPeerFetcher(
            ethContext, ethPeer, transactionPool, transactionTracker);
  }

  @Test
  public void requestTransactionShouldStartTaskWhenUnknownTransaction() {
    final Transaction transaction = generator.transaction();
    final List<Transaction> taskResult = List.of(transaction);
    final PeerTaskExecutorResult<List<Transaction>> peerTaskResult =
        new PeerTaskExecutorResult<>(
            Optional.of(taskResult), PeerTaskExecutorResponseCode.SUCCESS, List.of(ethPeer));

    when(peerTaskExecutor.executeAgainstPeer(
            any(GetPooledTransactionsFromPeerTask.class), eq(ethPeer)))
        .thenReturn(peerTaskResult);

    // Add hashes to the transaction tracker as if they were announced by the peer
    transactionTracker.receivedTransactionAnnouncements(ethPeer, List.of(transaction.getHash()));
    fetcher.requestTransactions();

    verify(peerTaskExecutor)
        .executeAgainstPeer(any(GetPooledTransactionsFromPeerTask.class), eq(ethPeer));

    verifyNoMoreInteractions(peerTaskExecutor);
    verify(transactionPool, times(1)).addRemoteTransactions(taskResult);

    assertThat(transactionTracker.hasSeenTransactionAnnouncement(ethPeer, transaction.getHash()))
        .isTrue();
  }

  @Test
  public void requestTransactionShouldSplitRequestIntoSeveralTasks() {
    final List<Transaction> transactions =
        IntStream.range(0, MAX_HASHES + 1).mapToObj(unused -> generator.transaction()).toList();

    // Add hashes to the transaction tracker as if they were announced by the peer
    transactionTracker.receivedTransactionAnnouncements(
        ethPeer, transactions.stream().map(Transaction::getHash).toList());

    final var taskResult1 = transactions.subList(0, MAX_HASHES);

    final var taskResult2 = transactions.subList(MAX_HASHES, transactions.size());

    when(peerTaskExecutor.executeAgainstPeer(
            any(GetPooledTransactionsFromPeerTask.class), eq(ethPeer)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(taskResult1), PeerTaskExecutorResponseCode.SUCCESS, List.of(ethPeer)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(taskResult2), PeerTaskExecutorResponseCode.SUCCESS, List.of(ethPeer)));

    fetcher.requestTransactions();

    verify(peerTaskExecutor, times(2))
        .executeAgainstPeer(any(GetPooledTransactionsFromPeerTask.class), eq(ethPeer));
    verifyNoMoreInteractions(peerTaskExecutor);
    verify(transactionPool).addRemoteTransactions(taskResult1);
    verify(transactionPool).addRemoteTransactions(taskResult2);
  }

  @Test
  public void requestOnlyNotAlreadySeenTransactions() {
    final List<Transaction> transactions =
        IntStream.range(0, MAX_HASHES + 1).mapToObj(unused -> generator.transaction()).toList();

    // tx index to mark as already seen
    final int alreadySeenTxIndex = MAX_HASHES / 2;

    // Add hashes to the transaction tracker as if they were announced by the peer,
    // there should be 2 requests, but we will mark one as already seen,
    // so we only expect one sent.
    transactionTracker.receivedTransactionAnnouncements(
        ethPeer, transactions.stream().map(Transaction::getHash).toList());

    transactionTracker.markTransactionAsSeen(ethPeer, transactions.get(alreadySeenTxIndex));

    final var taskResult = new ArrayList<>(transactions);
    taskResult.remove(alreadySeenTxIndex);

    when(peerTaskExecutor.executeAgainstPeer(
            any(GetPooledTransactionsFromPeerTask.class), eq(ethPeer)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(taskResult), PeerTaskExecutorResponseCode.SUCCESS, List.of(ethPeer)));

    fetcher.requestTransactions();

    verify(peerTaskExecutor)
        .executeAgainstPeer(any(GetPooledTransactionsFromPeerTask.class), eq(ethPeer));
    verifyNoMoreInteractions(peerTaskExecutor);
    verify(transactionPool).addRemoteTransactions(taskResult);
  }

  @Test
  public void requestTransactionShouldNotStartTaskWhenTransactionAlreadySeen1() {

    final Transaction transaction = generator.transaction();
    final Hash hash = transaction.getHash();

    // Firstly mark the transaction as already seen by this peer
    transactionTracker.markTransactionsAsSeen(ethPeer, List.of(transaction.getHash()));

    // Try to add the announcement for the same transaction
    transactionTracker.receivedTransactionAnnouncements(ethPeer, List.of(hash));
    fetcher.requestTransactions();

    verifyNoInteractions(peerTaskExecutor);
    verify(transactionPool, never()).addRemoteTransactions(List.of(transaction));
  }

  @Test
  public void requestTransactionShouldNotStartTaskWhenTransactionAlreadySeen2() {

    final Transaction transaction = generator.transaction();
    final Hash hash = transaction.getHash();

    // Firstly add the announcement for the transaction
    transactionTracker.receivedTransactionAnnouncements(ethPeer, List.of(hash));

    // Only after mark the transaction as already seen by this peer
    transactionTracker.markTransactionsAsSeen(ethPeer, List.of(transaction.getHash()));

    fetcher.requestTransactions();

    verifyNoInteractions(peerTaskExecutor);
    verify(transactionPool, never()).addRemoteTransactions(List.of(transaction));
  }
}
