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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

/** The type Web socket rpc request. */
public class WebSocketRpcRequest extends JsonRpcRequest {

  private String connectionId;

  /**
   * Instantiates a new Web socket rpc request.
   *
   * @param version the version
   * @param method the method
   * @param params the params
   * @param connectionId the connection id
   */
  @JsonCreator
  public WebSocketRpcRequest(
      @JsonProperty("jsonrpc") final String version,
      @JsonProperty("method") final String method,
      @JsonProperty("params") final Object[] params,
      @JsonProperty("connectionId") final String connectionId) {
    super(version, method, params);
    this.connectionId = connectionId;
  }

  /**
   * Sets connection id.
   *
   * @param connectionId the connection id
   */
  @JsonSetter("connectionId")
  public void setConnectionId(final String connectionId) {
    this.connectionId = connectionId;
  }

  /**
   * Gets connection id.
   *
   * @return the connection id
   */
  @JsonGetter("connectionId")
  public String getConnectionId() {
    return this.connectionId;
  }
}
