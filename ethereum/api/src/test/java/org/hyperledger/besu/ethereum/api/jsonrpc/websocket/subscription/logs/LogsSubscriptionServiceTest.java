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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.logs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.api.LogWithMetadata;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.LogResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LogsSubscriptionServiceTest {

  private final KeyPair keyPair = KeyPair.generate();
  private final BlockHeaderTestFixture blockHeaderTestFixture = new BlockHeaderTestFixture();
  private final TransactionTestFixture txTestFixture = new TransactionTestFixture();

  private LogsSubscriptionService logsSubscriptionService;

  @Mock private SubscriptionManager subscriptionManager;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private Blockchain blockchain;

  @Before
  public void before() {
    logsSubscriptionService = new LogsSubscriptionService(subscriptionManager, blockchainQueries);
  }

  @Test
  public void shouldSendLogMessageWhenBlockAddedEventHasAddedTransactionsMatchingSubscription() {
    final Address address = Address.fromHexString("0x0");
    final LogsSubscription subscription = createSubscription(address);
    final Transaction transaction = createTransaction();
    final Log log = createLog(address);
    final LogResult expectedLogResult = createLogResult(transaction, log, false);

    logsSubscriptionService.onBlockAdded(createBlockAddedEvent(transaction, null), blockchain);

    verify(subscriptionManager)
        .sendMessage(
            ArgumentMatchers.eq(subscription.getSubscriptionId()), refEq(expectedLogResult));
  }

  @Test
  public void shouldSendLogMessageWhenBlockAddedEventHasRemovedTransactionsMatchingSubscription() {
    final Address address = Address.fromHexString("0x0");
    final LogsSubscription subscription = createSubscription(address);
    final Transaction transaction = createTransaction();
    final Log log = createLog(address);
    final LogResult expectedLogResult = createLogResult(transaction, log, true);

    logsSubscriptionService.onBlockAdded(createBlockAddedEvent(null, transaction), blockchain);

    verify(subscriptionManager)
        .sendMessage(
            ArgumentMatchers.eq(subscription.getSubscriptionId()), refEq(expectedLogResult));
  }

  @Test
  public void shouldSendMessageForAllLogsMatchingSubscription() {
    final Address address = Address.fromHexString("0x0");
    final Log log = createLog(address);
    final LogsSubscription subscription = createSubscription(address);
    final List<Transaction> addedTransactions = createTransactionsWithLog(log);
    final List<Transaction> removedTransactions = createTransactionsWithLog(log);

    logsSubscriptionService.onBlockAdded(
        createBlockAddedEvent(addedTransactions, removedTransactions), blockchain);

    final int totalOfLogs = addedTransactions.size() + removedTransactions.size();

    verify(subscriptionManager, times(totalOfLogs))
        .sendMessage(ArgumentMatchers.eq(subscription.getSubscriptionId()), any());
  }

  @Test
  public void shouldSendLogMessageToAllMatchingSubscriptions() {
    final Address address = Address.fromHexString("0x0");
    final List<LogsSubscription> subscriptions = createSubscriptions(address);
    final Transaction transaction = createTransaction();
    final Log log = createLog(address);
    final LogResult expectedLogResult = createLogResult(transaction, log, false);

    logsSubscriptionService.onBlockAdded(createBlockAddedEvent(transaction, null), blockchain);

    verify(subscriptionManager, times(subscriptions.size()))
        .sendMessage(any(), refEq(expectedLogResult));
  }

  @Test
  public void shouldNotSendLogMessageWhenBlockAddedEventHasNoTransactions() {
    final Address address = Address.fromHexString("0x0");
    createSubscription(address);

    logsSubscriptionService.onBlockAdded(
        createBlockAddedEvent(Collections.emptyList(), Collections.emptyList()), blockchain);

    verify(subscriptionManager).subscriptionsOfType(any(), any());
    verify(subscriptionManager, times(0)).sendMessage(any(), any());
  }

  @Test
  public void shouldNotSendLogMessageWhenLogsDoNotMatchAnySubscription() {
    createSubscription(Address.fromHexString("0x0"));
    final Transaction transaction = createTransaction();
    final Log log = createLog(Address.fromHexString("0x1"));
    createLogResult(transaction, log, false);

    logsSubscriptionService.onBlockAdded(createBlockAddedEvent(transaction, null), blockchain);

    verify(subscriptionManager).subscriptionsOfType(any(), any());
    verify(subscriptionManager, times(0)).sendMessage(any(), any());
  }

  private Transaction createTransaction() {
    return txTestFixture.createTransaction(keyPair);
  }

  private Log createLog(final Address address) {
    return new Log(address, BytesValue.EMPTY, Collections.emptyList());
  }

  private LogsSubscription createSubscription(final Address address) {
    final FilterParameter filterParameter =
        new FilterParameter(null, null, Lists.newArrayList(address.toString()), null, null);
    final LogsSubscription logsSubscription = new LogsSubscription(1L, "conn", filterParameter);
    when(subscriptionManager.subscriptionsOfType(any(), any()))
        .thenReturn(Lists.newArrayList(logsSubscription));
    return logsSubscription;
  }

  private List<LogsSubscription> createSubscriptions(final Address address) {
    final List<LogsSubscription> subscriptions = new ArrayList<>();
    for (long i = 0; i < 3; i++) {
      final FilterParameter filterParameter =
          new FilterParameter(null, null, Lists.newArrayList(address.toString()), null, null);
      subscriptions.add(new LogsSubscription(i, "conn", filterParameter));
    }
    when(subscriptionManager.subscriptionsOfType(any(), any()))
        .thenReturn(Lists.newArrayList(subscriptions));
    return subscriptions;
  }

  private LogResult createLogResult(
      final Transaction transaction, final Log log, final boolean removed) {
    final TransactionReceiptWithMetadata txReceiptWithMetadata =
        createTransactionWithLog(transaction, log);
    final LogWithMetadata logWithMetadata = createLogWithMetadata(txReceiptWithMetadata, removed);
    return new LogResult(logWithMetadata);
  }

  private TransactionReceiptWithMetadata createTransactionWithLog(
      final Transaction transaction, final Log log) {
    final BlockHeader blockHeader = blockHeaderTestFixture.buildHeader();
    final TransactionReceipt transactionReceipt =
        new TransactionReceipt(Hash.ZERO, 1L, Lists.newArrayList(log), Optional.empty());
    final TransactionReceiptWithMetadata transactionReceiptWithMetadata =
        TransactionReceiptWithMetadata.create(
            transactionReceipt,
            transaction,
            transaction.hash(),
            0,
            1L,
            blockHeader.getHash(),
            blockHeader.getNumber());

    when(blockchainQueries.transactionReceiptByTransactionHash(eq(transaction.hash())))
        .thenReturn(Optional.of(transactionReceiptWithMetadata));

    return transactionReceiptWithMetadata;
  }

  private BlockAddedEvent createBlockAddedEvent(
      final Transaction addedTransaction, final Transaction removedTransaction) {
    final Block block = mock(Block.class);
    return BlockAddedEvent.createForChainReorg(
        block,
        addedTransaction != null ? Lists.newArrayList(addedTransaction) : Collections.emptyList(),
        removedTransaction != null
            ? Lists.newArrayList(removedTransaction)
            : Collections.emptyList());
  }

  private BlockAddedEvent createBlockAddedEvent(
      final List<Transaction> addedTransactions, final List<Transaction> removedTransactions) {
    final Block block = mock(Block.class);
    return BlockAddedEvent.createForChainReorg(
        block,
        addedTransactions != null ? Lists.newArrayList(addedTransactions) : Collections.emptyList(),
        removedTransactions != null
            ? Lists.newArrayList(removedTransactions)
            : Collections.emptyList());
  }

  private List<Transaction> createTransactionsWithLog(final Log log) {
    final ArrayList<Transaction> transactions =
        Lists.newArrayList(createTransaction(), createTransaction(), createTransaction());
    transactions.forEach(tx -> createTransactionWithLog(tx, log));
    return transactions;
  }

  private LogWithMetadata createLogWithMetadata(
      final TransactionReceiptWithMetadata transactionReceiptWithMetadata, final boolean removed) {
    return new LogWithMetadata(
        0,
        transactionReceiptWithMetadata.getBlockNumber(),
        transactionReceiptWithMetadata.getBlockHash(),
        transactionReceiptWithMetadata.getTransactionHash(),
        transactionReceiptWithMetadata.getTransactionIndex(),
        transactionReceiptWithMetadata.getReceipt().getLogs().get(0).getLogger(),
        transactionReceiptWithMetadata.getReceipt().getLogs().get(0).getData(),
        transactionReceiptWithMetadata.getReceipt().getLogs().get(0).getTopics(),
        removed);
  }
}
