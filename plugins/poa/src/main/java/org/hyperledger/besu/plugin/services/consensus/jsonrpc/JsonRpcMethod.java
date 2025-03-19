/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.plugin.services.consensus.jsonrpc;

import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;

/** Placeholder */
public interface JsonRpcMethod {

  /**
   * Applies the method to given request.
   *
   * @param request input data for the JSON-RPC method.
   * @return output from applying the JSON-RPC method to the input.
   */
  String response(PluginRpcRequest request);

  /**
   * Placeholder
   *
   * @return the name of the method
   */
  String getName();
}
