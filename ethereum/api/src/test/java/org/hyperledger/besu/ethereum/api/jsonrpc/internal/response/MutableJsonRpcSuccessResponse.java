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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.response;

import org.hyperledger.besu.plugin.services.rpc.RpcResponseType;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonPropertyOrder({"jsonrpc", "id", "result"})
public class MutableJsonRpcSuccessResponse {

  private Object id;
  private Object result;
  private Object version;

  public MutableJsonRpcSuccessResponse() {
    this.id = null;
    this.result = null;
  }

  public MutableJsonRpcSuccessResponse(final Object id, final Object result) {
    this.id = id;
    this.result = result;
  }

  public MutableJsonRpcSuccessResponse(final Object id) {
    this.id = id;
    this.result = "Success";
  }

  @JsonGetter("id")
  public Object getId() {
    return id;
  }

  @JsonGetter("result")
  public Object getResult() {
    return result;
  }

  @JsonSetter("id")
  public void setId(final Object id) {
    this.id = id;
  }

  @JsonSetter("result")
  public void setResult(final Object result) {
    this.result = result;
  }

  @JsonGetter("jsonrpc")
  public Object getVersion() {
    return version;
  }

  @JsonSetter("jsonrpc")
  public void setVersion(final Object version) {
    this.version = version;
  }

  @JsonIgnore
  public RpcResponseType getType() {
    return RpcResponseType.SUCCESS;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MutableJsonRpcSuccessResponse that = (MutableJsonRpcSuccessResponse) o;
    return Objects.equals(id, that.id) && Objects.equals(result, that.result);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, result);
  }
}
