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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;

import java.util.Map;

public class RpcMethods {

  private Map<String, JsonRpcMethod> enabledMethods;

  public RpcMethods(final Map<String, JsonRpcMethod> enabledMethods) {
    this.enabledMethods = enabledMethods;
  }

  public JsonRpcMethod getMethod(final String methodName) {
    return enabledMethods.get(methodName);
  }

  public boolean isEnabled(final String methodName) {
    return enabledMethods.containsKey(methodName);
  }

  public boolean isDefined(final String methodName) {
    return RpcMethod.rpcMethodExists(methodName) || isEnabled(methodName);
  }
}
