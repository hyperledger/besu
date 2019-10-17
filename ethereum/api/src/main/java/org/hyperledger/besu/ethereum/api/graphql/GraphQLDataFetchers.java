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
package org.hyperledger.besu.ethereum.api.graphql;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter.AccountAdapter;
import org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter.LogAdapter;
import org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter.NormalBlockAdapter;
import org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter.PendingStateAdapter;
import org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter.SyncStateAdapter;
import org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter.TransactionAdapter;
import org.hyperledger.besu.ethereum.api.graphql.internal.response.GraphQLError;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.LogWithMetadata;
import org.hyperledger.besu.ethereum.api.query.LogsQuery;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogTopic;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.plugin.data.SyncStatus;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import graphql.schema.DataFetcher;

public class GraphQLDataFetchers {
  public GraphQLDataFetchers(final Set<Capability> supportedCapabilities) {
    final OptionalInt version =
        supportedCapabilities.stream()
            .filter(cap -> EthProtocol.NAME.equals(cap.getName()))
            .mapToInt(Capability::getVersion)
            .max();
    highestEthVersion = version.isPresent() ? version.getAsInt() : null;
  }

  private final Integer highestEthVersion;

  DataFetcher<Optional<Integer>> getProtocolVersionDataFetcher() {
    return dataFetchingEnvironment -> Optional.of(highestEthVersion);
  }

  DataFetcher<Optional<Bytes32>> getSendRawTransactionDataFetcher() {
    return dataFetchingEnvironment -> {
      try {
        final TransactionPool transactionPool =
            ((GraphQLDataFetcherContext) dataFetchingEnvironment.getContext()).getTransactionPool();
        final BytesValue rawTran = dataFetchingEnvironment.getArgument("data");

        final Transaction transaction = Transaction.readFrom(RLP.input(rawTran));
        final ValidationResult<TransactionInvalidReason> validationResult =
            transactionPool.addLocalTransaction(transaction);
        if (validationResult.isValid()) {
          return Optional.of(transaction.getHash());
        } else {
          throw new GraphQLException(GraphQLError.of(validationResult.getInvalidReason()));
        }
      } catch (final IllegalArgumentException | RLPException e) {
        throw new GraphQLException(GraphQLError.INVALID_PARAMS);
      }
    };
  }

  DataFetcher<Optional<SyncStateAdapter>> getSyncingDataFetcher() {
    return dataFetchingEnvironment -> {
      final Synchronizer synchronizer =
          ((GraphQLDataFetcherContext) dataFetchingEnvironment.getContext()).getSynchronizer();
      final Optional<SyncStatus> syncStatus = synchronizer.getSyncStatus();
      return syncStatus.map(SyncStateAdapter::new);
    };
  }

  DataFetcher<Optional<PendingStateAdapter>> getPendingStateDataFetcher() {
    return dataFetchingEnvironment -> {
      final TransactionPool txPool =
          ((GraphQLDataFetcherContext) dataFetchingEnvironment.getContext()).getTransactionPool();
      return Optional.of(new PendingStateAdapter(txPool.getPendingTransactions()));
    };
  }

  DataFetcher<Optional<UInt256>> getGasPriceDataFetcher() {
    return dataFetchingEnvironment -> {
      final MiningCoordinator miningCoordinator =
          ((GraphQLDataFetcherContext) dataFetchingEnvironment.getContext()).getMiningCoordinator();

      return Optional.of(miningCoordinator.getMinTransactionGasPrice().asUInt256());
    };
  }

  DataFetcher<List<NormalBlockAdapter>> getRangeBlockDataFetcher() {

    return dataFetchingEnvironment -> {
      final BlockchainQueries blockchainQuery =
          ((GraphQLDataFetcherContext) dataFetchingEnvironment.getContext()).getBlockchainQueries();

      final long from = dataFetchingEnvironment.getArgument("from");
      final long to;
      if (dataFetchingEnvironment.containsArgument("to")) {
        to = dataFetchingEnvironment.getArgument("to");
      } else {
        to = blockchainQuery.latestBlock().map(block -> block.getHeader().getNumber()).orElse(0L);
      }
      if (from > to) {
        throw new GraphQLException(GraphQLError.INVALID_PARAMS);
      }

      final List<NormalBlockAdapter> results = new ArrayList<>();
      for (long i = from; i <= to; i++) {
        final Optional<BlockWithMetadata<TransactionWithMetadata, Hash>> block =
            blockchainQuery.blockByNumber(i);
        block.ifPresent(e -> results.add(new NormalBlockAdapter(e)));
      }
      return results;
    };
  }

  public DataFetcher<Optional<NormalBlockAdapter>> getBlockDataFetcher() {

    return dataFetchingEnvironment -> {
      final BlockchainQueries blockchain =
          ((GraphQLDataFetcherContext) dataFetchingEnvironment.getContext()).getBlockchainQueries();
      final Long number = dataFetchingEnvironment.getArgument("number");
      final Bytes32 hash = dataFetchingEnvironment.getArgument("hash");
      if ((number != null) && (hash != null)) {
        throw new GraphQLException(GraphQLError.INVALID_PARAMS);
      }

      final Optional<BlockWithMetadata<TransactionWithMetadata, Hash>> block;
      if (number != null) {
        block = blockchain.blockByNumber(number);
        checkArgument(block.isPresent(), "Block number %s was not found", number);
      } else if (hash != null) {
        block = blockchain.blockByHash(Hash.wrap(hash));
        Preconditions.checkArgument(block.isPresent(), "Block hash %s was not found", hash);
      } else {
        block = blockchain.latestBlock();
      }
      return block.map(NormalBlockAdapter::new);
    };
  }

  DataFetcher<Optional<AccountAdapter>> getAccountDataFetcher() {
    return dataFetchingEnvironment -> {
      final BlockchainQueries blockchainQuery =
          ((GraphQLDataFetcherContext) dataFetchingEnvironment.getContext()).getBlockchainQueries();
      final Address addr = dataFetchingEnvironment.getArgument("address");
      final Long bn = dataFetchingEnvironment.getArgument("blockNumber");
      if (bn != null) {
        final Optional<MutableWorldState> ws = blockchainQuery.getWorldState(bn);
        if (ws.isPresent()) {
          final Account account = ws.get().get(addr);
          Preconditions.checkArgument(
              account != null, "Account with address %s does not exist", addr);
          return Optional.of(new AccountAdapter(account));
        } else if (bn > blockchainQuery.getBlockchain().getChainHeadBlockNumber()) {
          // block is past chainhead
          throw new GraphQLException(GraphQLError.INVALID_PARAMS);
        } else {
          // we don't have that block
          throw new GraphQLException(GraphQLError.CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE);
        }
      } else {
        // return account on latest block
        final long latestBn = blockchainQuery.latestBlock().get().getHeader().getNumber();
        final Optional<MutableWorldState> ows = blockchainQuery.getWorldState(latestBn);
        return ows.flatMap(
                ws -> {
                  Account account = ws.get(addr);
                  Preconditions.checkArgument(
                      account != null, "Account with address %s does not exist", addr);
                  return Optional.ofNullable(account);
                })
            .map(AccountAdapter::new);
      }
    };
  }

  DataFetcher<Optional<List<LogAdapter>>> getLogsDataFetcher() {
    return dataFetchingEnvironment -> {
      final BlockchainQueries blockchainQuery =
          ((GraphQLDataFetcherContext) dataFetchingEnvironment.getContext()).getBlockchainQueries();

      final Map<String, Object> filter = dataFetchingEnvironment.getArgument("filter");

      final long currentBlock = blockchainQuery.getBlockchain().getChainHeadBlockNumber();
      final long fromBlock = (Long) filter.getOrDefault("fromBlock", currentBlock);
      final long toBlock = (Long) filter.getOrDefault("toBlock", currentBlock);

      if (fromBlock > toBlock) {
        throw new GraphQLException(GraphQLError.INVALID_PARAMS);
      }

      @SuppressWarnings("unchecked")
      final List<Address> addrs = (List<Address>) filter.get("addresses");
      @SuppressWarnings("unchecked")
      final List<List<Bytes32>> topics = (List<List<Bytes32>>) filter.get("topics");

      final List<List<LogTopic>> transformedTopics = new ArrayList<>();
      for (final List<Bytes32> topic : topics) {
        transformedTopics.add(topic.stream().map(LogTopic::of).collect(Collectors.toList()));
      }

      final LogsQuery query =
          new LogsQuery.Builder().addresses(addrs).topics(transformedTopics).build();

      final List<LogWithMetadata> logs = blockchainQuery.matchingLogs(fromBlock, toBlock, query);
      final List<LogAdapter> results = new ArrayList<>();
      for (final LogWithMetadata log : logs) {
        results.add(new LogAdapter(log));
      }
      return Optional.of(results);
    };
  }

  DataFetcher<Optional<TransactionAdapter>> getTransactionDataFetcher() {
    return dataFetchingEnvironment -> {
      final BlockchainQueries blockchain =
          ((GraphQLDataFetcherContext) dataFetchingEnvironment.getContext()).getBlockchainQueries();
      final Bytes32 hash = dataFetchingEnvironment.getArgument("hash");
      final Optional<TransactionWithMetadata> tran = blockchain.transactionByHash(Hash.wrap(hash));
      return tran.map(TransactionAdapter::new);
    };
  }
}
