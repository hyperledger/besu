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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

/** The type Abstract block parameter method. */
public abstract class AbstractBlockParameterMethod implements JsonRpcMethod {

  /** The Blockchain queries supplier. */
  protected final Supplier<BlockchainQueries> blockchainQueriesSupplier;

  /**
   * Instantiates a new Abstract block parameter method.
   *
   * @param blockchainQueries the blockchain queries
   */
  protected AbstractBlockParameterMethod(final BlockchainQueries blockchainQueries) {
    this(Suppliers.ofInstance(blockchainQueries));
  }

  /**
   * Instantiates a new Abstract block parameter method.
   *
   * @param blockchainQueriesSupplier the blockchain queries supplier
   */
  protected AbstractBlockParameterMethod(
      final Supplier<BlockchainQueries> blockchainQueriesSupplier) {
    this.blockchainQueriesSupplier = blockchainQueriesSupplier;
  }

  /**
   * Block parameter block parameter.
   *
   * @param request the request
   * @return the block parameter
   */
  protected abstract BlockParameter blockParameter(JsonRpcRequestContext request);

  /**
   * Result by block number object.
   *
   * @param request the request
   * @param blockNumber the block number
   * @return the object
   */
  protected abstract Object resultByBlockNumber(JsonRpcRequestContext request, long blockNumber);

  /**
   * Gets blockchain queries.
   *
   * @return the blockchain queries
   */
  protected BlockchainQueries getBlockchainQueries() {
    return blockchainQueriesSupplier.get();
  }

  /**
   * Pending result object.
   *
   * @param request the request
   * @return the object
   */
  protected Object pendingResult(final JsonRpcRequestContext request) {
    // TODO: Update once we mine and better understand pending semantics.
    // For now act like we are not mining and just return latest.
    return latestResult(request);
  }

  /**
   * Latest result object.
   *
   * @param request the request
   * @return the object
   */
  protected Object latestResult(final JsonRpcRequestContext request) {
    return resultByBlockNumber(request, blockchainQueriesSupplier.get().headBlockNumber());
  }

  /**
   * Finalized result object.
   *
   * @param request the request
   * @return the object
   */
  protected Object finalizedResult(final JsonRpcRequestContext request) {
    return posRelatedResult(request, BlockchainQueries::finalizedBlockHeader);
  }

  /**
   * Safe result object.
   *
   * @param request the request
   * @return the object
   */
  protected Object safeResult(final JsonRpcRequestContext request) {
    return posRelatedResult(request, BlockchainQueries::safeBlockHeader);
  }

  private Object posRelatedResult(
      final JsonRpcRequestContext request,
      final Function<BlockchainQueries, Optional<BlockHeader>> blockHeaderSupplier) {

    return blockHeaderSupplier
        .apply(blockchainQueriesSupplier.get())
        .map(header -> resultByBlockNumber(request, header.getNumber()))
        .orElseGet(
            () ->
                new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.UNKNOWN_BLOCK));
  }

  /**
   * Find result by param type object.
   *
   * @param request the request
   * @return the object
   */
  protected Object findResultByParamType(final JsonRpcRequestContext request) {
    final BlockParameter blockParam = blockParameter(request);
    final Optional<Long> blockNumber = blockParam.getNumber();

    if (blockNumber.isPresent()) {
      return resultByBlockNumber(request, blockNumber.get());
    } else if (blockParam.isLatest()) {
      return latestResult(request);
    } else if (blockParam.isFinalized()) {
      return finalizedResult(request);
    } else if (blockParam.isSafe()) {
      return safeResult(request);
    } else {
      return pendingResult(request);
    }
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
