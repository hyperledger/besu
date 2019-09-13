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

import tech.pegasys.pantheon.ethereum.api.TransactionWithMetadata;
import tech.pegasys.pantheon.ethereum.api.graphql.GraphQLDataFetcherContext;
import tech.pegasys.pantheon.ethereum.api.graphql.internal.BlockchainQuery;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.transaction.CallParameter;
import tech.pegasys.pantheon.ethereum.transaction.TransactionSimulator;
import tech.pegasys.pantheon.ethereum.transaction.TransactionSimulatorResult;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import graphql.schema.DataFetchingEnvironment;

@SuppressWarnings("unused") // reflected by GraphQL
public class PendingStateAdapter extends AdapterBase {

  private final PendingTransactions pendingTransactions;

  public PendingStateAdapter(final PendingTransactions pendingTransactions) {
    this.pendingTransactions = pendingTransactions;
  }

  public Integer getTransactionCount() {
    return pendingTransactions.size();
  }

  public List<TransactionAdapter> getTransactions() {
    return pendingTransactions.getTransactionInfo().stream()
        .map(PendingTransactions.TransactionInfo::getTransaction)
        .map(TransactionWithMetadata::new)
        .map(TransactionAdapter::new)
        .collect(Collectors.toList());
  }

  // until the miner can expose the current "proposed block" we have no
  // speculative environment, so estimate against latest.
  public Optional<AccountAdapter> getAccount(
      final DataFetchingEnvironment dataFetchingEnvironment) {
    final BlockchainQuery blockchainQuery =
        ((GraphQLDataFetcherContext) dataFetchingEnvironment.getContext()).getBlockchainQuery();
    final Address addr = dataFetchingEnvironment.getArgument("address");
    final Long blockNumber = dataFetchingEnvironment.getArgument("blockNumber");
    final long latestBlockNumber = blockchainQuery.latestBlock().get().getHeader().getNumber();
    final Optional<WorldState> optionalWorldState =
        blockchainQuery.getWorldState(latestBlockNumber);
    return optionalWorldState
        .flatMap(worldState -> Optional.ofNullable(worldState.get(addr)))
        .map(AccountAdapter::new);
  }

  // until the miner can expose the current "proposed block" we have no
  // speculative environment, so estimate against latest.
  public Optional<Long> getEstimateGas(final DataFetchingEnvironment environment) {
    final Optional<CallResult> result = getCall(environment);
    return result.map(CallResult::getGasUsed);
  }

  // until the miner can expose the current "proposed block" we have no
  // speculative environment, so estimate against latest.
  public Optional<CallResult> getCall(final DataFetchingEnvironment environment) {
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

    final Optional<TransactionSimulatorResult> opt = transactionSimulator.processAtHead(param);
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
