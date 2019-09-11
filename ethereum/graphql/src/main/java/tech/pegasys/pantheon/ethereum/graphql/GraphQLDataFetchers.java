/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.graphql;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.graphql.internal.BlockWithMetadata;
import tech.pegasys.pantheon.ethereum.graphql.internal.BlockchainQuery;
import tech.pegasys.pantheon.ethereum.graphql.internal.TransactionWithMetadata;
import tech.pegasys.pantheon.ethereum.graphql.internal.pojoadapter.AccountAdapter;
import tech.pegasys.pantheon.ethereum.graphql.internal.pojoadapter.NormalBlockAdapter;
import tech.pegasys.pantheon.ethereum.graphql.internal.pojoadapter.PendingStateAdapter;
import tech.pegasys.pantheon.ethereum.graphql.internal.pojoadapter.SyncStateAdapter;
import tech.pegasys.pantheon.ethereum.graphql.internal.pojoadapter.TransactionAdapter;
import tech.pegasys.pantheon.ethereum.graphql.internal.response.GraphQLError;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Capability;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.plugin.data.SyncStatus;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

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
          return Optional.of(transaction.hash());
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
      final BlockchainQuery blockchainQuery =
          ((GraphQLDataFetcherContext) dataFetchingEnvironment.getContext()).getBlockchainQuery();

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
      final BlockchainQuery blockchain =
          ((GraphQLDataFetcherContext) dataFetchingEnvironment.getContext()).getBlockchainQuery();
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
        checkArgument(block.isPresent(), "Block hash %s was not found", hash);
      } else {
        block = blockchain.latestBlock();
      }
      return block.map(NormalBlockAdapter::new);
    };
  }

  DataFetcher<Optional<AccountAdapter>> getAccountDataFetcher() {
    return dataFetchingEnvironment -> {
      final BlockchainQuery blockchainQuery =
          ((GraphQLDataFetcherContext) dataFetchingEnvironment.getContext()).getBlockchainQuery();
      final Address addr = dataFetchingEnvironment.getArgument("address");
      final Long bn = dataFetchingEnvironment.getArgument("blockNumber");
      if (bn != null) {
        final Optional<WorldState> ws = blockchainQuery.getWorldState(bn);
        if (ws.isPresent()) {
          final Account account = ws.get().get(addr);
          checkArgument(account != null, "Account with address %s does not exist", addr);
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
        final Optional<WorldState> ows = blockchainQuery.getWorldState(latestBn);
        return ows.flatMap(
                ws -> {
                  Account account = ws.get(addr);
                  checkArgument(account != null, "Account with address %s does not exist", addr);
                  return Optional.ofNullable(account);
                })
            .map(AccountAdapter::new);
      }
    };
  }

  DataFetcher<Optional<TransactionAdapter>> getTransactionDataFetcher() {
    return dataFetchingEnvironment -> {
      final BlockchainQuery blockchain =
          ((GraphQLDataFetcherContext) dataFetchingEnvironment.getContext()).getBlockchainQuery();
      final Bytes32 hash = dataFetchingEnvironment.getArgument("hash");
      final Optional<TransactionWithMetadata> tran = blockchain.transactionByHash(Hash.wrap(hash));
      return tran.map(TransactionAdapter::new);
    };
  }
}
