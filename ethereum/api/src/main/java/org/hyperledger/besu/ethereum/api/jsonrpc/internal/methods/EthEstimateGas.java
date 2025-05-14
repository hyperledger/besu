/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EthEstimateGas extends AbstractEstimateGas {
  private static final Logger LOG = LoggerFactory.getLogger(EthEstimateGas.class);
  private static final double SUB_CALL_REMAINING_GAS_RATIO = 64D / 63D;
  // zero tolerance means there is no tolerance,
  // which means keep looping until the estimate is exact (previous behavior)
  protected double estimateGasToleranceRatio;
  private static final long CALL_STIPEND = 2_300L;

  public EthEstimateGas(
      final BlockchainQueries blockchainQueries,
      final TransactionSimulator transactionSimulator,
      final ApiConfiguration apiConfiguration) {
    super(blockchainQueries, transactionSimulator);
    this.estimateGasToleranceRatio = apiConfiguration.getEstimateGasToleranceRatio();
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_ESTIMATE_GAS.getMethodName();
  }

  @Override
  protected Object simulate(
      final JsonRpcRequestContext requestContext,
      final CallParameter callParams,
      final ProcessableBlockHeader blockHeader,
      final TransactionSimulationFunction simulationFunction,
      final long gasLimitUpperBound) {

    // Optimistic simulation - get min transaction cost from GasCalculator
    final long minTxCost = getBlockchainQueries().getMinimumTransactionCost(blockHeader);
    if (attemptOptimisticSimulationWithMinimumBlockGasUsed(
        callParams, simulationFunction, OperationTracer.NO_TRACING, minTxCost)) {
      LOG.atDebug()
          .setMessage("[{}] Optimistic simulation with min tx gas cost {} successful")
          .addArgument(LOG_ID::get)
          .addArgument(minTxCost)
          .log();
      return Quantity.create(minTxCost);
    }

    final var maybeResult =
        simulationFunction.simulate(
            overrideGasLimit(callParams, gasLimitUpperBound), OperationTracer.NO_TRACING);

    final Optional<JsonRpcErrorResponse> maybeErrorResponse =
        validateSimulationResult(requestContext, maybeResult);
    if (maybeErrorResponse.isPresent()) {
      LOG.atDebug()
          .setMessage(
              "[{}] Simulation with gas limit upper bound {} failed with error response: {}")
          .addArgument(LOG_ID::get)
          .addArgument(gasLimitUpperBound)
          .addArgument(maybeErrorResponse.get()::toString)
          .log();
      return maybeErrorResponse.get();
    }

    final var result = maybeResult.get();
    long high = gasLimitUpperBound;
    long mid;

    long low = result.result().getEstimateGasUsedByTransaction() - 1;
    var optimisticGasLimit = processEstimateGas(result);

    final var optimisticResult =
        simulationFunction.simulate(
            overrideGasLimit(callParams, optimisticGasLimit), OperationTracer.NO_TRACING);

    if (optimisticResult.isPresent() && optimisticResult.get().isSuccessful()) {
      high = optimisticGasLimit;
    } else {
      low = optimisticGasLimit;
    }

    int iteration = 1;

    while (low + 1 < high) {
      // check if we are close enough
      if (estimateGasToleranceRatio > 0) {
        double currentRatio = (double) (high - low) / high;
        if (currentRatio < estimateGasToleranceRatio) {
          LOG.atTrace()
              .setMessage("[{}]-[{}] Estimation within tolerance of {}, high {}, low {}, ratio {}")
              .addArgument(LOG_ID::get)
              .addArgument(iteration)
              .addArgument(estimateGasToleranceRatio)
              .addArgument(high)
              .addArgument(low)
              .addArgument(currentRatio)
              .log();
          break;
        }
      }
      mid = (low + high) / 2;
      var binarySearchResult =
          simulationFunction.simulate(
              overrideGasLimit(callParams, mid), OperationTracer.NO_TRACING);

      LOG.atTrace()
          .setMessage("[{}]-[{}] Simulation with gas limit {}, high {}, low {}, result {}")
          .addArgument(LOG_ID::get)
          .addArgument(iteration)
          .addArgument(mid)
          .addArgument(high)
          .addArgument(low)
          .addArgument(binarySearchResult)
          .log();

      if (binarySearchResult.isEmpty() || !binarySearchResult.get().isSuccessful()) {
        low = mid;
      } else {
        high = mid;
      }
      ++iteration;
    }

    LOG.atDebug().setMessage("[{}] Returning {}").addArgument(LOG_ID::get).addArgument(high).log();
    return Quantity.create(high);
  }

  private Optional<JsonRpcErrorResponse> validateSimulationResult(
      final JsonRpcRequestContext requestContext,
      final Optional<TransactionSimulatorResult> maybeResult) {
    if (maybeResult.isEmpty()) {
      LOG.error("[{}] No result after simulating transaction.", LOG_ID.get());
      return Optional.of(
          new JsonRpcErrorResponse(
              requestContext.getRequest().getId(), RpcErrorType.INTERNAL_ERROR));
    }

    // if the transaction is invalid or doesn't have enough gas with the max it never will!
    if (maybeResult.get().isInvalid() || !maybeResult.get().isSuccessful()) {
      return Optional.of(errorResponse(requestContext, maybeResult.get()));
    }
    return Optional.empty();
  }

  /**
   * Estimate gas by adding call stipend and compute the necessary gas for sub calls
   *
   * @param result transaction simulator result
   * @return estimate gas
   */
  protected long processEstimateGas(final TransactionSimulatorResult result) {
    final long gasUsedByTransaction = result.result().getEstimateGasUsedByTransaction();

    // no more than 64/63 of the remaining gas can be passed to the sub calls
    return ((long) ((gasUsedByTransaction + CALL_STIPEND) * SUB_CALL_REMAINING_GAS_RATIO));
  }
}
