/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INTERNAL_ERROR;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginJsonRpcMethod implements JsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(PluginJsonRpcMethod.class);

  private final String name;
  private final Function<PluginRpcRequest, ?> function;

  public PluginJsonRpcMethod(final String name, final Function<PluginRpcRequest, ?> function) {
    this.name = name;
    this.function = function;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext request) {
    try {
      Object result = function.apply(() -> request.getRequest().getParams());
      return new JsonRpcSuccessResponse(request.getRequest().getId(), result);
    } catch (Exception ex) {
      LOG.error("Error calling plugin JSON-RPC endpoint", ex);
      return new JsonRpcErrorResponse(request.getRequest().getId(), INTERNAL_ERROR);
    }
  }
}
