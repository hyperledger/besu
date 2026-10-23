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
package org.hyperledger.besu.plugins.graphql.internal.pojoadapter;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.TransactionSimulationService.SimulationParameters;
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService;
import org.hyperledger.besu.plugins.graphql.internal.query.ChainQueries;
import org.hyperledger.besu.plugins.graphql.internal.query.TransactionWithMetadata;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;

/**
 * This class represents the pending state of transactions in the Ethereum network.
 *
 * <p>It extends the AdapterBase class and provides methods to interact with the transaction pool.
 */
@SuppressWarnings("unused") // reflected by GraphQL
public class PendingStateAdapter extends AdapterBase {

  private final TransactionPoolService transactionPool;

  /**
   * Constructor for PendingStateAdapter.
   *
   * <p>It initializes the transactionPool field with the provided argument.
   *
   * @param transactionPool the transaction pool service to be used.
   */
  public PendingStateAdapter(final TransactionPoolService transactionPool) {
    this.transactionPool = transactionPool;
  }

  /**
   * Returns the count of transactions in the transaction pool.
   *
   * @return the count of transactions in the transaction pool.
   */
  public Integer getTransactionCount() {
    return transactionPool.getPendingTransactionCount();
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
    final ChainQueries blockchainQuery = getBlockchainQueries(dataFetchingEnvironment);
    final Address addr = dataFetchingEnvironment.getArgument("address");
    final Long blockNumber = dataFetchingEnvironment.getArgument("blockNumber");
    final long latestBlockNumber = blockchainQuery.latestBlock().get().getHeader().getNumber();
    return blockchainQuery
        .getAndMapWorldStateWithHash(
            latestBlockNumber,
            (ws, stateBlockHash) ->
                Optional.ofNullable(ws.get(addr))
                    .map(account -> new AccountAdapter(account, stateBlockHash)))
        .orElseGet(() -> new AccountAdapter((Account) null, null));
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
   * <p>This method retrieves the transaction parameters from the data fetching environment and then
   * simulates the execution of the transaction. The simulation is done against the latest block as
   * there is currently no way to expose the current "proposed block" for a speculative environment.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing the result of the transaction simulation, or an empty Optional
   *     if the transaction simulation was not successful.
   */
  public Optional<CallResult> getCall(final DataFetchingEnvironment environment) {
    // until the miner can expose the current "proposed block" we have no
    // speculative environment, so estimate against latest.
    final BlockchainService blockchainService =
        getBlockchainQueries(environment).getBlockchainService();
    return CallExecutor.executeCall(
        environment,
        blockchainService::getChainHeadHash,
        EnumSet.of(
            SimulationParameters.ALLOW_EXCEEDING_BALANCE, SimulationParameters.ALLOW_FUTURE_NONCE));
  }
}
