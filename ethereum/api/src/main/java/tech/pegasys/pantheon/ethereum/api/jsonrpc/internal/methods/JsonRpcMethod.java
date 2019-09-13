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

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;

import java.util.ArrayList;
import java.util.List;

public interface JsonRpcMethod {

  /**
   * Standardised JSON-RPC method name.
   *
   * @return identification of the JSON-RPC method.
   */
  String getName();

  /**
   * Applies the method to given request.
   *
   * @param request input data for the JSON-RPC method.
   * @return output from applying the JSON-RPC method to the input.
   */
  JsonRpcResponse response(JsonRpcRequest request);

  /**
   * The list of Permissions that correspond to this JSON-RPC method. e.g. [net/*, net/listening]
   *
   * @return list of permissions that match this method.
   */
  default List<String> getPermissions() {
    List<String> permissions = new ArrayList<>();
    permissions.add("*:*");
    permissions.add(this.getName().replace('_', ':'));
    permissions.add(this.getName().substring(0, this.getName().indexOf('_')) + ":*");
    return permissions;
  };
}
