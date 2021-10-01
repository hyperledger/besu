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
package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;

import java.util.function.Function;

/**
 * This service allows you to add functions exposed via RPC endpoints.
 *
 * <p>This service will be available during the registration callback and must be used during the
 * registration callback. RPC endpoints are configured prior to the start callback and all endpoints
 * connected. No endpoint will actually be called prior to the start callback so initialization
 * unrelated to the callback registration can also be done at that time.
 */
public interface RpcEndpointService extends BesuService {

  /**
   * Register a function as an RPC endpoint exposed via JSON-RPC.
   *
   * <p>The mechanism is a Java function that takes a list of Strings and returns any Java object,
   * registered in a specific namespace with a function name.
   *
   * <p>The resulting endpoint is the {@code namespace} and the {@code functionName} concatenated
   * with an underscore to create the JSON-RPC method name.
   *
   * <p>The function takes a {@link PluginRpcRequest} which contains a list of the inputs expressed
   * entirely as strings. Javascript numbers are converted to strings via their toString method, and
   * complex input objects are not supported.
   *
   * <p>The output is a Java object, primitive, or array that will be inspected via <a
   * href="https://github.com/FasterXML/jackson-databind">Jackson databind</a>. In general if
   * JavaBeans naming patterns are followed those names will be reflected in the returned JSON
   * object. If the method throws an exception the return is an error with an INTERNAL_ERROR
   * treatment.
   *
   * @param namespace The namespace of the method, must be alphanumeric.
   * @param functionName The name of the function, must be alphanumeric.
   * @param function The function itself.
   * @param <T> specified type of return object
   */
  <T> void registerRPCEndpoint(
      String namespace, String functionName, Function<PluginRpcRequest, T> function);
}
