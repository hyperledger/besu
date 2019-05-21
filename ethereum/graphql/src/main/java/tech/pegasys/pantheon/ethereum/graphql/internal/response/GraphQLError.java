/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.graphql.internal.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonGetter;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum GraphQLError {
  // Standard errors
  INVALID_PARAMS(-32602, "Invalid params"),
  INTERNAL_ERROR(-32603, "Internal error"),

  CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE(-32008, "Initial sync is still in progress");

  private final int code;
  private final String message;

  GraphQLError(final int code, final String message) {
    this.code = code;
    this.message = message;
  }

  @JsonGetter("code")
  public int getCode() {
    return code;
  }

  @JsonGetter("message")
  public String getMessage() {
    return message;
  }
}
