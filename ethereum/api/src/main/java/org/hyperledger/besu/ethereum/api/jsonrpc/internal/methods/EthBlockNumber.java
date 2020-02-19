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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public class EthBlockNumber implements JsonRpcMethod {

  private final Supplier<BlockchainQueries> blockchain;
  private final boolean resultAsInteger;

  public EthBlockNumber(final BlockchainQueries blockchain) {
    this(Suppliers.ofInstance(blockchain), false);
  }

  public EthBlockNumber(
      final Supplier<BlockchainQueries> blockchain, final boolean resultAsInteger) {
    this.blockchain = blockchain;
    this.resultAsInteger = resultAsInteger;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_BLOCK_NUMBER.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final long value = blockchain.get().headBlockNumber();
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), resultAsInteger ? value : Quantity.create(value));
  }
}
