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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.OptionalLong;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public abstract class AbstractBlockParameterMethod implements JsonRpcMethod {

  private final Supplier<BlockchainQueries> blockchainQueries;
  private final JsonRpcParameter parameters;

  protected AbstractBlockParameterMethod(
      final BlockchainQueries blockchainQueries, final JsonRpcParameter parameters) {
    this(Suppliers.ofInstance(blockchainQueries), parameters);
  }

  protected AbstractBlockParameterMethod(
      final Supplier<BlockchainQueries> blockchainQueries, final JsonRpcParameter parameters) {
    this.blockchainQueries = blockchainQueries;
    this.parameters = parameters;
  }

  protected abstract BlockParameter blockParameter(JsonRpcRequest request);

  protected abstract Object resultByBlockNumber(JsonRpcRequest request, long blockNumber);

  protected BlockchainQueries getBlockchainQueries() {
    return blockchainQueries.get();
  }

  protected JsonRpcParameter getParameters() {
    return parameters;
  }

  protected Object pendingResult(final JsonRpcRequest request) {
    // TODO: Update once we mine and better understand pending semantics.
    // For now act like we are not mining and just return latest.
    return latestResult(request);
  }

  protected Object latestResult(final JsonRpcRequest request) {
    return resultByBlockNumber(request, blockchainQueries.get().headBlockNumber());
  }

  protected Object findResultByParamType(final JsonRpcRequest request) {
    final BlockParameter blockParam = blockParameter(request);

    final Object result;
    final OptionalLong blockNumber = blockParam.getNumber();
    if (blockNumber.isPresent()) {
      result = resultByBlockNumber(request, blockNumber.getAsLong());
    } else if (blockParam.isLatest()) {
      result = latestResult(request);
    } else {
      // If block parameter is not numeric or latest, it is pending.
      result = pendingResult(request);
    }

    return result;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    return new JsonRpcSuccessResponse(request.getId(), findResultByParamType(request));
  }
}
