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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods.miner;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.core.Address;

public class MinerSetCoinbase implements JsonRpcMethod {

  private final MiningCoordinator miningCoordinator;
  private final JsonRpcParameter parameters;

  public MinerSetCoinbase(
      final MiningCoordinator miningCoordinator, final JsonRpcParameter parameters) {
    this.miningCoordinator = miningCoordinator;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return RpcMethod.MINER_SET_COINBASE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    try {
      final Address coinbase = parameters.required(req.getParams(), 0, Address.class);
      miningCoordinator.setCoinbase(coinbase);
      return new JsonRpcSuccessResponse(req.getId(), true);
    } catch (final UnsupportedOperationException ex) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.INVALID_REQUEST);
    }
  }
}
