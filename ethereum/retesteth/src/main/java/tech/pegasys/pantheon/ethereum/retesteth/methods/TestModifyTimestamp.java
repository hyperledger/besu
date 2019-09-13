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
package tech.pegasys.pantheon.ethereum.retesteth.methods;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.retesteth.RetestethContext;

public class TestModifyTimestamp implements JsonRpcMethod {

  private final RetestethContext context;
  private final JsonRpcParameter parameters;

  public TestModifyTimestamp(final RetestethContext context, final JsonRpcParameter parameters) {
    this.context = context;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "test_modifyTimestamp";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final long epochSeconds = parameters.required(request.getParams(), 0, Long.class);
    context.getRetestethClock().resetTime(epochSeconds);
    return new JsonRpcSuccessResponse(request.getId(), true);
  }
}
