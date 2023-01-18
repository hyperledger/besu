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
package org.hyperledger.besu.enclave;

/** The Enclave server exception. */
public class EnclaveServerException extends RuntimeException {
  /** Status Code */
  private final int statusCode;

  /**
   * Instantiates a new Enclave server exception.
   *
   * @param statusCode the status code
   * @param message the message
   */
  public EnclaveServerException(final int statusCode, final String message) {
    super(message);
    this.statusCode = statusCode;
  }

  /**
   * Gets status code.
   *
   * @return the status code
   */
  public int getStatusCode() {
    return statusCode;
  }
}
