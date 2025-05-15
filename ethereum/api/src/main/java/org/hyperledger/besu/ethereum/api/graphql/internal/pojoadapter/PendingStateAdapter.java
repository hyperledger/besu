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
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLContextType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.ImmutableCallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * This class represents the pending state of transactions in the Ethereum network.
 *
 * <p>It extends the AdapterBase class and provides methods to interact with the transaction pool.
 */
@SuppressWarnings("unused") // reflected by GraphQL
public class PendingStateAdapter extends AdapterBase {

  private final TransactionPool transactionPool;

  /**
   * Constructor for PendingStateAdapter.
   *
   * <p>It initializes the transactionPool field with the provided argument.
   *
   * @param transactionPool the transaction pool to be used.
   */
  public PendingStateAdapter(final TransactionPool transactionPool) {
    this.transactionPool = transactionPool;
  }

  /**
   * Returns the count of transactions in the transaction pool.
   *
   * @return the count of transactions in the transaction pool.
   */
  public Integer getTransactionCount() {
    return transactionPool.count();
  }

  /**
   * Returns a list of TransactionAdapter objects for the transactions in the transaction pool.
   *
   * @return a list of TransactionAdapter objects for the transactions in the transaction pool.
   */
  public List<TransactionAdapter> getTransactions() {
    return transactionPool.getPendingTransactions().stream()
        .map(PendingTransaction::getTransaction)
        .map(TransactionWithMetadata::new)
        .map(tx -> new TransactionAdapter(tx, null))
        .toList();
  }

  /**
   * Returns an AccountAdapter object for the account associated with the provided address.
   *
   * <p>The account state is estimated against the latest block.
   *
   * @param dataFetchingEnvironment the data fetching environment.
   * @return an AccountAdapter object for the account associated with the provided address.
   */
  public AccountAdapter getAccount(final DataFetchingEnvironment dataFetchingEnvironment) {
    // until the miner can expose the current "proposed block" we have no
    // speculative environment, so estimate against latest.
    final BlockchainQueries blockchainQuery =
        dataFetchingEnvironment.getGraphQlContext().get(GraphQLContextType.BLOCKCHAIN_QUERIES);
    final Address addr = dataFetchingEnvironment.getArgument("address");
    final Long blockNumber = dataFetchingEnvironment.getArgument("blockNumber");
    final long latestBlockNumber = blockchainQuery.latestBlock().get().getHeader().getNumber();
    return blockchainQuery
        .getAndMapWorldState(latestBlockNumber, ws -> Optional.ofNullable(ws.get(addr)))
        .map(AccountAdapter::new)
        .orElseGet(() -> new AccountAdapter(null));
  }

  /**
   * Estimates the gas required for a transaction.
   *
   * <p>This method calls the getCall method to simulate the transaction and then retrieves the gas
   * used by the transaction. The gas estimation is done against the latest block as there is
   * currently no way to expose the current "proposed block" for a speculative environment.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing the estimated gas for the transaction, or an empty Optional if
   *     the transaction simulation was not successful.
   */
  public Optional<Long> getEstimateGas(final DataFetchingEnvironment environment) {
    // until the miner can expose the current "proposed block" we have no
    // speculative environment, so estimate against latest.
    final Optional<CallResult> result = getCall(environment);
    return result.map(CallResult::getGasUsed);
  }

  /**
   * Simulates the execution of a transaction.
   *
   * <p>This method retrieves the transaction parameters from the data fetching environment, creates
   * a CallParameter object, and then uses a TransactionSimulator to simulate the execution of the
   * transaction. The simulation is done against the latest block as there is currently no way to
   * expose the current "proposed block" for a speculative environment.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing the result of the transaction simulation, or an empty Optional
   *     if the transaction simulation was not successful.
   */
  public Optional<CallResult> getCall(final DataFetchingEnvironment environment) {
    // until the miner can expose the current "proposed block" we have no
    // speculative environment, so estimate against latest.
    final Map<String, Object> callData = environment.getArgument("data");
    final Optional<Address> from = Optional.ofNullable((Address) callData.get("from"));
    final Optional<Address> to = Optional.ofNullable((Address) callData.get("to"));
    final var gasParam = callData.get("gas");
    final OptionalLong gas =
        gasParam != null ? OptionalLong.of((Long) gasParam) : OptionalLong.empty();
    final Optional<Wei> gasPrice =
        Optional.ofNullable((UInt256) callData.get("gasPrice")).map(Wei::of);
    final Optional<Wei> value = Optional.ofNullable((UInt256) callData.get("value")).map(Wei::of);
    final Optional<Bytes> data = Optional.ofNullable((Bytes) callData.get("data"));
    final Optional<Wei> maxFeePerGas =
        Optional.ofNullable((UInt256) callData.get("maxFeePerGas")).map(Wei::of);
    final Optional<Wei> maxPriorityFeePerGas =
        Optional.ofNullable((UInt256) callData.get("maxPriorityFeePerGas")).map(Wei::of);

    final BlockchainQueries query = getBlockchainQueries(environment);
    final ProtocolSchedule protocolSchedule =
        environment.getGraphQlContext().get(GraphQLContextType.PROTOCOL_SCHEDULE);
    final TransactionSimulator transactionSimulator =
        environment.getGraphQlContext().get(GraphQLContextType.TRANSACTION_SIMULATOR);

    final CallParameter callParameters =
        ImmutableCallParameter.builder()
            .sender(from)
            .to(to)
            .gas(gas)
            .gasPrice(gasPrice)
            .value(value)
            .maxPriorityFeePerGas(maxPriorityFeePerGas)
            .maxFeePerGas(maxFeePerGas)
            .input(data)
            .build();

    return transactionSimulator.process(
        callParameters,
        TransactionValidationParams.transactionSimulatorAllowExceedingBalanceAndFutureNonce(),
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
        query.getBlockchain().getChainHeadHeader());
  }
}
