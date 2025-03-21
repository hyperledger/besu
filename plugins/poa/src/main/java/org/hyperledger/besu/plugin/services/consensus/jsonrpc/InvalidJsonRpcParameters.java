/*
 * Copyright contributors to Besu.
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
public class InvalidJsonRpcParameters extends InvalidJsonRpcRequestException {

  /**
   * Placeholder
   *
   * @param s message
   */
  public InvalidJsonRpcParameters(final String s) {
    super(s);
  }

  /**
   * Placeholder
   *
   * @param message the error message
   * @param rpcErrorType the error type
   */
  public InvalidJsonRpcParameters(final String message, final RpcErrorType rpcErrorType) {
    super(message, rpcErrorType);
  }

  /**
   * Placeholder
   *
   * @param message the error message
   * @param cause the error cause
   */
  public InvalidJsonRpcParameters(final String message, final Throwable cause) {
    super(message, cause);
  }

  /**
   * Placeholder
   *
   * @param message the error message
   * @param rpcErrorType the error type
   * @param cause the error cause
   */
  public InvalidJsonRpcParameters(
      final String message, final RpcErrorType rpcErrorType, final Throwable cause) {
    super(message, rpcErrorType, cause);
  }
}
