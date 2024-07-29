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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.response;

import org.hyperledger.besu.plugin.services.rpc.RpcResponseType;

import java.util.Arrays;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"jsonrpc", "id", "error"})
public class JsonRpcUnauthorizedResponse implements JsonRpcResponse {

  private final Object id;
  private final JsonRpcError error;
  @JsonIgnore private final RpcErrorType errorType;

  public JsonRpcUnauthorizedResponse(final Object id, final JsonRpcError error) {
    this.id = id;
    this.error = error;
    this.errorType = findErrorType(error.getCode(), error.getMessage());
  }

  public JsonRpcUnauthorizedResponse(final Object id, final RpcErrorType error) {
    this(id, new JsonRpcError(error));
  }

  @JsonGetter("id")
  public Object getId() {
    return id;
  }

  @JsonGetter("error")
  public JsonRpcError getError() {
    return error;
  }

  @Override
  @JsonIgnore
  public RpcResponseType getType() {
    return RpcResponseType.UNAUTHORIZED;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JsonRpcUnauthorizedResponse that = (JsonRpcUnauthorizedResponse) o;
    return Objects.equals(id, that.id) && Objects.equals(error, that.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, error);
  }

  @JsonIgnore
  public RpcErrorType getErrorType() {
    return errorType;
  }

  private RpcErrorType findErrorType(final int code, final String message) {
    return Arrays.stream(RpcErrorType.values())
        .filter(e -> e.getCode() == code && e.getMessage().equals(message))
        .findFirst()
        .get();
  }
}
