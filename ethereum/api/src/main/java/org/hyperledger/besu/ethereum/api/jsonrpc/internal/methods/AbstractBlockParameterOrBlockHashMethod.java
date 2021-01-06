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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public abstract class AbstractBlockParameterOrBlockHashMethod implements JsonRpcMethod {
  protected final Supplier<BlockchainQueries> blockchainQueries;

  protected AbstractBlockParameterOrBlockHashMethod(final BlockchainQueries blockchainQueries) {
    this(Suppliers.ofInstance(blockchainQueries));
  }

  protected AbstractBlockParameterOrBlockHashMethod(
      final Supplier<BlockchainQueries> blockchainQueries) {
    this.blockchainQueries = blockchainQueries;
  }

  protected abstract BlockParameterOrBlockHash blockParameterOrBlockHash(
      JsonRpcRequestContext request);

  protected abstract Object resultByBlockNumber(JsonRpcRequestContext request, long blockNumber);

  protected BlockchainQueries getBlockchainQueries() {
    return blockchainQueries.get();
  }

  protected Object pendingResult(final JsonRpcRequestContext request) {
    // TODO: Update once we mine and better understand pending semantics.
    // For now act like we are not mining and just return latest.
    return latestResult(request);
  }

  protected Object latestResult(final JsonRpcRequestContext request) {
    return resultByBlockNumber(request, blockchainQueries.get().headBlockNumber());
  }

  protected Object handleParamTypes(final JsonRpcRequestContext requestContext) {
    final BlockParameterOrBlockHash blockParameterOrBlockHash =
        blockParameterOrBlockHash(requestContext);

    final Object result;
    if (blockParameterOrBlockHash.isLatest()) {
      result = latestResult(requestContext);
    } else if (blockParameterOrBlockHash.isPending()) {
      result = pendingResult(requestContext);
    } else if (blockParameterOrBlockHash.isNumeric() || blockParameterOrBlockHash.isEarliest()) {
      result =
          resultByBlockNumber(
              requestContext,
              blockParameterOrBlockHash
                  .getNumber()
                  .orElseThrow(() -> new IllegalStateException("Invalid Block Number Parameter.")));
    } else {
      result =
          resultByBlockNumber(
              requestContext,
              blockchainQueries
                  .get()
                  .blockByHash(
                      blockParameterOrBlockHash
                          .getHash()
                          .orElseThrow(
                              () -> new IllegalStateException("Invalid Block Number Parameter.")))
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Block not found, " + blockParameterOrBlockHash.getHash().get()))
                  .getHeader()
                  .getNumber());
    }

    return result;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), handleParamTypes(requestContext));
  }
}
