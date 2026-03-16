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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetPooledTransactionsFromPeerTask;
import org.hyperledger.besu.ethereum.eth.transactions.PeerTransactionTracker;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAnnouncement;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.metrics.StubMetricsSystem;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.netty.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  private StubMetricsSystem metricsSystem;
  private PeerTransactionTracker transactionTracker;

  @BeforeEach
  public void setup() {
    metricsSystem = new StubMetricsSystem();
    when(ethContext.getEthPeers()).thenReturn(ethPeers);
    transactionTracker =
        new PeerTransactionTracker(TransactionPoolConfiguration.DEFAULT, ethPeers, ethScheduler);
    when(ethContext.getScheduler()).thenReturn(ethScheduler);
    when(ethContext.getPeerTaskExecutor()).thenReturn(peerTaskExecutor);
    ScheduledFuture<?> mock = mock(ScheduledFuture.class);
    fetcher =
        new BufferedGetPooledTransactionsFromPeerFetcher(
            ethContext,
            mock,
            ethPeer,
            transactionPool,
            transactionTracker,
            EthProtocolConfiguration.DEFAULT_MAX_TRANSACTIONS_MESSAGE_SIZE,
            new TransactionPoolMetrics(metricsSystem),
            "new_pooled_transaction_hashes");
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

    fetcher.addAnnouncements(List.of(new TransactionAnnouncement(transaction)));
    fetcher.requestTransactions();

    verify(peerTaskExecutor)
        .executeAgainstPeer(any(GetPooledTransactionsFromPeerTask.class), eq(ethPeer));
    verifyNoMoreInteractions(peerTaskExecutor);
    verify(transactionPool, times(1)).addRemoteTransactions(taskResult);

    assertThat(transactionTracker.hasSeenTransaction(transaction.getHash())).isTrue();
  }

  @Test
  public void requestTransactionShouldSplitRequestIntoSeveralTasks() {
    final Map<TransactionAnnouncement, Transaction> transactionsByAnnouncement =
        IntStream.range(0, 257)
            .mapToObj(unused -> generator.transaction())
            .collect(Collectors.toMap(TransactionAnnouncement::new, Function.identity()));
    final Map<Hash, Transaction> transactionsByHash =
        transactionsByAnnouncement.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().hash(), Map.Entry::getValue));
    fetcher.addAnnouncements(transactionsByAnnouncement.keySet());

    when(peerTaskExecutor.executeAgainstPeer(
            any(GetPooledTransactionsFromPeerTask.class), eq(ethPeer)))
        .thenAnswer(
            (invocationOnMock) -> {
              GetPooledTransactionsFromPeerTask task =
                  invocationOnMock.getArgument(0, GetPooledTransactionsFromPeerTask.class);

              List<Transaction> resultTransactions =
                  task.getHashes().stream().map(transactionsByHash::get).toList();
              return new PeerTaskExecutorResult<>(
                  Optional.of(resultTransactions),
                  PeerTaskExecutorResponseCode.SUCCESS,
                  List.of(ethPeer));
            });

    fetcher.requestTransactions();

    verify(peerTaskExecutor, times(2))
        .executeAgainstPeer(any(GetPooledTransactionsFromPeerTask.class), eq(ethPeer));
    verifyNoMoreInteractions(peerTaskExecutor);
  }

  @Test
  public void requestTransactionShouldSplitRequestWhenCumulativeSizeExceedsLimit() {
    // DEFAULT_MAX_TRANSACTIONS_MESSAGE_SIZE = 1 MB (1,048,576 bytes).
    // The inner check is: if (cumulative + txSize > limit) break.
    // With 2 announcements of 600 KB each:
    //   ann1: 0 + 600KB ≤ 1MB  → added (cumulative = 600KB)
    //   ann2: 600KB + 600KB > 1MB → break, ann2 is not removed
    // First batch = [ann1].  ann2 stays queued and is returned as the second batch.
    final long largeSize = 600L * 1024;
    final List<TransactionAnnouncement> announcements =
        IntStream.range(0, 2)
            .mapToObj(unused -> generator.transaction())
            .map(tx -> new TransactionAnnouncement(tx.getHash(), tx.getType(), largeSize))
            .toList();

    when(peerTaskExecutor.executeAgainstPeer(
            any(GetPooledTransactionsFromPeerTask.class), eq(ethPeer)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of()), PeerTaskExecutorResponseCode.SUCCESS, List.of(ethPeer)));

    fetcher.addAnnouncements(announcements);
    fetcher.requestTransactions();

    final ArgumentCaptor<GetPooledTransactionsFromPeerTask> taskCaptor =
        ArgumentCaptor.forClass(GetPooledTransactionsFromPeerTask.class);

    verify(peerTaskExecutor, times(2)).executeAgainstPeer(taskCaptor.capture(), eq(ethPeer));
    verifyNoMoreInteractions(peerTaskExecutor);

    assertThat(taskCaptor.getAllValues().stream().map(GetPooledTransactionsFromPeerTask::getHashes))
        .containsExactly(
            List.of(announcements.get(0).hash()), List.of(announcements.get(1).hash()));
  }

  @Test
  public void requestTransactionShouldDiscardOversizedAnnouncementAndNotLoopForever() {
    // An announcement whose individual size exceeds the limit would be stuck at the head of the
    // queue forever (peek → size check fails → break → peek again → ...).
    // The fix: if txSize > limit, discard it (remove) before breaking.
    final long oversizedSize = EthProtocolConfiguration.DEFAULT_MAX_TRANSACTIONS_MESSAGE_SIZE + 1L;
    final Transaction oversizedTx = generator.transaction();
    final Transaction normalTx = generator.transaction();

    final TransactionAnnouncement oversized =
        new TransactionAnnouncement(oversizedTx.getHash(), oversizedTx.getType(), oversizedSize);
    final TransactionAnnouncement normal = new TransactionAnnouncement(normalTx);

    fetcher.addAnnouncements(List.of(oversized, normal));

    when(peerTaskExecutor.executeAgainstPeer(
            any(GetPooledTransactionsFromPeerTask.class), eq(ethPeer)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of()), PeerTaskExecutorResponseCode.SUCCESS, List.of(ethPeer)));

    // First call: oversized is discarded then break → no request is made, method returns normally
    fetcher.requestTransactions();
    verifyNoInteractions(peerTaskExecutor);

    // Second call: oversized is gone; normal tx is now at the head and gets fetched
    fetcher.requestTransactions();

    final ArgumentCaptor<GetPooledTransactionsFromPeerTask> taskCaptor =
        ArgumentCaptor.forClass(GetPooledTransactionsFromPeerTask.class);
    verify(peerTaskExecutor, times(1)).executeAgainstPeer(taskCaptor.capture(), eq(ethPeer));
    assertThat(taskCaptor.getValue().getHashes()).containsExactly(normal.hash());
    verifyNoMoreInteractions(peerTaskExecutor);
  }

  @Test
  public void requestTransactionShouldNotStartTaskWhenTransactionAlreadySeen() {

    final Transaction transaction = generator.transaction();
    final TransactionAnnouncement announcement = new TransactionAnnouncement(transaction);
    transactionTracker.markTransactionHashesAsSeen(ethPeer, List.of(announcement.hash()));

    fetcher.addAnnouncements(List.of(announcement));
    fetcher.requestTransactions();

    verifyNoInteractions(peerTaskExecutor);
    verify(transactionPool, never()).addRemoteTransactions(List.of(transaction));
    assertThat(
            metricsSystem.getCounterValue(
                "remote_transactions_already_seen_total", "new_pooled_transaction_hashes"))
        .isEqualTo(1);
  }
}
