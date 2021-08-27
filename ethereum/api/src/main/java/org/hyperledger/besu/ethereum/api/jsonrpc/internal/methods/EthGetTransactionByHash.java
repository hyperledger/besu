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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionCompleteResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionPendingResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;

import java.util.Optional;

public class EthGetTransactionByHash implements JsonRpcMethod {

  private final BlockchainQueries blockchain;
  private final AbstractPendingTransactionsSorter pendingTransactions;

  public EthGetTransactionByHash(
      final BlockchainQueries blockchain,
      final AbstractPendingTransactionsSorter pendingTransactions) {
    this.blockchain = blockchain;
    this.pendingTransactions = pendingTransactions;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_TRANSACTION_BY_HASH.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    if (requestContext.getRequest().getParamLength() != 1) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }
    final Hash hash = requestContext.getRequiredParameter(0, Hash.class);
    final JsonRpcSuccessResponse jsonRpcSuccessResponse =
        new JsonRpcSuccessResponse(requestContext.getRequest().getId(), getResult(hash));
    return jsonRpcSuccessResponse;
  }

  private Object getResult(final Hash hash) {
    final Optional<Object> transactionPendingResult =
        pendingTransactions.getTransactionByHash(hash).map(TransactionPendingResult::new);
    return transactionPendingResult.orElseGet(
        () -> blockchain.transactionByHash(hash).map(TransactionCompleteResult::new).orElse(null));
  }
}
