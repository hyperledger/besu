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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

@JsonInclude(value = JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public class JsonRpcError {
  private final int code;
  private final String message;
  private final String data;
  private String reason;

  @JsonCreator
  public JsonRpcError(
      @JsonProperty("code") final int code,
      @JsonProperty("message") final String message,
      @JsonProperty("data") final String data) {
    this.code = code;
    this.message = message;
    this.data = data;
  }

  public JsonRpcError(final RpcErrorType errorType, final String data) {
    this(errorType.getCode(), errorType.getMessage(), data);

    // For execution reverted errors decode the data (if present)
    if (errorType == RpcErrorType.REVERT_ERROR && data != null) {
      JsonRpcErrorResponse.decodeRevertReason(Bytes.fromHexString(data))
          .ifPresent(
              (decodedReason) -> {
                this.reason = decodedReason;
              });
    }
  }

  public JsonRpcError(final RpcErrorType errorType) {
    this(errorType, null);
  }

  @JsonGetter("code")
  public int getCode() {
    return code;
  }

  @JsonGetter("message")
  public String getMessage() {
    return (reason == null ? message : message + ": " + reason);
  }

  @JsonGetter("data")
  public String getData() {
    return data;
  }

  public void setReason(final String reason) {
    this.reason = reason;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JsonRpcError that = (JsonRpcError) o;
    return code == that.code
        && Objects.equals(message.split(":", -1)[0], that.message.split(":", -1)[0])
        && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message.split(":", -1)[0], data);
  }
}
