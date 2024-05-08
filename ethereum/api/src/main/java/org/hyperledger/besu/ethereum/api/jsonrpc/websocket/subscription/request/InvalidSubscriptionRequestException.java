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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request;

/** The type Invalid subscription request exception. */
public class InvalidSubscriptionRequestException extends RuntimeException {

  /** Instantiates a new Invalid subscription request exception. */
  public InvalidSubscriptionRequestException() {
    super();
  }

  /**
   * Instantiates a new Invalid subscription request exception.
   *
   * @param message the message
   */
  public InvalidSubscriptionRequestException(final String message) {
    super(message);
  }

  /**
   * Instantiates a new Invalid subscription request exception.
   *
   * @param message the message
   * @param cause the cause
   */
  public InvalidSubscriptionRequestException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
