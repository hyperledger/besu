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
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;

import java.util.Optional;

public class EthGetBlockTransactionCountByHash implements JsonRpcMethod {

  private final BlockchainQueries blockchain;

  public EthGetBlockTransactionCountByHash(final BlockchainQueries blockchain) {
    this.blockchain = blockchain;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_BLOCK_TRANSACTION_COUNT_BY_HASH.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Hash hash;
    try {
      hash = requestContext.getRequiredParameter(0, Hash.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid block header hash parameter (index 0)",
          RpcErrorType.INVALID_BLOCK_HASH_PARAMS,
          e);
    }
    final Optional<Integer> count = blockchain.getTransactionCount(hash);

    if (count.isEmpty()) {
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), null);
    }
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), Quantity.create(count.get()));
  }
}
