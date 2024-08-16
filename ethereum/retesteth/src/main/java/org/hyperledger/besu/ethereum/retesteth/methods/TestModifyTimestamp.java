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
package org.hyperledger.besu.ethereum.retesteth.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.retesteth.RetestethContext;

public class TestModifyTimestamp implements JsonRpcMethod {

  private final RetestethContext context;

  public TestModifyTimestamp(final RetestethContext context) {
    this.context = context;
  }

  @Override
  public String getName() {
    return "test_modifyTimestamp";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final long epochSeconds;
    try {
      epochSeconds = requestContext.getRequiredParameter(0, Long.class);
    } catch (Exception e) { // TODO:replace with JsonRpcParameter.JsonRpcParameterException
      throw new InvalidJsonRpcParameters(
          "Invalid timestamp parameter (index 0)", RpcErrorType.INVALID_TIMESTAMP_PARAMS, e);
    }
    context.getRetestethClock().resetTime(epochSeconds);
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), true);
  }
}
