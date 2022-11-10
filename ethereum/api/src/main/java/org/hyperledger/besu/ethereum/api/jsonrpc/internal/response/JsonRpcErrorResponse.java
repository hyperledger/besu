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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.MoreObjects;

@JsonPropertyOrder({"jsonrpc", "id", "error"})
public class JsonRpcErrorResponse implements JsonRpcResponse {

  private final Object id;
  private final JsonRpcError error;
  private final Object result;

  public JsonRpcErrorResponse(final Object id, final JsonRpcError error) {
    this.id = id;
    this.error = error;
    this.result = null;
  }

  public JsonRpcErrorResponse(final Object id, final JsonRpcError error, final Object result) {
    this.id = id;
    this.error = error;
    this.result = result;
  }

  @JsonGetter("id")
  public Object getId() {
    return id;
  }

  @JsonGetter("error")
  public JsonRpcError getError() {
    return error;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonGetter("result")
  public Object getResult() {
    return result;
  }

  @Override
  @JsonIgnore
  public JsonRpcResponseType getType() {
    return JsonRpcResponseType.ERROR;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JsonRpcErrorResponse that = (JsonRpcErrorResponse) o;
    return Objects.equals(id, that.id)
        && error == that.error
        && Objects.equals(result, that.result);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, error, result);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("error", error)
        .add("result", result)
        .toString();
  }
}
