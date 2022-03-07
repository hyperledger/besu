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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;

import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public abstract class AbstractBlockParameterMethod implements JsonRpcMethod {

  protected final Supplier<BlockchainQueries> blockchainQueriesSupplier;

  protected AbstractBlockParameterMethod(final BlockchainQueries blockchainQueries) {
    this(Suppliers.ofInstance(blockchainQueries));
  }

  protected AbstractBlockParameterMethod(
      final Supplier<BlockchainQueries> blockchainQueriesSupplier) {
    this.blockchainQueriesSupplier = blockchainQueriesSupplier;
  }

  protected abstract BlockParameter blockParameter(JsonRpcRequestContext request);

  protected abstract Object resultByBlockNumber(JsonRpcRequestContext request, long blockNumber);

  protected BlockchainQueries getBlockchainQueries() {
    return blockchainQueriesSupplier.get();
  }

  protected Object pendingResult(final JsonRpcRequestContext request) {
    // TODO: Update once we mine and better understand pending semantics.
    // For now act like we are not mining and just return latest.
    return latestResult(request);
  }

  protected Object latestResult(final JsonRpcRequestContext request) {
    return resultByBlockNumber(request, blockchainQueriesSupplier.get().headBlockNumber());
  }

  protected Object findResultByParamType(final JsonRpcRequestContext request) {
    final BlockParameter blockParam = blockParameter(request);

    final Object result;
    final Optional<Long> blockNumber = blockParam.getNumber();
    if (blockNumber.isPresent()) {
      result = resultByBlockNumber(request, blockNumber.get());
    } else if (blockParam.isLatest()) {
      result = latestResult(request);
    } else {
      // If block parameter is not numeric or latest, it is pending.
      result = pendingResult(request);
    }

    return result;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    Object response = findResultByParamType(requestContext);

    if (response instanceof JsonRpcErrorResponse) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), ((JsonRpcErrorResponse) response).getError());
    }

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), response);
  }
}
