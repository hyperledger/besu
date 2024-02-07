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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.evm.tracing.EstimateGasOperationTracer;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EthEstimateGas extends AbstractEstimateGas {

  private static final Logger LOG = LoggerFactory.getLogger(EthEstimateGas.class);

  public EthEstimateGas(
      final BlockchainQueries blockchainQueries, final TransactionSimulator transactionSimulator) {
    super(blockchainQueries, transactionSimulator);
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_ESTIMATE_GAS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final JsonCallParameter callParams = validateAndGetCallParams(requestContext);

    final BlockHeader blockHeader = blockHeader();
    if (blockHeader == null) {
      LOG.error("Chain head block not found");
      return errorResponse(requestContext, RpcErrorType.INTERNAL_ERROR);
    }
    if (!blockchainQueries
        .getWorldStateArchive()
        .isWorldStateAvailable(blockHeader.getStateRoot(), blockHeader.getHash())) {
      return errorResponse(requestContext, RpcErrorType.WORLD_STATE_UNAVAILABLE);
    }

    final CallParameter modifiedCallParams =
        overrideGasLimitAndPrice(callParams, blockHeader.getGasLimit());

    final boolean isAllowExceedingBalance = !callParams.isMaybeStrict().orElse(Boolean.FALSE);

    final EstimateGasOperationTracer operationTracer = new EstimateGasOperationTracer();

    var gasUsed =
        executeSimulation(
            blockHeader, modifiedCallParams, operationTracer, isAllowExceedingBalance);

    if (gasUsed.isEmpty()) {
      LOG.error("gasUsed is empty after simulating transaction.");
      return errorResponse(requestContext, RpcErrorType.INTERNAL_ERROR);
    }

    // if the transaction is invalid or doesn't have enough gas with the max it never will!
    if (gasUsed.get().isInvalid() || !gasUsed.get().isSuccessful()) {
      return errorResponse(requestContext, gasUsed.get());
    }

    var low = gasUsed.get().result().getEstimateGasUsedByTransaction();
    var lowResult =
        executeSimulation(
            blockHeader,
            overrideGasLimitAndPrice(callParams, low),
            operationTracer,
            isAllowExceedingBalance);

    if (lowResult.isPresent() && lowResult.get().isSuccessful()) {
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), Quantity.create(low));
    }

    var high = processEstimateGas(gasUsed.get(), operationTracer);
    var mid = high;
    while (low + 1 < high) {
      mid = (high + low) / 2;

      var binarySearchResult =
          executeSimulation(
              blockHeader,
              overrideGasLimitAndPrice(callParams, mid),
              operationTracer,
              isAllowExceedingBalance);
      if (binarySearchResult.isEmpty() || !binarySearchResult.get().isSuccessful()) {
        low = mid;
      } else {
        high = mid;
      }
    }

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), Quantity.create(high));
  }

  private Optional<TransactionSimulatorResult> executeSimulation(
      final BlockHeader blockHeader,
      final CallParameter modifiedCallParams,
      final EstimateGasOperationTracer operationTracer,
      final boolean allowExceedingBalance) {
    return transactionSimulator.process(
        modifiedCallParams,
        ImmutableTransactionValidationParams.builder()
            .from(TransactionValidationParams.transactionSimulator())
            .isAllowExceedingBalance(allowExceedingBalance)
            .build(),
        operationTracer,
        blockHeader.getNumber());
  }
}
