/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.retesteth.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogSeries;
import org.hyperledger.besu.ethereum.retesteth.RetestethContext;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.Optional;

public class TestGetLogHash implements JsonRpcMethod {
  private final RetestethContext context;
  private final JsonRpcParameter parameters;

  public TestGetLogHash(final RetestethContext context, final JsonRpcParameter parameters) {
    this.context = context;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "test_getLogHash";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final Hash txHash = parameters.required(request.getParams(), 0, Hash.class);

    final Optional<TransactionReceiptWithMetadata> receipt =
        context.getBlockchainQueries().transactionReceiptByTransactionHash(txHash);
    return new JsonRpcSuccessResponse(
        request.getId(),
        receipt.map(this::calculateLogHash).orElse(Hash.EMPTY_LIST_HASH).toString());
  }

  private Hash calculateLogHash(
      final TransactionReceiptWithMetadata transactionReceiptWithMetadata) {
    final LogSeries logs = new LogSeries(transactionReceiptWithMetadata.getReceipt().getLogs());
    return Hash.hash(RLP.encode(logs::writeTo));
  }
}
