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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.evm.tracing.EstimateGasOperationTracer;

import java.util.function.Function;

public class EthEstimateGas extends AbstractEstimateGas {

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
      return errorResponse(requestContext, JsonRpcError.INTERNAL_ERROR);
    }
    if (!blockchainQueries
        .getWorldStateArchive()
        .isWorldStateAvailable(blockHeader.getStateRoot(), blockHeader.getHash())) {
      return errorResponse(requestContext, JsonRpcError.WORLD_STATE_UNAVAILABLE);
    }

    final CallParameter modifiedCallParams =
        overrideGasLimitAndPrice(callParams, blockHeader.getGasLimit());

    final EstimateGasOperationTracer operationTracer = new EstimateGasOperationTracer();

    return transactionSimulator
        .process(
            modifiedCallParams,
            ImmutableTransactionValidationParams.builder()
                .from(TransactionValidationParams.transactionSimulator())
                .isAllowExceedingBalance(!callParams.isMaybeStrict().orElse(Boolean.FALSE))
                .build(),
            operationTracer,
            blockHeader.getNumber())
        .map(gasEstimateResponse(requestContext, operationTracer))
        .orElse(errorResponse(requestContext, JsonRpcError.INTERNAL_ERROR));
  }

  private Function<TransactionSimulatorResult, JsonRpcResponse> gasEstimateResponse(
      final JsonRpcRequestContext request, final EstimateGasOperationTracer operationTracer) {
    return result ->
        result.isSuccessful()
            ? new JsonRpcSuccessResponse(
                request.getRequest().getId(),
                Quantity.create(processEstimateGas(result, operationTracer)))
            : errorResponse(request, result);
  }
}
