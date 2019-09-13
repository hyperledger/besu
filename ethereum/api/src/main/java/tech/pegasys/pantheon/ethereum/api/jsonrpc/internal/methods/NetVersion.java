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
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.math.BigInteger;
import java.util.Optional;

/**
 * In ConsenSys' client, net_version maps to the network id, as specified in *
 * https://github.com/ethereum/wiki/wiki/JSON-RPC#net_version
 *
 * <p>This method can be deprecated in the future, @see https://github.com/ethereum/EIPs/issues/611
 */
public class NetVersion implements JsonRpcMethod {
  private final String chainId;

  public NetVersion(final Optional<BigInteger> chainId) {
    this.chainId = String.valueOf(chainId.orElse(null));
  }

  @Override
  public String getName() {
    return RpcMethod.NET_VERSION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    return new JsonRpcSuccessResponse(req.getId(), chainId);
  }
}
