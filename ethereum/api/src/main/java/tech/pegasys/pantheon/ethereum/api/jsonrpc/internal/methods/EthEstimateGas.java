/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results.Quantity;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.transaction.CallParameter;
import tech.pegasys.pantheon.ethereum.transaction.TransactionSimulator;
import tech.pegasys.pantheon.ethereum.transaction.TransactionSimulatorResult;

import java.util.function.Function;

public class EthEstimateGas implements JsonRpcMethod {

  private final BlockchainQueries blockchainQueries;
  private final TransactionSimulator transactionSimulator;
  private final JsonRpcParameter parameters;

  public EthEstimateGas(
      final BlockchainQueries blockchainQueries,
      final TransactionSimulator transactionSimulator,
      final JsonRpcParameter parameters) {
    this.blockchainQueries = blockchainQueries;
    this.transactionSimulator = transactionSimulator;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_ESTIMATE_GAS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final JsonCallParameter callParams =
        parameters.required(request.getParams(), 0, JsonCallParameter.class);

    final BlockHeader blockHeader = blockHeader();
    if (blockHeader == null) {
      return errorResponse(request);
    }

    final JsonCallParameter modifiedCallParams =
        overrideGasLimitAndPrice(callParams, blockHeader.getGasLimit());

    return transactionSimulator
        .process(modifiedCallParams, blockHeader.getNumber())
        .map(gasEstimateResponse(request))
        .orElse(errorResponse(request));
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
      final JsonRpcRequest request) {
    return result ->
        new JsonRpcSuccessResponse(request.getId(), Quantity.create(result.getGasEstimate()));
  }

  private JsonRpcErrorResponse errorResponse(final JsonRpcRequest request) {
    return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INTERNAL_ERROR);
  }
}
