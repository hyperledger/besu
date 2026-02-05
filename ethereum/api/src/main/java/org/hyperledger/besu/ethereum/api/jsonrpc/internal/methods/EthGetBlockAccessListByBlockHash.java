/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockAccessListResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;

public class EthGetBlockAccessListByBlockHash implements JsonRpcMethod {

  private final BlockchainQueries blockchainQueries;

  public EthGetBlockAccessListByBlockHash(final BlockchainQueries blockchainQueries) {
    this.blockchainQueries = blockchainQueries;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_BLOCK_ACCESS_LIST_BY_BLOCK_HASH.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Hash blockHash;
    try {
      blockHash = requestContext.getRequiredParameter(0, Hash.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid block hash parameter (index 0)", RpcErrorType.INVALID_BLOCK_HASH_PARAMS, e);
    }

    final var requestId = requestContext.getRequest().getId();
    final var maybeHeader = blockchainQueries.getBlockHeaderByHash(blockHash);
    if (maybeHeader.isEmpty()) {
      return new JsonRpcErrorResponse(requestId, RpcErrorType.BLOCK_NOT_FOUND);
    }

    final BlockHeader header = maybeHeader.get();
    if (!blockchainQueries.isBlockAccessListSupported(header)) {
      return new JsonRpcErrorResponse(
          requestId, RpcErrorType.BLOCK_ACCESS_LIST_NOT_AVAILABLE_FOR_PRE_AMSTERDAM_BLOCKS);
    }

    return blockchainQueries
        .getBlockchain()
        .getBlockAccessList(blockHash)
        .<JsonRpcResponse>map(
            bal ->
                new JsonRpcSuccessResponse(
                    requestId, BlockAccessListResult.fromBlockAccessList(bal)))
        .orElseGet(
            () -> new JsonRpcErrorResponse(requestId, RpcErrorType.PRUNED_HISTORY_UNAVAILABLE));
  }
}
