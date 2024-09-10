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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;

import java.util.Optional;

public class AdminGenerateLogBloomCache implements JsonRpcMethod {

  private final BlockchainQueries blockchainQueries;

  public AdminGenerateLogBloomCache(final BlockchainQueries blockchainQueries) {
    this.blockchainQueries = blockchainQueries;
  }

  @Override
  public String getName() {
    return RpcMethod.ADMIN_GENERATE_LOG_BLOOM_CACHE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Optional<BlockParameter> startBlockParam;
    try {
      startBlockParam = requestContext.getOptionalParameter(0, BlockParameter.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid start block parameter (index 0)", RpcErrorType.INVALID_BLOCK_PARAMS, e);
    }
    final long startBlock;
    if (startBlockParam.isEmpty() || startBlockParam.get().isEarliest()) {
      startBlock = 0;
    } else if (startBlockParam.get().getNumber().isPresent()) {
      startBlock = startBlockParam.get().getNumber().get();
    } else {
      // latest, pending
      startBlock = Long.MAX_VALUE;
    }

    final Optional<BlockParameter> stopBlockParam;
    try {
      stopBlockParam = requestContext.getOptionalParameter(1, BlockParameter.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid stop block parameter (index 1)", RpcErrorType.INVALID_BLOCK_PARAMS, e);
    }
    final long stopBlock;
    if (stopBlockParam.isEmpty()) {
      if (startBlockParam.isEmpty()) {
        stopBlock = -1L;
      } else {
        stopBlock = Long.MAX_VALUE;
      }
    } else if (stopBlockParam.get().isEarliest()) {
      stopBlock = 0;
    } else if (stopBlockParam.get().getNumber().isPresent()) {
      stopBlock = stopBlockParam.get().getNumber().get();
    } else {
      // latest, pending
      stopBlock = Long.MAX_VALUE;
    }

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        blockchainQueries
            .getTransactionLogBloomCacher()
            .map(cacher -> cacher.requestCaching(startBlock, stopBlock))
            .orElse(null));
  }
}
