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
package org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLContextType;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.LogsQuery;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.primitives.Longs;
import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

@SuppressWarnings("unused") // reflected by GraphQL
public class BlockAdapterBase extends AdapterBase {

  private final BlockHeader header;

  BlockAdapterBase(final BlockHeader header) {
    this.header = header;
  }

  public Optional<NormalBlockAdapter> getParent(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    final Hash parentHash = header.getParentHash();
    final Optional<BlockWithMetadata<TransactionWithMetadata, Hash>> block =
        query.blockByHash(parentHash);
    return block.map(NormalBlockAdapter::new);
  }

  public Optional<Bytes32> getHash() {
    return Optional.of(header.getHash());
  }

  public Optional<Bytes> getNonce() {
    final long nonce = header.getNonce();
    final byte[] bytes = Longs.toByteArray(nonce);
    return Optional.of(Bytes.wrap(bytes));
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

  public Optional<AdapterBase> getMiner(final DataFetchingEnvironment environment) {

    final BlockchainQueries query = getBlockchainQueries(environment);
    long blockNumber = header.getNumber();
    final Long bn = environment.getArgument("block");
    if (bn != null) {
      blockNumber = bn;
    }

    return Optional.ofNullable(query.getWorldState(blockNumber).get().get(header.getCoinbase()))
        .map(account -> (AdapterBase) new AccountAdapter(account))
        .or(() -> Optional.of(new EmptyAccountAdapter(header.getCoinbase())));
  }

  public Optional<Bytes> getExtraData() {
    return Optional.of(header.getExtraData());
  }

  public Optional<Long> getGasLimit() {
    return Optional.of(header.getGasLimit());
  }

  public Optional<Long> getGasUsed() {
    return Optional.of(header.getGasUsed());
  }

  public Optional<UInt256> getTimestamp() {
    return Optional.of(UInt256.valueOf(header.getTimestamp()));
  }

  public Optional<Bytes> getLogsBloom() {
    return Optional.of(header.getLogsBloom());
  }

  public Optional<Bytes32> getMixHash() {
    return Optional.of(header.getMixHash());
  }

  public Optional<Difficulty> getDifficulty() {
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

    final BlockchainQueries query = getBlockchainQueries(environment);
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
      if (topic.isEmpty()) {
        transformedTopics.add(Collections.singletonList(null));
      } else {
        transformedTopics.add(topic.stream().map(LogTopic::of).collect(Collectors.toList()));
      }
    }
    final LogsQuery query =
        new LogsQuery.Builder().addresses(addrs).topics(transformedTopics).build();

    final BlockchainQueries blockchain = getBlockchainQueries(environment);

    final Hash hash = header.getHash();
    final List<LogWithMetadata> logs = blockchain.matchingLogs(hash, query, () -> true);
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
    final Bytes data = (Bytes) callData.get("data");

    final BlockchainQueries query = getBlockchainQueries(environment);
    final ProtocolSchedule protocolSchedule =
        environment.getGraphQlContext().get(GraphQLContextType.PROTOCOL_SCHEDULE);
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

    final Optional<TransactionSimulatorResult> opt =
        transactionSimulator.process(
            param,
            TransactionValidationParams.transactionSimulator(),
            OperationTracer.NO_TRACING,
            bn);
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
