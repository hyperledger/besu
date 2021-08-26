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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionReceiptResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionReceiptRootResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionReceiptStatusResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.mainnet.TransactionReceiptType;

public class EthGetTransactionReceipt implements JsonRpcMethod {

  private final BlockchainQueries blockchainQueries;

  public EthGetTransactionReceipt(final BlockchainQueries blockchainQueries) {
    this.blockchainQueries = blockchainQueries;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_TRANSACTION_RECEIPT.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Hash hash = requestContext.getRequiredParameter(0, Hash.class);
    final TransactionReceiptResult result =
        blockchainQueries
            .transactionReceiptByTransactionHash(hash)
            .map(this::getResult)
            .orElse(null);
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), result);
  }

  private TransactionReceiptResult getResult(final TransactionReceiptWithMetadata receipt) {
    if (receipt.getReceipt().getTransactionReceiptType() == TransactionReceiptType.ROOT) {
      return new TransactionReceiptRootResult(receipt);
    } else {
      return new TransactionReceiptStatusResult(receipt);
    }
  }
}
