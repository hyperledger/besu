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
package org.hyperledger.besu.plugin.services;

import java.util.Collection;

public interface JsonRpcConfigurationService extends BesuService {
  /**
   * has JSON-RPC been enabled
   *
   * @return has JSON-RPC been enabled
   */
  boolean isJsonRpcEnabled();

  /**
   * Port JSON-RPC HTTP is listening on
   *
   * @return Port JSON-RPC HTTP is listening on
   */
  int getJsonRpcPort();

  /**
   * The host that JSON-RPC HTTP is listening on
   *
   * @return Host that JSON-RPC HTTP is listening on
   */
  String getJsonRpcHost();

  /**
   * The list of APIs enabled on JSON-RPC HTTP service
   *
   * @return list of APIs enabled on JSON-RPC HTTP service
   */
  Collection<String> getJsonRpcApis();
}
