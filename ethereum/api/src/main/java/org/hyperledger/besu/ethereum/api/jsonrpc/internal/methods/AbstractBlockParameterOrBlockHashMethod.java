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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;

import java.util.Optional;
import java.util.OptionalLong;
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

  protected abstract Object resultByBlockHash(JsonRpcRequestContext request, Hash blockHash);

  protected BlockchainQueries getBlockchainQueries() {
    return blockchainQueries.get();
  }

  protected Object pendingResult(final JsonRpcRequestContext request) {
    // TODO: Update once we mine and better understand pending semantics.
    // For now act like we are not mining and just return latest.
    return latestResult(request);
  }

  protected Object latestResult(final JsonRpcRequestContext request) {
    return resultByBlockHash(
        request, getBlockchainQueries().getBlockchain().getChainHead().getHash());
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
      final OptionalLong blockNumber = blockParameterOrBlockHash.getNumber();
      if (blockNumber.isEmpty() || blockNumber.getAsLong() < 0) {
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
      } else if (blockNumber.getAsLong() > getBlockchainQueries().headBlockNumber()) {
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), JsonRpcError.BLOCK_NOT_FOUND);
      }

      result =
          resultByBlockHash(
              requestContext,
              blockchainQueries
                  .get()
                  .getBlockHashByNumber(blockNumber.getAsLong())
                  .orElse(Hash.EMPTY));
    } else {
      Optional<Hash> blockHash = blockParameterOrBlockHash.getHash();
      if (blockHash.isEmpty()) {
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
      }

      if (Boolean.TRUE.equals(blockParameterOrBlockHash.getRequireCanonical())
          && !getBlockchainQueries().blockIsOnCanonicalChain(blockHash.get())) {
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), JsonRpcError.JSON_RPC_NOT_CANONICAL_ERROR);
      }

      result = resultByBlockHash(requestContext, blockHash.get());
    }

    return result;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    Object response = handleParamTypes(requestContext);

    if (response instanceof JsonRpcErrorResponse) {
      return (JsonRpcResponse) response;
    }

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), response);
  }
}
