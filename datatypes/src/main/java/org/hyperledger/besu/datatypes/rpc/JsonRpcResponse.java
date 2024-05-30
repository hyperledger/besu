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
package org.hyperledger.besu.datatypes.rpc;

import com.fasterxml.jackson.annotation.JsonGetter;

/** Represent a Json RPC response */
public interface JsonRpcResponse {

  /**
   * The JsonRPC version
   *
   * @return the version
   */
  @JsonGetter("jsonrpc")
  default String getVersion() {
    return "2.0";
  }

  /**
   * Get the response type
   *
   * @return the response type
   */
  JsonRpcResponseType getType();
}
