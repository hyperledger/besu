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
package org.hyperledger.besu.plugins.graphql;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.LogTopic;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.LogWithMetadata;
import org.hyperledger.besu.plugin.data.SyncStatus;
import org.hyperledger.besu.plugin.services.BlockchainQueryService;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService;
import org.hyperledger.besu.plugin.services.sync.SynchronizationService;
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService;
import org.hyperledger.besu.plugins.graphql.internal.pojoadapter.BlockAdapterBase;
import org.hyperledger.besu.plugins.graphql.internal.pojoadapter.LogAdapter;
import org.hyperledger.besu.plugins.graphql.internal.pojoadapter.NormalBlockAdapter;
import org.hyperledger.besu.plugins.graphql.internal.pojoadapter.PendingStateAdapter;
import org.hyperledger.besu.plugins.graphql.internal.pojoadapter.SyncStateAdapter;
import org.hyperledger.besu.plugins.graphql.internal.pojoadapter.TransactionAdapter;
import org.hyperledger.besu.plugins.graphql.internal.query.BlockWithMetadata;
import org.hyperledger.besu.plugins.graphql.internal.query.ChainQueries;
import org.hyperledger.besu.plugins.graphql.internal.query.TransactionWithMetadata;
import org.hyperledger.besu.plugins.graphql.internal.response.GraphQLError;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;
import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains data fetchers for GraphQL queries.
 *
 * <p>Data fetchers are responsible for fetching data for a specific field. Each field in the schema
 * is associated with a data fetcher. When the field is being processed during a query, the
 * associated data fetcher is invoked to get the data for that field.
 *
 * <p>This class contains data fetchers for various fields such as syncing state, pending state, gas
 * price, chain ID, max priority fee per gas, range block, block, logs, and transaction.
 *
 * <p>Each data fetcher is a method that returns a `DataFetcher` object. The `DataFetcher` object
 * defines how to fetch the data for the field. It takes a `DataFetchingEnvironment` object as input
 * which contains all the context needed to fetch the data.
 */
public class GraphQLDataFetchers {

  private static final Logger LOG = LoggerFactory.getLogger(GraphQLDataFetchers.class);

  /**
   * Default maximum number of blocks a single {@code blocks(from,to)} query may span, used when no
   * explicit limit is supplied (e.g. by test call sites). Mirrors {@link
   * GraphQLConfiguration#DEFAULT_MAX_BLOCK_RANGE}.
   */
  public static final long DEFAULT_MAX_BLOCK_RANGE = GraphQLConfiguration.DEFAULT_MAX_BLOCK_RANGE;

  /**
   * Default maximum number of addresses a single {@code logs(filter)} query may name, used when no
   * explicit limit is supplied (e.g. by test call sites). Mirrors the default of Besu's {@code
   * --rpc-max-log-filter-addresses} option, so the GraphQL and JSON-RPC log queries share one
   * operator-visible limit.
   */
  public static final int DEFAULT_MAX_FILTER_ADDRESSES = 1000;

  private final long maxBlockRange;
  private final int maxFilterAddresses;

  /** Constructs a new GraphQLDataFetchers instance with the default caps on range queries. */
  public GraphQLDataFetchers() {
    this(DEFAULT_MAX_BLOCK_RANGE, DEFAULT_MAX_FILTER_ADDRESSES);
  }

  /**
   * Constructs a new GraphQLDataFetchers instance with an explicit cap on the span of a single
   * {@code blocks(from,to)} or {@code logs(filter)} range query.
   *
   * @param maxBlockRange the maximum number of blocks a single {@code blocks(from,to)} or {@code
   *     logs(filter)} query may span; 0 means no limit
   */
  public GraphQLDataFetchers(final long maxBlockRange) {
    this(maxBlockRange, DEFAULT_MAX_FILTER_ADDRESSES);
  }

  /**
   * Constructs a new GraphQLDataFetchers instance with explicit caps on the inputs of a log query.
   *
   * @param maxBlockRange the maximum number of blocks a single {@code blocks(from,to)} or {@code
   *     logs(filter)} query may span; 0 means no limit
   * @param maxFilterAddresses the maximum number of addresses a single {@code logs(filter)} query
   *     may name; 0 means no limit
   */
  public GraphQLDataFetchers(final long maxBlockRange, final int maxFilterAddresses) {
    this.maxBlockRange = maxBlockRange;
    this.maxFilterAddresses = maxFilterAddresses;
  }

  /**
   * Returns a DataFetcher that fetches the result of sending a raw transaction.
   *
   * <p>The DataFetcher is a functional interface. It has a single method that takes a
   * DataFetchingEnvironment object as input and returns the hash of the transaction if it is valid
   * and added to the transaction pool. If the transaction is invalid, it throws a GraphQLException
   * with the invalid reason. If the raw transaction data cannot be read, it throws a
   * GraphQLException with INVALID_PARAMS error.
   *
   * @return a DataFetcher that fetches the result of sending a raw transaction
   */
  DataFetcher<Optional<Bytes32>> getSendRawTransactionDataFetcher() {
    return dataFetchingEnvironment -> {
      try {
        final TransactionPoolService transactionPool =
            dataFetchingEnvironment
                .getGraphQlContext()
                .get(GraphQLContextType.TRANSACTION_POOL_SERVICE);
        final RlpConverterService rlpConverterService =
            dataFetchingEnvironment
                .getGraphQlContext()
                .get(GraphQLContextType.RLP_CONVERTER_SERVICE);
        final Bytes rawTran = dataFetchingEnvironment.getArgument("data");

        final Transaction transaction = rlpConverterService.buildTransactionFromRlp(rawTran);
        final Optional<String> invalidReason = transactionPool.addTransactionViaApi(transaction);
        if (invalidReason.isEmpty()) {
          return Optional.of(Bytes32.wrap(transaction.getHash().getBytes()));
        } else {
          throw new GraphQLException(GraphQLError.of(invalidReason.get()));
        }
      } catch (final IllegalArgumentException e) {
        throw new GraphQLException(GraphQLError.INVALID_PARAMS);
      }
    };
  }

  /**
   * Returns a DataFetcher that fetches the syncing state of the Ethereum node.
   *
   * <p>The DataFetcher is a functional interface. It has a single method that takes a
   * DataFetchingEnvironment object as input and returns the syncing state as an
   * Optional<SyncStateAdapter>.
   *
   * <p>The SyncStateAdapter is a wrapper around the SyncStatus of the Ethereum node. It provides
   * information about the current syncing state of the node such as the current block, highest
   * block, and starting block.
   *
   * @return a DataFetcher that fetches the syncing state of the Ethereum node
   */
  DataFetcher<Optional<SyncStateAdapter>> getSyncingDataFetcher() {
    return dataFetchingEnvironment -> {
      final SynchronizationService synchronizer =
          dataFetchingEnvironment
              .getGraphQlContext()
              .get(GraphQLContextType.SYNCHRONIZATION_SERVICE);
      final Optional<SyncStatus> syncStatus = synchronizer.getSyncStatus();
      return syncStatus.map(SyncStateAdapter::new);
    };
  }

  DataFetcher<Optional<PendingStateAdapter>> getPendingStateDataFetcher() {
    return dataFetchingEnvironment -> {
      final TransactionPoolService txPool =
          dataFetchingEnvironment
              .getGraphQlContext()
              .get(GraphQLContextType.TRANSACTION_POOL_SERVICE);
      return Optional.of(new PendingStateAdapter(txPool));
    };
  }

  DataFetcher<Wei> getGasPriceDataFetcher() {
    return dataFetchingEnvironment -> {
      final GraphQLContext graphQLContext = dataFetchingEnvironment.getGraphQlContext();
      final BlockchainQueryService blockchainQueries =
          graphQLContext.get(GraphQLContextType.BLOCKCHAIN_QUERY_SERVICE);
      return blockchainQueries.getOracleGasPrice();
    };
  }

  /**
   * Returns a DataFetcher that fetches the chain ID of the Ethereum node.
   *
   * <p>The DataFetcher is a functional interface. It has a single method that takes a
   * DataFetchingEnvironment object as input and returns the chain ID as an {@code
   * Optional<BigInteger>}.
   *
   * @return a DataFetcher that fetches the chain ID of the Ethereum node
   */
  public DataFetcher<Optional<BigInteger>> getChainIdDataFetcher() {
    return dataFetchingEnvironment -> {
      final GraphQLContext graphQLContext = dataFetchingEnvironment.getGraphQlContext();
      final BlockchainService blockchainService =
          graphQLContext.get(GraphQLContextType.BLOCKCHAIN_SERVICE);
      return blockchainService.getChainId();
    };
  }

  /**
   * Returns a DataFetcher that fetches the maximum priority fee per gas of the Ethereum node.
   *
   * <p>The DataFetcher is a functional interface. It has a single method that takes a
   * DataFetchingEnvironment object as input and returns the maximum priority fee per gas as a Wei
   * object.
   *
   * @return a DataFetcher that fetches the maximum priority fee per gas of the Ethereum node
   */
  public DataFetcher<Wei> getMaxPriorityFeePerGasDataFetcher() {
    return dataFetchingEnvironment -> {
      final BlockchainQueryService blockchainQuery =
          dataFetchingEnvironment
              .getGraphQlContext()
              .get(GraphQLContextType.BLOCKCHAIN_QUERY_SERVICE);
      return blockchainQuery.getOracleMaxPriorityFeePerGas();
    };
  }

  DataFetcher<List<NormalBlockAdapter>> getRangeBlockDataFetcher() {

    return dataFetchingEnvironment -> {
      final ChainQueries blockchainQuery =
          ChainQueries.fromContext(dataFetchingEnvironment.getGraphQlContext());
      final Supplier<Boolean> isAlive =
          dataFetchingEnvironment.getGraphQlContext().get(GraphQLContextType.IS_ALIVE_HANDLER);

      final long chainHeadBlockNumber = blockchainQuery.headBlockNumber();
      long from;
      if (dataFetchingEnvironment.containsArgument("from")) {
        from = dataFetchingEnvironment.getArgument("from");
      } else {
        from = chainHeadBlockNumber;
      }
      long to;
      if (dataFetchingEnvironment.containsArgument("to")) {
        to = dataFetchingEnvironment.getArgument("to");
      } else {
        to = chainHeadBlockNumber;
      }
      if (from < 0 || from > to) {
        throw new GraphQLException(GraphQLError.INVALID_PARAMS);
      }
      // Checked on the caller-supplied (pre-clamp) span so an attacker can't sidestep the cap by
      // supplying a `to` far beyond the chain head, relying on the clamp below to shrink it first.
      if (maxBlockRange > 0 && (to - from) > maxBlockRange) {
        throw new GraphQLException(GraphQLError.INVALID_PARAMS);
      }
      // A supplied `to` beyond the chain head only ever shrinks the loop below, so clamping here
      // (after the span check above) can only reduce work, never let more through.
      to = Math.min(to, chainHeadBlockNumber);

      final List<NormalBlockAdapter> results = new ArrayList<>();
      for (long i = from; i <= to; i++) {
        // IS_ALIVE_HANDLER is always populated for real HTTP requests (GraphQLHttpService), but
        // any call site that builds a GraphQlContext without it (tests, future callers) should
        // fail open here rather than NPE inside the loop.
        if (isAlive != null) {
          stopIfExpired(isAlive);
        }
        final Optional<BlockWithMetadata> block = blockchainQuery.blockByNumber(i);
        block.ifPresent(e -> results.add(new NormalBlockAdapter(e)));
      }
      return results;
    };
  }

  /**
   * Aborts a query whose time is up, the same way as Besu's backend queries do.
   *
   * @param isAlive whether the query may still run
   */
  private static void stopIfExpired(final Supplier<Boolean> isAlive) {
    if (!isAlive.get()) {
      LOG.warn("Zombie backend query detected [ {} ], aborting process.", "unnamed");
      throw new QueryTimeoutException();
    }
  }

  /** Thrown when a query runs past the HTTP timeout, with the message of Besu's RPC timeouts. */
  static class QueryTimeoutException extends RuntimeException {
    QueryTimeoutException() {
      super("Timeout expired");
    }
  }

  /**
   * Returns a DataFetcher that fetches a specific block in the Ethereum blockchain.
   *
   * <p>The DataFetcher is a functional interface. It has a single method that takes a
   * DataFetchingEnvironment object as input. This method fetches a block based on either a block
   * number or a block hash. If both a block number and a block hash are provided, it throws a
   * GraphQLException with INVALID_PARAMS error. If neither a block number nor a block hash is
   * provided, it fetches the latest block.
   *
   * <p>The fetched block is then wrapped in a {@link NormalBlockAdapter} and returned as an {@code
   * Optional<NormalBlockAdapter>}.
   *
   * @return a DataFetcher that fetches a specific block in the Ethereum blockchain
   */
  public DataFetcher<Optional<NormalBlockAdapter>> getBlockDataFetcher() {

    return dataFetchingEnvironment -> {
      final ChainQueries blockchain =
          ChainQueries.fromContext(dataFetchingEnvironment.getGraphQlContext());
      final Long number = dataFetchingEnvironment.getArgument("number");
      final Hash hash = dataFetchingEnvironment.getArgument("hash");
      if ((number != null) && (hash != null)) {
        throw new GraphQLException(GraphQLError.INVALID_PARAMS);
      }

      final Optional<BlockWithMetadata> block;
      if (number != null) {
        block = blockchain.blockByNumber(number);
        checkArgument(block.isPresent(), "Block number %s was not found", number);
      } else if (hash != null) {
        block = blockchain.blockByHash(hash);
        Preconditions.checkArgument(block.isPresent(), "Block hash %s was not found", hash);
      } else {
        block = blockchain.latestBlock();
      }
      return block.map(NormalBlockAdapter::new);
    };
  }

  DataFetcher<Optional<List<LogAdapter>>> getLogsDataFetcher() {
    return dataFetchingEnvironment -> {
      final ChainQueries chainQueries =
          ChainQueries.fromContext(dataFetchingEnvironment.getGraphQlContext());
      final BlockchainQueryService blockchainQuery =
          dataFetchingEnvironment
              .getGraphQlContext()
              .get(GraphQLContextType.BLOCKCHAIN_QUERY_SERVICE);

      final Map<String, Object> filter = dataFetchingEnvironment.getArgument("filter");

      final long currentBlock = chainQueries.headBlockNumber();
      final long fromBlock = (Long) filter.getOrDefault("fromBlock", currentBlock);
      final long toBlock = (Long) filter.getOrDefault("toBlock", currentBlock);

      if (fromBlock < 0) {
        throw new GraphQLException(GraphQLError.INVALID_PARAMS);
      }
      if (fromBlock > toBlock) {
        throw new GraphQLException(GraphQLError.INVALID_PARAMS);
      }
      // Checked on the caller-supplied (pre-clamp) span so an attacker can't sidestep the cap by
      // supplying a `toBlock` far beyond the chain head, relying on the log query to short-circuit.
      if (maxBlockRange > 0 && (toBlock - fromBlock) > maxBlockRange) {
        throw new GraphQLException(GraphQLError.INVALID_PARAMS);
      }

      @SuppressWarnings("unchecked")
      final List<Address> addrs = (List<Address>) filter.get("addresses");
      // The address list costs a bloom lookup per address per block, so the same cap that
      // eth_newFilter and eth_subscribe enforce applies here; an uncapped list would let one
      // unauthenticated query do orders of magnitude more work than a capped filter would.
      if (maxFilterAddresses > 0 && addrs != null && addrs.size() > maxFilterAddresses) {
        throw new GraphQLException(GraphQLError.INVALID_PARAMS);
      }
      // `topics` is nullable in the schema, and the schema's own documentation says "[] or nil
      // matches any topic list", so an omitted value has to behave like an empty one rather than
      // being dereferenced. graphql-java puts the key in the map with a null value when a client
      // writes `topics: null` explicitly, so getOrDefault is not enough on its own.
      // (`addrs` may stay null: an empty address list matches any address.)
      @SuppressWarnings("unchecked")
      final List<List<LogTopic>> topics =
          Optional.ofNullable((List<List<LogTopic>>) filter.get("topics")).orElse(List.of());

      final List<List<Bytes32>> transformedTopics = BlockAdapterBase.toQueryTopics(topics);

      final Supplier<Boolean> isAlive =
          dataFetchingEnvironment.getGraphQlContext().get(GraphQLContextType.IS_ALIVE_HANDLER);
      final List<LogWithMetadata> logs =
          blockchainQuery.getLogs(
              fromBlock,
              toBlock,
              addrs == null ? List.of() : addrs,
              transformedTopics,
              // fail open without an alive handler, as the blocks query does
              isAlive == null ? () -> true : isAlive::get);
      final List<LogAdapter> results = new ArrayList<>();
      for (final LogWithMetadata log : logs) {
        results.add(new LogAdapter(log));
      }
      return Optional.of(results);
    };
  }

  DataFetcher<Optional<TransactionAdapter>> getTransactionDataFetcher() {
    return dataFetchingEnvironment -> {
      final ChainQueries blockchain =
          ChainQueries.fromContext(dataFetchingEnvironment.getGraphQlContext());
      final Hash hash = dataFetchingEnvironment.getArgument("hash");
      final Optional<TransactionWithMetadata> tran = blockchain.transactionByHash(hash);
      return tran.map(this::getTransactionAdapter);
    };
  }

  private TransactionAdapter getTransactionAdapter(
      final TransactionWithMetadata transactionWithMetadata) {
    return new TransactionAdapter(transactionWithMetadata);
  }
}
