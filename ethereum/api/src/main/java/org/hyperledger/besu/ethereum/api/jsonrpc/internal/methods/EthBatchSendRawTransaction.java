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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.util.DomainObjectDecodeUtils;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;

import java.util.function.Supplier;
import java.util.stream.IntStream;

import com.google.common.base.Suppliers;

public class EthBatchSendRawTransaction implements JsonRpcMethod {

  private final Supplier<TransactionPool> transactionPool;

  public EthBatchSendRawTransaction(final TransactionPool transactionPool) {
    this(Suppliers.ofInstance(transactionPool));
  }

  public EthBatchSendRawTransaction(final Supplier<TransactionPool> transactionPool) {
    this.transactionPool = transactionPool;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_BATCH_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    IntStream.range(0, requestContext.getRequest().getParamLength())
        .mapToObj(i -> requestContext.getRequiredParameter(i, String.class))
        .map(DomainObjectDecodeUtils::decodeRawTransaction)
        .forEach(transactionPool.get()::addLocalTransaction);
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
  }
}
