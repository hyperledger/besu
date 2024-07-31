/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import java.util.HashSet;
import java.util.Set;

import org.immutables.value.Value;

@Value.Immutable
public interface InProcessRpcConfiguration {
  boolean DEFAULT_IN_PROCESS_RPC_ENABLED = false;
  Set<String> DEFAULT_IN_PROCESS_RPC_APIS = new HashSet<>(RpcApis.DEFAULT_RPC_APIS);

  @Value.Default
  default boolean isEnabled() {
    return DEFAULT_IN_PROCESS_RPC_ENABLED;
  }

  @Value.Default
  default Set<String> getInProcessRpcApis() {
    return DEFAULT_IN_PROCESS_RPC_APIS;
  }
}
