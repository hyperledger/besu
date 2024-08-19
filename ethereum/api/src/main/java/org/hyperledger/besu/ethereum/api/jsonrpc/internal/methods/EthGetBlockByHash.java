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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public class EthGetBlockByHash implements JsonRpcMethod {

  private final BlockResultFactory blockResult;
  private final Supplier<BlockchainQueries> blockchain;
  private final boolean includeCoinbase;

  public EthGetBlockByHash(
      final BlockchainQueries blockchain, final BlockResultFactory blockResult) {
    this(Suppliers.ofInstance(blockchain), blockResult, false);
  }

  public EthGetBlockByHash(
      final Supplier<BlockchainQueries> blockchain,
      final BlockResultFactory blockResult,
      final boolean includeCoinbase) {
    this.blockchain = blockchain;
    this.blockResult = blockResult;
    this.includeCoinbase = includeCoinbase;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_BLOCK_BY_HASH.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), blockResult(requestContext));
  }

  private BlockResult blockResult(final JsonRpcRequestContext request) {
    final Hash hash;
    try {
      hash = request.getRequiredParameter(0, Hash.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid block hash parameter (index 0)", RpcErrorType.INVALID_BLOCK_HASH_PARAMS, e);
    }

    if (isCompleteTransactions(request)) {
      return transactionComplete(hash);
    }

    return transactionHash(hash);
  }

  private BlockResult transactionComplete(final Hash hash) {
    return blockchain
        .get()
        .blockByHash(hash)
        .map(tx -> blockResult.transactionComplete(tx, includeCoinbase))
        .orElse(null);
  }

  private BlockResult transactionHash(final Hash hash) {
    return blockchain
        .get()
        .blockByHashWithTxHashes(hash)
        .map(blockResult::transactionHash)
        .orElse(null);
  }

  private boolean isCompleteTransactions(final JsonRpcRequestContext requestContext) {
    try {
      return requestContext.getRequiredParameter(1, Boolean.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid return complete transaction parameter (index 1)",
          RpcErrorType.INVALID_RETURN_COMPLETE_TRANSACTION_PARAMS,
          e);
    }
  }
}
