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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.TransactionCompleteResult;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.TransactionPendingResult;

import java.util.Optional;

public class EthGetTransactionByHash implements JsonRpcMethod {

  private final BlockchainQueries blockchain;
  private final PendingTransactions pendingTransactions;
  private final JsonRpcParameter parameters;

  public EthGetTransactionByHash(
      final BlockchainQueries blockchain,
      final PendingTransactions pendingTransactions,
      final JsonRpcParameter parameters) {
    this.blockchain = blockchain;
    this.pendingTransactions = pendingTransactions;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "eth_getTransactionByHash";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    if (request.getParamLength() != 1) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }
    final Hash hash = parameters.required(request.getParams(), 0, Hash.class);
    final JsonRpcSuccessResponse jsonRpcSuccessResponse =
        new JsonRpcSuccessResponse(request.getId(), getResult(hash));
    return jsonRpcSuccessResponse;
  }

  private Object getResult(final Hash hash) {
    final Optional<Object> transactionCompleteResult =
        blockchain.transactionByHash(hash).map(TransactionCompleteResult::new);
    return transactionCompleteResult.orElseGet(
        () ->
            pendingTransactions
                .getTransactionByHash(hash)
                .map(TransactionPendingResult::new)
                .orElse(null));
  }
}
