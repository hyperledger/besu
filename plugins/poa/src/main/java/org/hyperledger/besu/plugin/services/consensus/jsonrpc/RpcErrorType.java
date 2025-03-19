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
package org.hyperledger.besu.plugin.services.consensus.jsonrpc;

import org.hyperledger.besu.plugin.services.rpc.RpcMethodError;

import java.util.Optional;
import java.util.function.Function;

/** RPC error types */
public enum RpcErrorType implements RpcMethodError {
  // Standard errors
  /** Placeholder */
  INVALID_PARAMS(INVALID_PARAMS_ERROR_CODE, "Invalid params"),
  /** Placeholder */
  INVALID_BLOCK_HASH_PARAMS(INVALID_PARAMS_ERROR_CODE, "Invalid block hash params");

  private final int code;
  private final String message;
  private final Function<String, Optional<String>> dataDecoder;

  RpcErrorType(final int code, final String message) {
    this(code, message, null);
  }

  /**
   * Create RPC error type
   *
   * @param code the error code
   * @param message the error message
   * @param dataDecoder the decoder
   */
  RpcErrorType(
      final int code, final String message, final Function<String, Optional<String>> dataDecoder) {
    this.code = code;
    this.message = message;
    this.dataDecoder = dataDecoder;
  }

  @Override
  public int getCode() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public Optional<String> decodeData(final String data) {
    return dataDecoder == null ? Optional.empty() : dataDecoder.apply(data);
  }
}
