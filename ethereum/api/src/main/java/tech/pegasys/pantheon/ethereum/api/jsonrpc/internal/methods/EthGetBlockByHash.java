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
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results.BlockResult;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import tech.pegasys.pantheon.ethereum.core.Hash;

public class EthGetBlockByHash implements JsonRpcMethod {

  private final BlockResultFactory blockResult;
  private final BlockchainQueries blockchain;
  private final JsonRpcParameter parameters;

  public EthGetBlockByHash(
      final BlockchainQueries blockchain,
      final BlockResultFactory blockResult,
      final JsonRpcParameter parameters) {
    this.blockchain = blockchain;
    this.blockResult = blockResult;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_BLOCK_BY_HASH.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    return new JsonRpcSuccessResponse(request.getId(), blockResult(request));
  }

  private BlockResult blockResult(final JsonRpcRequest request) {
    final Hash hash = parameters.required(request.getParams(), 0, Hash.class);

    if (isCompleteTransactions(request)) {
      return transactionComplete(hash);
    }

    return transactionHash(hash);
  }

  private BlockResult transactionComplete(final Hash hash) {
    return blockchain.blockByHash(hash).map(tx -> blockResult.transactionComplete(tx)).orElse(null);
  }

  private BlockResult transactionHash(final Hash hash) {
    return blockchain
        .blockByHashWithTxHashes(hash)
        .map(tx -> blockResult.transactionHash(tx))
        .orElse(null);
  }

  private boolean isCompleteTransactions(final JsonRpcRequest request) {
    return parameters.required(request.getParams(), 1, Boolean.class);
  }
}
