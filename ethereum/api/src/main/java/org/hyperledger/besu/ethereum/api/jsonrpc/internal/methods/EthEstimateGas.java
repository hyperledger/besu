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

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;

import java.util.function.Function;

public class EthEstimateGas implements JsonRpcMethod {

  private final BlockchainQueries blockchainQueries;
  private final TransactionSimulator transactionSimulator;

  public EthEstimateGas(
      final BlockchainQueries blockchainQueries, final TransactionSimulator transactionSimulator) {
    this.blockchainQueries = blockchainQueries;
    this.transactionSimulator = transactionSimulator;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_ESTIMATE_GAS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final JsonCallParameter callParams =
        requestContext.getRequiredParameter(0, JsonCallParameter.class);

    final BlockHeader blockHeader = blockHeader();
    if (blockHeader == null) {
      return errorResponse(requestContext, JsonRpcError.INTERNAL_ERROR);
    }

    final JsonCallParameter modifiedCallParams =
        overrideGasLimitAndPrice(callParams, blockHeader.getGasLimit());

    return transactionSimulator
        .process(modifiedCallParams, blockHeader.getNumber())
        .map(gasEstimateResponse(requestContext))
        .orElse(errorResponse(requestContext, JsonRpcError.INTERNAL_ERROR));
  }

  private BlockHeader blockHeader() {
    final long headBlockNumber = blockchainQueries.headBlockNumber();
    return blockchainQueries.getBlockchain().getBlockHeader(headBlockNumber).orElse(null);
  }

  private JsonCallParameter overrideGasLimitAndPrice(
      final CallParameter callParams, final long gasLimit) {
    return new JsonCallParameter(
        callParams.getFrom() != null ? callParams.getFrom().toString() : null,
        callParams.getTo() != null ? callParams.getTo().toString() : null,
        Quantity.create(gasLimit),
        Quantity.create(0L),
        callParams.getValue() != null ? Quantity.create(callParams.getValue()) : null,
        callParams.getPayload() != null ? callParams.getPayload().toString() : null);
  }

  private Function<TransactionSimulatorResult, JsonRpcResponse> gasEstimateResponse(
      final JsonRpcRequestContext request) {
    return result ->
        result.isSuccessful()
            ? new JsonRpcSuccessResponse(
                request.getRequest().getId(), Quantity.create(result.getGasEstimate()))
            : errorResponse(request, result.getValidationResult());
  }

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request,
      final ValidationResult<TransactionValidator.TransactionInvalidReason> validationResult) {
    JsonRpcError jsonRpcError = null;
    if (validationResult != null) {
      jsonRpcError =
          JsonRpcErrorConverter.convertTransactionInvalidReason(
              validationResult.getInvalidReason());
    }
    return errorResponse(request, jsonRpcError);
  }

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final JsonRpcError jsonRpcError) {
    return new JsonRpcErrorResponse(
        request.getRequest().getId(),
        jsonRpcError == null ? JsonRpcError.INTERNAL_ERROR : jsonRpcError);
  }
}
