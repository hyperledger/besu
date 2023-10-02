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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.LogResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.query.PrivacyQueries;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LogsSubscriptionServiceTest {

  private final BlockDataGenerator gen = new BlockDataGenerator(1);
  private final MutableBlockchain blockchain =
      InMemoryKeyValueStorageProvider.createInMemoryBlockchain(gen.genesisBlock());

  private LogsSubscriptionService logsSubscriptionService;
  private final AtomicLong nextSubscriptionId = new AtomicLong();

  @Mock private SubscriptionManager subscriptionManager;

  @Mock private PrivacyQueries privacyQueries;

  @BeforeEach
  public void before() {
    logsSubscriptionService =
        new LogsSubscriptionService(subscriptionManager, Optional.of(privacyQueries));
    blockchain.observeLogs(logsSubscriptionService);
    blockchain.observeBlockAdded(logsSubscriptionService::checkPrivateLogs);
  }

  @Test
  public void singleMatchingLogEvent() {
    final BlockWithReceipts blockWithReceipts = generateBlock(2, 2, 2);
    final Block block = blockWithReceipts.getBlock();
    final List<TransactionReceipt> receipts = blockWithReceipts.getReceipts();

    final int txIndex = 1;
    final int logIndexInTransaction = 1;
    final int logIndexInBlock = 3; // third log in the block
    final Log targetLog = receipts.get(txIndex).getLogsList().get(logIndexInTransaction);

    final LogsSubscription subscription = createSubscription(targetLog.getLogger());
    registerSubscriptions(subscription);
    blockchain.appendBlock(blockWithReceipts.getBlock(), blockWithReceipts.getReceipts());

    final ArgumentCaptor<LogResult> captor = ArgumentCaptor.forClass(LogResult.class);
    verify(subscriptionManager).sendMessage(eq(subscription.getSubscriptionId()), captor.capture());

    final List<LogResult> logResults = captor.getAllValues();

    assertThat(logResults).hasSize(1);
    final LogResult result = logResults.get(0);
    assertLogResultMatches(
        result, block, receipts, txIndex, logIndexInTransaction, logIndexInBlock, false);
  }

  @Test
  public void singleMatchingLogEmittedThenRemovedInReorg() {
    // Create block that emits an event
    final BlockWithReceipts blockWithReceipts = generateBlock(2, 2, 2);
    final Block block = blockWithReceipts.getBlock();
    final List<TransactionReceipt> receipts = blockWithReceipts.getReceipts();

    final int txIndex = 1;
    final int logListIndex = 1;
    final Log targetLog = receipts.get(txIndex).getLogsList().get(logListIndex);

    final LogsSubscription subscription = createSubscription(targetLog.getLogger());
    registerSubscriptions(subscription);
    blockchain.appendBlock(blockWithReceipts.getBlock(), blockWithReceipts.getReceipts());

    // Cause a reorg that removes the block which emitted an event
    BlockHeader parentHeader = blockchain.getGenesisBlock().getHeader();
    while (!blockchain.getChainHeadHash().equals(parentHeader.getHash())) {
      final BlockWithReceipts newBlock = generateBlock(parentHeader, 2, 0, 0);
      parentHeader = newBlock.getBlock().getHeader();
      blockchain.appendBlock(newBlock.getBlock(), newBlock.getReceipts());
    }

    final ArgumentCaptor<LogResult> captor = ArgumentCaptor.forClass(LogResult.class);
    verify(subscriptionManager, times(2))
        .sendMessage(eq(subscription.getSubscriptionId()), captor.capture());

    final List<LogResult> logResults = captor.getAllValues();

    assertThat(logResults).hasSize(2);
    final LogResult firstLog = logResults.get(0);
    // third log in the block
    assertLogResultMatches(firstLog, block, receipts, txIndex, logListIndex, 3, false);
    final LogResult secondLog = logResults.get(1);
    // third log in the block, but was removed
    assertLogResultMatches(secondLog, block, receipts, txIndex, logListIndex, 3, true);
  }

  @Test
  public void singleMatchingLogEmittedThenMovedInReorg() {
    // Create block that emits an event
    final BlockWithReceipts blockWithReceipts = generateBlock(2, 2, 2);
    final Block block = blockWithReceipts.getBlock();
    final List<TransactionReceipt> receipts = blockWithReceipts.getReceipts();

    final int txIndex = 1;
    final int logListIndex = 1;
    final Log targetLog = receipts.get(txIndex).getLogsList().get(logListIndex);

    final LogsSubscription subscription = createSubscription(targetLog.getLogger());
    registerSubscriptions(subscription);
    blockchain.appendBlock(blockWithReceipts.getBlock(), blockWithReceipts.getReceipts());

    // Cause a reorg that removes the block which emitted an event
    BlockHeader parentHeader = blockchain.getGenesisBlock().getHeader();
    while (!blockchain.getChainHeadHash().equals(parentHeader.getHash())) {
      final BlockWithReceipts newBlock = generateBlock(parentHeader, 2, 0, 0);
      parentHeader = newBlock.getBlock().getHeader();
      blockchain.appendBlock(newBlock.getBlock(), newBlock.getReceipts());
    }

    // Now add another block that re-emits the target log
    final BlockWithReceipts newBlockWithLog =
        generateBlock(1, () -> Collections.singletonList(targetLog));
    blockchain.appendBlock(newBlockWithLog.getBlock(), newBlockWithLog.getReceipts());
    // Sanity check
    assertThat(blockchain.getChainHeadHash()).isEqualTo(newBlockWithLog.getBlock().getHash());

    final ArgumentCaptor<LogResult> captor = ArgumentCaptor.forClass(LogResult.class);
    verify(subscriptionManager, times(3))
        .sendMessage(eq(subscription.getSubscriptionId()), captor.capture());

    final List<LogResult> logResults = captor.getAllValues();

    assertThat(logResults).hasSize(3);
    final LogResult originalLog = logResults.get(0);
    assertLogResultMatches(originalLog, block, receipts, txIndex, logListIndex, 3, false);
    final LogResult removedLog = logResults.get(1);
    assertLogResultMatches(removedLog, block, receipts, txIndex, logListIndex, 3, true);
    final LogResult updatedLog = logResults.get(2);
    assertLogResultMatches(
        updatedLog, newBlockWithLog.getBlock(), newBlockWithLog.getReceipts(), 0, 0, 0, false);
  }

  @Test
  public void multipleMatchingLogsEmitted() {
    final Log targetLog = gen.log();
    final Log otherLog = gen.log();
    final List<Log> logs = Arrays.asList(targetLog, otherLog);

    final LogsSubscription subscription = createSubscription(targetLog.getLogger());
    registerSubscriptions(subscription);

    // Generate blocks with multiple logs matching subscription
    final int txCount = 2;
    final List<BlockWithReceipts> targetBlocks = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      final BlockWithReceipts blockWithReceipts = generateBlock(txCount, () -> logs);
      targetBlocks.add(blockWithReceipts);
      blockchain.appendBlock(blockWithReceipts.getBlock(), blockWithReceipts.getReceipts());

      // Add another block with unrelated logs
      final BlockWithReceipts otherBlock = generateBlock(txCount, 2, 2);
      blockchain.appendBlock(otherBlock.getBlock(), otherBlock.getReceipts());
    }

    final ArgumentCaptor<LogResult> captor = ArgumentCaptor.forClass(LogResult.class);
    verify(subscriptionManager, times(targetBlocks.size() * txCount))
        .sendMessage(eq(subscription.getSubscriptionId()), captor.capture());
    final List<LogResult> logResults = captor.getAllValues();

    // Verify all logs are emitted
    assertThat(logResults).hasSize(targetBlocks.size() * txCount);

    final List<Integer> expectedLogIndex = Arrays.asList(0, 2);

    for (int i = 0; i < targetBlocks.size(); i++) {
      final BlockWithReceipts targetBlock = targetBlocks.get(i);
      for (int j = 0; j < txCount; j++) {
        final int resultIndex = i * txCount + j;
        assertLogResultMatches(
            logResults.get(resultIndex),
            targetBlock.getBlock(),
            targetBlock.getReceipts(),
            j,
            0,
            expectedLogIndex.get(j),
            false);
      }
    }
  }

  @Test
  public void multipleSubscriptionsForSingleMatchingLog() {
    final BlockWithReceipts blockWithReceipts = generateBlock(2, 2, 2);
    final Block block = blockWithReceipts.getBlock();
    final List<TransactionReceipt> receipts = blockWithReceipts.getReceipts();

    final int txIndex = 1;
    final int logIndex = 1;
    final Log targetLog = receipts.get(txIndex).getLogsList().get(logIndex);

    final List<LogsSubscription> subscriptions =
        Stream.generate(() -> createSubscription(targetLog.getLogger()))
            .limit(3)
            .collect(Collectors.toList());
    registerSubscriptions(subscriptions);
    blockchain.appendBlock(blockWithReceipts.getBlock(), blockWithReceipts.getReceipts());

    for (LogsSubscription subscription : subscriptions) {
      final ArgumentCaptor<LogResult> captor = ArgumentCaptor.forClass(LogResult.class);
      verify(subscriptionManager)
          .sendMessage(eq(subscription.getSubscriptionId()), captor.capture());

      final List<LogResult> logResults = captor.getAllValues();

      assertThat(logResults).hasSize(1);
      final LogResult result = logResults.get(0);
      assertLogResultMatches(result, block, receipts, txIndex, logIndex, 3, false);
    }
  }

  @Test
  public void noLogsEmitted() {
    final Address address = Address.fromHexString("0x0");
    final LogsSubscription subscription = createSubscription(address);
    registerSubscriptions(subscription);

    final BlockWithReceipts blockWithReceipts = generateBlock(2, 0, 0);
    blockchain.appendBlock(blockWithReceipts.getBlock(), blockWithReceipts.getReceipts());

    final ArgumentCaptor<LogResult> captor = ArgumentCaptor.forClass(LogResult.class);
    verify(subscriptionManager, times(0))
        .sendMessage(eq(subscription.getSubscriptionId()), captor.capture());
  }

  @Test
  public void noMatchingLogsEmitted() {
    final Address address = Address.fromHexString("0x0");
    final LogsSubscription subscription = createSubscription(address);
    registerSubscriptions(subscription);

    final BlockWithReceipts blockWithReceipts = generateBlock(2, 2, 2);
    blockchain.appendBlock(blockWithReceipts.getBlock(), blockWithReceipts.getReceipts());

    final ArgumentCaptor<LogResult> captor = ArgumentCaptor.forClass(LogResult.class);
    verify(subscriptionManager, times(0))
        .sendMessage(eq(subscription.getSubscriptionId()), captor.capture());
  }

  @Test
  public void whenExistsPrivateLogsSubscriptionPrivacyQueriesIsCalled() {
    final String privacyGroupId = "privacy_group_id";
    final Address address = Address.fromHexString("0x0");
    final String enclavePublicKey = "public_key";
    final PrivateLogsSubscription subscription =
        createPrivateSubscription(privacyGroupId, address, enclavePublicKey);
    registerSubscriptions(subscription);

    final BlockWithReceipts blockWithReceipts = generateBlock(2, 2, 2);
    blockchain.appendBlock(blockWithReceipts.getBlock(), blockWithReceipts.getReceipts());

    verify(privacyQueries)
        .matchingLogs(
            eq(subscription.getPrivacyGroupId()),
            eq(blockWithReceipts.getHash()),
            eq(subscription.getFilterParameter().getLogsQuery()));
  }

  @Test
  public void whenPrivateLogsSubscriptionMatchesLogNotificationIsSent() {
    final String privacyGroupId = "privacy_group_id";
    final Address address = Address.fromHexString("0x0");
    final String enclavePublicKey = "public_key";
    final PrivateLogsSubscription subscription =
        createPrivateSubscription(privacyGroupId, address, enclavePublicKey);
    registerSubscriptions(subscription);

    when(privacyQueries.matchingLogs(any(), any(), any())).thenReturn(List.of(logWithMetadata()));

    final BlockWithReceipts blockWithReceipts = generateBlock(2, 2, 2);
    blockchain.appendBlock(blockWithReceipts.getBlock(), blockWithReceipts.getReceipts());

    verify(subscriptionManager, times(1)).sendMessage(eq(subscription.getSubscriptionId()), any());
  }

  private void assertLogResultMatches(
      final LogResult result,
      final Block block,
      final List<TransactionReceipt> receipts,
      final int txIndex,
      final int logListIndex,
      final int logIndex,
      final boolean isRemoved) {
    final Transaction expectedTransaction = block.getBody().getTransactions().get(txIndex);
    final Log expectedLog = receipts.get(txIndex).getLogsList().get(logListIndex);

    assertThat(result.getLogIndex()).isEqualTo(Quantity.create(logIndex));
    assertThat(result.getTransactionIndex()).isEqualTo(Quantity.create(txIndex));
    assertThat(result.getBlockNumber()).isEqualTo(Quantity.create(block.getHeader().getNumber()));
    assertThat(result.getBlockHash()).isEqualTo(block.getHash().toString());
    assertThat(result.getTransactionHash()).isEqualTo(expectedTransaction.getHash().toString());
    assertThat(result.getAddress()).isEqualTo(expectedLog.getLogger().toString());
    assertThat(result.getData()).isEqualTo(expectedLog.getData().toString());
    assertThat(result.getTopics())
        .isEqualTo(
            expectedLog.getTopics().stream().map(Bytes::toString).collect(Collectors.toList()));
    assertThat(result.isRemoved()).isEqualTo(isRemoved);
  }

  private BlockWithReceipts generateBlock(
      final int txCount, final int logsPerTx, final int topicsPerLog) {
    final BlockHeader parent = blockchain.getChainHeadHeader();
    return generateBlock(parent, txCount, () -> gen.logs(logsPerTx, topicsPerLog));
  }

  private BlockWithReceipts generateBlock(
      final BlockHeader parentHeader,
      final int txCount,
      final int logsPerTx,
      final int topicsPerLog) {
    return generateBlock(parentHeader, txCount, () -> gen.logs(logsPerTx, topicsPerLog));
  }

  private BlockWithReceipts generateBlock(
      final int txCount, final Supplier<List<Log>> logsSupplier) {
    final BlockHeader parent = blockchain.getChainHeadHeader();
    return generateBlock(parent, txCount, logsSupplier);
  }

  private BlockWithReceipts generateBlock(
      final BlockHeader parentHeader, final int txCount, final Supplier<List<Log>> logsSupplier) {
    final List<TransactionReceipt> receipts = new ArrayList<>();
    final List<Log> logs = new ArrayList<>();
    final BlockOptions blockOptions = BlockOptions.create();
    for (int i = 0; i < txCount; i++) {
      final Transaction tx = gen.transaction();
      final TransactionReceipt receipt = gen.receipt(logsSupplier.get());

      receipts.add(receipt);
      receipt.getLogsList().forEach(logs::add);
      blockOptions.addTransaction(tx);
    }

    blockOptions.setParentHash(parentHeader.getHash());
    blockOptions.setBlockNumber(parentHeader.getNumber() + 1L);
    final Block block = gen.block(blockOptions);

    return new BlockWithReceipts(block, receipts);
  }

  private PrivateLogsSubscription createPrivateSubscription(
      final String privacyGroupId, final Address address, final String enclavePublicKey) {
    return new PrivateLogsSubscription(
        nextSubscriptionId.incrementAndGet(),
        "conn",
        new FilterParameter(
            BlockParameter.LATEST,
            BlockParameter.LATEST,
            null,
            null,
            Arrays.asList(address),
            Collections.emptyList(),
            null,
            null,
            null),
        privacyGroupId,
        enclavePublicKey);
  }

  private LogsSubscription createSubscription(final Address address) {
    return createSubscription(Arrays.asList(address), Collections.emptyList());
  }

  private LogsSubscription createSubscription(
      final List<Address> addresses, final List<List<LogTopic>> logTopics) {

    return new LogsSubscription(
        nextSubscriptionId.incrementAndGet(),
        "conn",
        new FilterParameter(
            BlockParameter.LATEST,
            BlockParameter.LATEST,
            null,
            null,
            addresses,
            logTopics,
            null,
            null,
            null));
  }

  private void registerSubscriptions(final LogsSubscription... subscriptions) {
    registerSubscriptions(Arrays.asList(subscriptions));
  }

  private void registerSubscriptions(final List<LogsSubscription> subscriptions) {
    when(subscriptionManager.subscriptionsOfType(any(), any()))
        .thenReturn(Lists.newArrayList(subscriptions));
  }

  private LogWithMetadata logWithMetadata() {
    return new LogWithMetadata(
        0,
        100L,
        Hash.ZERO,
        Hash.ZERO,
        0,
        Address.fromHexString("0x0"),
        Bytes.EMPTY,
        Lists.newArrayList(),
        false);
  }
}
