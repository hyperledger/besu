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
package org.hyperledger.besu.plugin.services.consensus.jsonrpc;

/** Placeholder */
public class InvalidJsonRpcRequestException extends IllegalArgumentException {

  /** The RPC error type */
  private final RpcErrorType rpcErrorType;

  /**
   * Placeholder
   *
   * @param message the message
   */
  public InvalidJsonRpcRequestException(final String message) {
    super(message);
    rpcErrorType = RpcErrorType.INVALID_PARAMS;
  }

  /**
   * Placeholder
   *
   * @param message the error message
   * @param rpcErrorType the error type
   */
  public InvalidJsonRpcRequestException(final String message, final RpcErrorType rpcErrorType) {
    super(message);
    this.rpcErrorType = rpcErrorType;
  }

  /**
   * Placeholder
   *
   * @param message the error message
   * @param cause the cause of the error
   */
  public InvalidJsonRpcRequestException(final String message, final Throwable cause) {
    super(message, cause);
    rpcErrorType = RpcErrorType.INVALID_PARAMS;
  }

  /**
   * Placeholder
   *
   * @param message the error message
   * @param rpcErrorType the error type
   * @param cause the cause of the error
   */
  public InvalidJsonRpcRequestException(
      final String message, final RpcErrorType rpcErrorType, final Throwable cause) {
    super(message, cause);
    this.rpcErrorType = rpcErrorType;
  }

  /**
   * Get the error type
   *
   * @return the error type
   */
  public RpcErrorType getRpcErrorType() {
    return rpcErrorType;
  }
}
