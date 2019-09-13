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

import tech.pegasys.pantheon.ethereum.api.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RpcModules implements JsonRpcMethod {

  private final Map<String, String> moduleVersions;

  public RpcModules(final Collection<RpcApi> modulesList) {
    this.moduleVersions =
        modulesList.stream()
            .map(RpcApi::getCliValue)
            .map(String::toLowerCase)
            .collect(Collectors.toMap(Function.identity(), s -> "1.0"));
  }

  @Override
  public String getName() {
    return RpcMethod.RPC_MODULES.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    return new JsonRpcSuccessResponse(req.getId(), moduleVersions);
  }
}
