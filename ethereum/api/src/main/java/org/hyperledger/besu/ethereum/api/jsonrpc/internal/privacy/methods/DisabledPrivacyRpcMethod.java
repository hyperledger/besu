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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;

@Deprecated(since = "24.12.0")
public class DisabledPrivacyRpcMethod implements JsonRpcMethod {

  private final String methodName;

  @Override
  public String getName() {
    return methodName;
  }

  public DisabledPrivacyRpcMethod(final String methodName) {
    this.methodName = methodName;
  }

  @Override
  public final JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return new JsonRpcErrorResponse(
        requestContext.getRequest().getId(), RpcErrorType.PRIVACY_NOT_ENABLED);
  }
}
