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
import org.hyperledger.besu.datatypes.BlobGas;
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
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

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

  public Bytes32 getHash() {
    return header.getHash();
  }

  public Bytes getNonce() {
    final long nonce = header.getNonce();
    final byte[] bytes = Longs.toByteArray(nonce);
    return Bytes.wrap(bytes);
  }

  public Bytes32 getTransactionsRoot() {
    return header.getTransactionsRoot();
  }

  public Bytes32 getStateRoot() {
    return header.getStateRoot();
  }

  public Bytes32 getReceiptsRoot() {
    return header.getReceiptsRoot();
  }

  public AccountAdapter getMiner(final DataFetchingEnvironment environment) {

    final BlockchainQueries query = getBlockchainQueries(environment);
    long blockNumber = header.getNumber();
    final Long bn = environment.getArgument("block");
    if (bn != null) {
      blockNumber = bn;
    }

    return query
        .getAndMapWorldState(blockNumber, ws -> Optional.ofNullable(ws.get(header.getCoinbase())))
        .map(AccountAdapter::new)
        .orElseGet(() -> new EmptyAccountAdapter(header.getCoinbase()));
  }

  public Bytes getExtraData() {
    return header.getExtraData();
  }

  public Optional<Wei> getBaseFeePerGas() {
    return header.getBaseFee();
  }

  public Long getGasLimit() {
    return header.getGasLimit();
  }

  public Long getGasUsed() {
    return header.getGasUsed();
  }

  public Long getTimestamp() {
    return header.getTimestamp();
  }

  public Bytes getLogsBloom() {
    return header.getLogsBloom();
  }

  public Bytes32 getMixHash() {
    return header.getMixHash();
  }

  public Difficulty getDifficulty() {
    return header.getDifficulty();
  }

  public Bytes32 getOmmerHash() {
    return header.getOmmersHash();
  }

  public Long getNumber() {
    return header.getNumber();
  }

  public AccountAdapter getAccount(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    final long bn = header.getNumber();
    final Address address = environment.getArgument("address");
    return query
        .getAndMapWorldState(
            bn, ws -> Optional.of(new AccountAdapter(ws.get(address), Optional.of(bn))))
        .get();
  }

  public List<LogAdapter> getLogs(final DataFetchingEnvironment environment) {

    final Map<String, Object> filter = environment.getArgument("filter");

    @SuppressWarnings("unchecked")
    final List<Address> addresses = (List<Address>) filter.get("addresses");
    @SuppressWarnings("unchecked")
    final List<List<Bytes32>> topics = (List<List<Bytes32>>) filter.get("topics");

    final List<List<LogTopic>> transformedTopics = new ArrayList<>();
    for (final List<Bytes32> topic : topics) {
      if (topic.isEmpty()) {
        transformedTopics.add(Collections.singletonList(null));
      } else {
        transformedTopics.add(topic.stream().map(LogTopic::of).toList());
      }
    }
    final LogsQuery query =
        new LogsQuery.Builder().addresses(addresses).topics(transformedTopics).build();

    final BlockchainQueries blockchain = getBlockchainQueries(environment);

    final Hash hash = header.getHash();
    final List<LogWithMetadata> logs = blockchain.matchingLogs(hash, query, () -> true);
    final List<LogAdapter> results = new ArrayList<>();
    for (final LogWithMetadata log : logs) {
      results.add(new LogAdapter(log));
    }
    return results;
  }

  public Long getEstimateGas(final DataFetchingEnvironment environment) {
    final Optional<CallResult> result = executeCall(environment);
    return result.map(CallResult::getGasUsed).orElse(0L);
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
    final Optional<Wei> maxFeePerGas =
        Optional.ofNullable((UInt256) callData.get("maxFeePerGas")).map(Wei::of);
    final Optional<Wei> maxPriorityFeePerGas =
        Optional.ofNullable((UInt256) callData.get("maxPriorityFeePerGas")).map(Wei::of);

    final BlockchainQueries query = getBlockchainQueries(environment);
    final ProtocolSchedule protocolSchedule =
        environment.getGraphQlContext().get(GraphQLContextType.PROTOCOL_SCHEDULE);
    final long bn = header.getNumber();
    final long gasCap = environment.getGraphQlContext().get(GraphQLContextType.GAS_CAP);
    final TransactionSimulator transactionSimulator =
        new TransactionSimulator(
            query.getBlockchain(), query.getWorldStateArchive(), protocolSchedule, gasCap);

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
        new CallParameter(
            from,
            to,
            gasParam,
            gasPriceParam,
            maxPriorityFeePerGas,
            maxFeePerGas,
            valueParam,
            data,
            Optional.empty());

    ImmutableTransactionValidationParams.Builder transactionValidationParams =
        ImmutableTransactionValidationParams.builder()
            .from(TransactionValidationParams.transactionSimulator());
    transactionValidationParams.isAllowExceedingBalance(true);

    return transactionSimulator.process(
        param,
        transactionValidationParams.build(),
        OperationTracer.NO_TRACING,
        (mutableWorldState, transactionSimulatorResult) ->
            transactionSimulatorResult.map(
                result -> {
                  long status = 0;
                  if (result.isSuccessful()) {
                    status = 1;
                  }
                  return new CallResult(status, result.getGasEstimate(), result.getOutput());
                }),
        header);
  }

  Bytes getRawHeader() {
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    header.writeTo(rlpOutput);
    return rlpOutput.encoded();
  }

  Bytes getRaw(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    return query
        .getBlockchain()
        .getBlockBody(header.getBlockHash())
        .map(
            blockBody -> {
              final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
              blockBody.writeWrappedBodyTo(rlpOutput);
              return rlpOutput.encoded();
            })
        .orElse(Bytes.EMPTY);
  }

  Optional<Bytes32> getWithdrawalsRoot() {
    return header.getWithdrawalsRoot().map(Function.identity());
  }

  Optional<List<WithdrawalAdapter>> getWithdrawals(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    return query
        .getBlockchain()
        .getBlockBody(header.getBlockHash())
        .flatMap(
            blockBody ->
                blockBody
                    .getWithdrawals()
                    .map(wl -> wl.stream().map(WithdrawalAdapter::new).toList()));
  }

  public Optional<Long> getBlobGasUsed() {
    return header.getBlobGasUsed();
  }

  public Optional<Long> getExcessBlobGas() {
    return header.getExcessBlobGas().map(BlobGas::toLong);
  }
}
