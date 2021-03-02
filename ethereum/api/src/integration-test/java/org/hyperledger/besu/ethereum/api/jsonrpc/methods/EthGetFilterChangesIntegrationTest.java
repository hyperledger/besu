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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManagerBuilder;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthGetFilterChanges;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.PeerPendingTransactionTracker;
import org.hyperledger.besu.ethereum.eth.transactions.PeerTransactionTracker;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool.TransactionBatchAddedListener;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthGetFilterChangesIntegrationTest {

  @Mock private TransactionBatchAddedListener batchAddedListener;
  @Mock private TransactionBatchAddedListener pendingBatchAddedListener;
  private MutableBlockchain blockchain;
  private final String ETH_METHOD = "eth_getFilterChanges";
  private final String JSON_RPC_VERSION = "2.0";
  private TransactionPool transactionPool;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  private PendingTransactions transactions;

  private static final int MAX_TRANSACTIONS = 5;
  private static final int MAX_HASHES = 5;
  private static final KeyPair keyPair = SignatureAlgorithmFactory.getInstance().generateKeyPair();
  private final Transaction transaction = createTransaction(1);
  private FilterManager filterManager;
  private EthGetFilterChanges method;
  private final SyncState syncState = mock(SyncState.class);

  @Before
  public void setUp() {
    final ExecutionContextTestFixture executionContext = ExecutionContextTestFixture.create();
    blockchain = executionContext.getBlockchain();
    transactions =
        new PendingTransactions(
            TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
            MAX_TRANSACTIONS,
            MAX_HASHES,
            TestClock.fixed(),
            metricsSystem,
            blockchain::getChainHeadHeader,
            TransactionPoolConfiguration.DEFAULT_PRICE_BUMP);
    final ProtocolContext protocolContext = executionContext.getProtocolContext();

    PeerTransactionTracker peerTransactionTracker = mock(PeerTransactionTracker.class);
    PeerPendingTransactionTracker peerPendingTransactionTracker =
        mock(PeerPendingTransactionTracker.class);
    EthContext ethContext = mock(EthContext.class);
    EthPeers ethPeers = mock(EthPeers.class);
    when(ethContext.getEthPeers()).thenReturn(ethPeers);

    transactionPool =
        new TransactionPool(
            transactions,
            executionContext.getProtocolSchedule(),
            protocolContext,
            batchAddedListener,
            Optional.of(pendingBatchAddedListener),
            syncState,
            ethContext,
            peerTransactionTracker,
            Optional.of(peerPendingTransactionTracker),
            Wei.ZERO,
            metricsSystem,
            Optional.empty(),
            TransactionPoolConfiguration.DEFAULT);
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(blockchain, protocolContext.getWorldStateArchive());
    filterManager =
        new FilterManagerBuilder()
            .blockchainQueries(blockchainQueries)
            .transactionPool(transactionPool)
            .build();

    method = new EthGetFilterChanges(filterManager);
  }

  @Test
  public void shouldReturnErrorResponseIfFilterNotFound() {
    final JsonRpcRequestContext request = requestWithParams("0");

    final JsonRpcResponse expected = new JsonRpcErrorResponse(null, JsonRpcError.FILTER_NOT_FOUND);
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByField(expected);
  }

  @Test
  public void shouldReturnEmptyArrayIfNoNewBlocks() {
    final String filterId = filterManager.installBlockFilter();

    assertThatFilterExists(filterId);

    final JsonRpcRequestContext request = requestWithParams(String.valueOf(filterId));
    final JsonRpcSuccessResponse expected = new JsonRpcSuccessResponse(null, Lists.emptyList());
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByField(expected);

    filterManager.uninstallFilter(filterId);

    assertThatFilterDoesNotExist(filterId);
  }

  @Test
  public void shouldReturnEmptyArrayIfNoAddedPendingTransactions() {
    final String filterId = filterManager.installPendingTransactionFilter();

    assertThatFilterExists(filterId);

    final JsonRpcRequestContext request = requestWithParams(String.valueOf(filterId));

    // We haven't added any transactions, so the list of pending transactions should be empty.
    final JsonRpcSuccessResponse expected = new JsonRpcSuccessResponse(null, Lists.emptyList());
    final JsonRpcResponse actual = method.response(request);
    assertThat(actual).isEqualToComparingFieldByField(expected);

    filterManager.uninstallFilter(filterId);

    assertThatFilterDoesNotExist(filterId);
  }

  @Test
  public void shouldReturnHashesIfNewBlocks() {
    final String filterId = filterManager.installBlockFilter();

    assertThatFilterExists(filterId);

    final JsonRpcRequestContext request = requestWithParams(String.valueOf(filterId));

    // We haven't added any blocks, so the list of new blocks should be empty.
    JsonRpcSuccessResponse expected = new JsonRpcSuccessResponse(null, Lists.emptyList());
    JsonRpcResponse actual = method.response(request);
    assertThat(actual).isEqualToComparingFieldByField(expected);

    final Block block = appendBlock(transaction);

    // We've added one block, so there should be one new hash.
    expected = new JsonRpcSuccessResponse(null, Lists.newArrayList(block.getHash().toString()));
    actual = method.response(request);
    assertThat(actual).isEqualToComparingFieldByField(expected);

    // The queue should be flushed and return no results.
    expected = new JsonRpcSuccessResponse(null, Lists.emptyList());
    actual = method.response(request);
    assertThat(actual).isEqualToComparingFieldByField(expected);

    filterManager.uninstallFilter(filterId);

    assertThatFilterDoesNotExist(filterId);
  }

  @Test
  public void shouldReturnHashesIfNewPendingTransactions() {
    final String filterId = filterManager.installPendingTransactionFilter();

    assertThatFilterExists(filterId);

    final JsonRpcRequestContext request = requestWithParams(String.valueOf(filterId));

    // We haven't added any transactions, so the list of pending transactions should be empty.
    JsonRpcSuccessResponse expected = new JsonRpcSuccessResponse(null, Lists.emptyList());
    JsonRpcResponse actual = method.response(request);
    assertThat(actual).isEqualToComparingFieldByField(expected);

    transactions.addRemoteTransaction(transaction);

    // We've added one transaction, so there should be one new hash.
    expected =
        new JsonRpcSuccessResponse(null, Lists.newArrayList(String.valueOf(transaction.getHash())));
    actual = method.response(request);
    assertThat(actual).isEqualToComparingFieldByField(expected);

    // The queue should be flushed and return no results.
    expected = new JsonRpcSuccessResponse(null, Lists.emptyList());
    actual = method.response(request);
    assertThat(actual).isEqualToComparingFieldByField(expected);

    filterManager.uninstallFilter(filterId);

    assertThatFilterDoesNotExist(filterId);
  }

  private void assertThatFilterExists(final String filterId) {
    assertThat(filterExists(filterId)).isTrue();
  }

  private void assertThatFilterDoesNotExist(final String filterId) {
    assertThat(filterExists(filterId)).isFalse();
  }

  /**
   * Determines whether a specified filter exists.
   *
   * @param filterId The filter ID to check.
   * @return A boolean - true if the filter exists, false if not.
   */
  private boolean filterExists(final String filterId) {
    final JsonRpcResponse response = method.response(requestWithParams(String.valueOf(filterId)));
    if (response instanceof JsonRpcSuccessResponse) {
      return true;
    } else {
      assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
      assertThat(((JsonRpcErrorResponse) response).getError())
          .isEqualTo(JsonRpcError.FILTER_NOT_FOUND);
      return false;
    }
  }

  private Block appendBlock(final Transaction... transactionsToAdd) {
    return appendBlock(Difficulty.ONE, getHeaderForCurrentChainHead(), transactionsToAdd);
  }

  private BlockHeader getHeaderForCurrentChainHead() {
    return blockchain.getBlockHeader(blockchain.getChainHeadHash()).get();
  }

  private Block appendBlock(
      final Difficulty difficulty,
      final BlockHeader parentBlock,
      final Transaction... transactionsToAdd) {
    final List<Transaction> transactionList = asList(transactionsToAdd);
    final Block block =
        new Block(
            new BlockHeaderTestFixture()
                .difficulty(difficulty)
                .parentHash(parentBlock.getHash())
                .number(parentBlock.getNumber() + 1)
                .buildHeader(),
            new BlockBody(transactionList, emptyList()));
    final List<TransactionReceipt> transactionReceipts =
        transactionList.stream()
            .map(transaction -> new TransactionReceipt(1, 1, emptyList(), Optional.empty()))
            .collect(toList());
    blockchain.appendBlock(block, transactionReceipts);
    return block;
  }

  private Transaction createTransaction(final int transactionNumber) {
    return Transaction.builder()
        .type(TransactionType.FRONTIER)
        .gasLimit(100)
        .gasPrice(Wei.ZERO)
        .nonce(1)
        .payload(Bytes.EMPTY)
        .to(Address.ID)
        .value(Wei.of(transactionNumber))
        .sender(Address.ID)
        .chainId(BigInteger.ONE)
        .signAndBuild(keyPair);
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params));
  }
}
