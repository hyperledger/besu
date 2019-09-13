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
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results.Quantity;

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
  public JsonRpcResponse response(final JsonRpcRequest req) {
    final long value = blockchain.get().headBlockNumber();
    return new JsonRpcSuccessResponse(
        req.getId(), resultAsInteger ? value : Quantity.create(value));
  }
}
