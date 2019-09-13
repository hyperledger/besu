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
package tech.pegasys.pantheon.ethereum.api.graphql.internal.pojoadapter;

import tech.pegasys.pantheon.ethereum.api.BlockWithMetadata;
import tech.pegasys.pantheon.ethereum.api.LogWithMetadata;
import tech.pegasys.pantheon.ethereum.api.LogsQuery;
import tech.pegasys.pantheon.ethereum.api.TransactionWithMetadata;
import tech.pegasys.pantheon.ethereum.api.graphql.GraphQLDataFetcherContext;
import tech.pegasys.pantheon.ethereum.api.graphql.internal.BlockchainQuery;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.LogTopic;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.transaction.CallParameter;
import tech.pegasys.pantheon.ethereum.transaction.TransactionSimulator;
import tech.pegasys.pantheon.ethereum.transaction.TransactionSimulatorResult;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.primitives.Longs;
import graphql.schema.DataFetchingEnvironment;

@SuppressWarnings("unused") // reflected by GraphQL
public class BlockAdapterBase extends AdapterBase {

  private final BlockHeader header;

  BlockAdapterBase(final BlockHeader header) {
    this.header = header;
  }

  public Optional<NormalBlockAdapter> getParent(final DataFetchingEnvironment environment) {
    final BlockchainQuery query = getBlockchainQuery(environment);
    final Hash parentHash = header.getParentHash();
    final Optional<BlockWithMetadata<TransactionWithMetadata, Hash>> block =
        query.blockByHash(parentHash);
    return block.map(NormalBlockAdapter::new);
  }

  public Optional<Bytes32> getHash() {
    return Optional.of(header.getHash());
  }

  public Optional<BytesValue> getNonce() {
    final long nonce = header.getNonce();
    final byte[] bytes = Longs.toByteArray(nonce);
    return Optional.of(BytesValue.wrap(bytes));
  }

  public Optional<Bytes32> getTransactionsRoot() {
    return Optional.of(header.getTransactionsRoot());
  }

  public Optional<Bytes32> getStateRoot() {
    return Optional.of(header.getStateRoot());
  }

  public Optional<Bytes32> getReceiptsRoot() {
    return Optional.of(header.getReceiptsRoot());
  }

  public Optional<AccountAdapter> getMiner(final DataFetchingEnvironment environment) {

    final BlockchainQuery query = getBlockchainQuery(environment);
    long blockNumber = header.getNumber();
    final Long bn = environment.getArgument("block");
    if (bn != null) {
      blockNumber = bn;
    }
    return Optional.of(
        new AccountAdapter(query.getWorldState(blockNumber).get().get(header.getCoinbase())));
  }

  public Optional<BytesValue> getExtraData() {
    return Optional.of(header.getExtraData());
  }

  public Optional<Long> getGasLimit() {
    return Optional.of(header.getGasLimit());
  }

  public Optional<Long> getGasUsed() {
    return Optional.of(header.getGasUsed());
  }

  public Optional<UInt256> getTimestamp() {
    return Optional.of(UInt256.of(header.getTimestamp()));
  }

  public Optional<BytesValue> getLogsBloom() {
    return Optional.of(header.getLogsBloom().getBytes());
  }

  public Optional<Bytes32> getMixHash() {
    return Optional.of(header.getMixHash());
  }

  public Optional<UInt256> getDifficulty() {
    return Optional.of(header.getDifficulty());
  }

  public Optional<Bytes32> getOmmerHash() {
    return Optional.of(header.getOmmersHash());
  }

  public Optional<Long> getNumber() {
    final long bn = header.getNumber();
    return Optional.of(bn);
  }

  public Optional<AccountAdapter> getAccount(final DataFetchingEnvironment environment) {

    final BlockchainQuery query = getBlockchainQuery(environment);
    final long bn = header.getNumber();
    final WorldState ws = query.getWorldState(bn).get();

    if (ws != null) {
      final Address addr = environment.getArgument("address");
      return Optional.of(new AccountAdapter(ws.get(addr)));
    }
    return Optional.empty();
  }

  public List<LogAdapter> getLogs(final DataFetchingEnvironment environment) {

    final Map<String, Object> filter = environment.getArgument("filter");

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

    final BlockchainQuery blockchain = getBlockchainQuery(environment);

    final Hash hash = header.getHash();
    final List<LogWithMetadata> logs = blockchain.matchingLogs(hash, query);
    final List<LogAdapter> results = new ArrayList<>();
    for (final LogWithMetadata log : logs) {
      results.add(new LogAdapter(log));
    }
    return results;
  }

  public Optional<Long> getEstimateGas(final DataFetchingEnvironment environment) {
    final Optional<CallResult> result = executeCall(environment);
    return result.map(CallResult::getGasUsed);
  }

  public Optional<CallResult> getCall(final DataFetchingEnvironment environment) {
    return executeCall(environment);
  }

  private Optional<CallResult> executeCall(final DataFetchingEnvironment environment) {
    final Map<String, Object> callData = environment.getArgument("data");
    final Address from = (Address) callData.get("from");
    final Address to = (Address) callData.get("to");
    final Long gas = (Long) callData.get("gas");
    final UInt256 gasPrice = (UInt256) callData.get("gasPrice");
    final UInt256 value = (UInt256) callData.get("value");
    final BytesValue data = (BytesValue) callData.get("data");

    final BlockchainQuery query = getBlockchainQuery(environment);
    final ProtocolSchedule<?> protocolSchedule =
        ((GraphQLDataFetcherContext) environment.getContext()).getProtocolSchedule();
    final long bn = header.getNumber();

    final TransactionSimulator transactionSimulator =
        new TransactionSimulator(
            query.getBlockchain(), query.getWorldStateArchive(), protocolSchedule);

    long gasParam = -1;
    Wei gasPriceParam = null;
    Wei valueParam = null;
    if (gas != null) {
      gasParam = gas;
    }
    if (gasPrice != null) {
      gasPriceParam = Wei.of(gasPrice);
    }
    if (value != null) {
      valueParam = Wei.of(value);
    }
    final CallParameter param =
        new CallParameter(from, to, gasParam, gasPriceParam, valueParam, data);

    final Optional<TransactionSimulatorResult> opt = transactionSimulator.process(param, bn);
    if (opt.isPresent()) {
      final TransactionSimulatorResult result = opt.get();
      long status = 0;
      if (result.isSuccessful()) {
        status = 1;
      }
      final CallResult callResult =
          new CallResult(status, result.getGasEstimate(), result.getOutput());
      return Optional.of(callResult);
    }
    return Optional.empty();
  }
}
