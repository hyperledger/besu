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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.ethereum.api.LogWithMetadata;
import org.hyperledger.besu.ethereum.api.LogsQuery;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import io.vertx.core.AbstractVerticle;

/** Manages JSON-RPC filter events. */
public class FilterManager extends AbstractVerticle {

  private static final int FILTER_TIMEOUT_CHECK_TIMER = 10000;

  private final FilterIdGenerator filterIdGenerator;
  private final FilterRepository filterRepository;
  private final BlockchainQueries blockchainQueries;

  public FilterManager(
      final BlockchainQueries blockchainQueries,
      final TransactionPool transactionPool,
      final FilterIdGenerator filterIdGenerator,
      final FilterRepository filterRepository) {
    this.filterIdGenerator = filterIdGenerator;
    this.filterRepository = filterRepository;
    checkNotNull(blockchainQueries.getBlockchain());
    blockchainQueries.getBlockchain().observeBlockAdded(this::recordBlockEvent);
    transactionPool.subscribePendingTransactions(this::recordPendingTransactionEvent);
    this.blockchainQueries = blockchainQueries;
  }

  @Override
  public void start() {
    startFilterTimeoutTimer();
  }

  @Override
  public void stop() {
    filterRepository.deleteAll();
  }

  private void startFilterTimeoutTimer() {
    vertx.setPeriodic(
        FILTER_TIMEOUT_CHECK_TIMER,
        timerId ->
            vertx.executeBlocking(
                future -> new FilterTimeoutMonitor(filterRepository).checkFilters(), result -> {}));
  }

  /**
   * Installs a new block filter
   *
   * @return the block filter id
   */
  public String installBlockFilter() {
    final String filterId = filterIdGenerator.nextId();
    filterRepository.save(new BlockFilter(filterId));
    return filterId;
  }

  /**
   * Installs a pending transaction filter
   *
   * @return the transaction filter id
   */
  public String installPendingTransactionFilter() {
    final String filterId = filterIdGenerator.nextId();
    filterRepository.save(new PendingTransactionFilter(filterId));
    return filterId;
  }

  /**
   * Installs a new log filter
   *
   * @param fromBlock {@link BlockParameter} Integer block number, or latest/pending/earliest.
   * @param toBlock {@link BlockParameter} Integer block number, or latest/pending/earliest.
   * @param logsQuery {@link LogsQuery} Addresses and/or topics to filter by
   * @return the log filter id
   */
  public String installLogFilter(
      final BlockParameter fromBlock, final BlockParameter toBlock, final LogsQuery logsQuery) {
    final String filterId = filterIdGenerator.nextId();
    filterRepository.save(new LogFilter(filterId, fromBlock, toBlock, logsQuery));
    return filterId;
  }

  /**
   * Uninstalls the specified filter.
   *
   * @param filterId the id of the filter to remove
   * @return {@code true} if the filter was successfully removed; otherwise {@code false}
   */
  public boolean uninstallFilter(final String filterId) {
    if (filterRepository.exists(filterId)) {
      filterRepository.delete(filterId);
      return true;
    } else {
      return false;
    }
  }

  public void recordBlockEvent(final BlockAddedEvent event, final Blockchain blockchain) {
    final Hash blockHash = event.getBlock().getHash();
    final Collection<BlockFilter> blockFilters =
        filterRepository.getFiltersOfType(BlockFilter.class);
    blockFilters.forEach(
        (filter) -> {
          synchronized (filter) {
            filter.addBlockHash(blockHash);
          }
        });

    checkBlockchainForMatchingLogsForFilters();
  }

  private void checkBlockchainForMatchingLogsForFilters() {
    final Collection<LogFilter> logFilters = filterRepository.getFiltersOfType(LogFilter.class);
    logFilters.forEach(
        (filter) -> {
          final long headBlockNumber = blockchainQueries.headBlockNumber();
          final long toBlockNumber =
              filter.getToBlock().getNumber().orElse(blockchainQueries.headBlockNumber());
          final List<LogWithMetadata> logs =
              blockchainQueries.matchingLogs(headBlockNumber, toBlockNumber, filter.getLogsQuery());
          filter.addLog(logs);
        });
  }

  @VisibleForTesting
  void recordPendingTransactionEvent(final Transaction transaction) {
    final Collection<PendingTransactionFilter> pendingTransactionFilters =
        filterRepository.getFiltersOfType(PendingTransactionFilter.class);
    if (pendingTransactionFilters.isEmpty()) {
      return;
    }

    pendingTransactionFilters.forEach(
        (filter) -> {
          synchronized (filter) {
            filter.addTransactionHash(transaction.hash());
          }
        });
  }

  /**
   * Gets the new block hashes that have occurred since the filter was last checked.
   *
   * @param filterId the id of the filter to get the new blocks for
   * @return the new block hashes that have occurred since the filter was last checked
   */
  public List<Hash> blockChanges(final String filterId) {
    final BlockFilter filter = filterRepository.getFilter(filterId, BlockFilter.class).orElse(null);
    if (filter == null) {
      return null;
    }

    final List<Hash> hashes;
    synchronized (filter) {
      hashes = new ArrayList<>(filter.blockHashes());
      filter.clearBlockHashes();
      filter.resetExpireTime();
    }
    return hashes;
  }

  /**
   * Gets the pending transactions that have occurred since the filter was last checked.
   *
   * @param filterId the id of the filter to get the pending transactions for
   * @return the new pending transaction hashes that have occurred since the filter was last checked
   */
  public List<Hash> pendingTransactionChanges(final String filterId) {
    final PendingTransactionFilter filter =
        filterRepository.getFilter(filterId, PendingTransactionFilter.class).orElse(null);
    if (filter == null) {
      return null;
    }

    final List<Hash> hashes;
    synchronized (filter) {
      hashes = new ArrayList<>(filter.transactionHashes());
      filter.clearTransactionHashes();
      filter.resetExpireTime();
    }
    return hashes;
  }

  public List<LogWithMetadata> logsChanges(final String filterId) {
    final LogFilter filter = filterRepository.getFilter(filterId, LogFilter.class).orElse(null);
    if (filter == null) {
      return null;
    }

    final List<LogWithMetadata> logs;
    synchronized (filter) {
      logs = new ArrayList<>(filter.logs());
      filter.clearLogs();
      filter.resetExpireTime();
    }
    return logs;
  }

  public List<LogWithMetadata> logs(final String filterId) {
    final LogFilter filter = filterRepository.getFilter(filterId, LogFilter.class).orElse(null);
    if (filter == null) {
      return null;
    } else {
      filter.resetExpireTime();
    }

    final long fromBlockNumber =
        filter.getFromBlock().getNumber().orElse(blockchainQueries.headBlockNumber());
    final long toBlockNumber =
        filter.getToBlock().getNumber().orElse(blockchainQueries.headBlockNumber());

    return blockchainQueries.matchingLogs(fromBlockNumber, toBlockNumber, filter.getLogsQuery());
  }
}
