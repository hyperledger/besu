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

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.plugin.services.rpc.RpcMethodError;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The type Json rpc error. */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public class JsonRpcError {
  private final int code;
  private final String message;
  private final String data;
  private String reason;

  /**
   * Instantiates a new Json rpc error.
   *
   * @param code the code
   * @param message the message
   * @param data the data
   */
  @JsonCreator
  public JsonRpcError(
      @JsonProperty("code") final int code,
      @JsonProperty("message") final String message,
      @JsonProperty("data") final String data) {
    this.code = code;
    this.message = message;
    this.data = data;
  }

  /**
   * Instantiates a new Json rpc error.
   *
   * @param errorType the error type
   * @param data the data
   */
  public JsonRpcError(final RpcMethodError errorType, final String data) {
    this(errorType.getCode(), errorType.getMessage(), data);

    if (data != null) {
      errorType.decodeData(data).ifPresent(decodedData -> this.reason = decodedData);
    }
  }

  /**
   * Instantiates a new Json rpc error.
   *
   * @param errorType the error type
   */
  public JsonRpcError(final RpcErrorType errorType) {
    this(errorType, null);
  }

  /**
   * From json rpc error.
   *
   * @param validationResult the validation result
   * @return the json rpc error
   */
  public static JsonRpcError from(
      final ValidationResult<TransactionInvalidReason> validationResult) {
    final var jsonRpcError =
        new JsonRpcError(
            JsonRpcErrorConverter.convertTransactionInvalidReason(
                validationResult.getInvalidReason()));
    jsonRpcError.reason = validationResult.getErrorMessage();
    return jsonRpcError;
  }

  /**
   * Gets code.
   *
   * @return the code
   */
  @JsonGetter("code")
  public int getCode() {
    return code;
  }

  /**
   * Gets message.
   *
   * @return the message
   */
  @JsonGetter("message")
  public String getMessage() {
    return (reason == null ? message : message + ": " + reason);
  }

  /**
   * Gets data.
   *
   * @return the data
   */
  @JsonGetter("data")
  public String getData() {
    return data;
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
