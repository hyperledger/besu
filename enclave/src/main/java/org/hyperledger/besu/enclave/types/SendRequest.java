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
package org.hyperledger.besu.enclave.types;

import static java.nio.charset.StandardCharsets.UTF_8;

/** The Send request. */
public abstract class SendRequest {
  private final byte[] payload;
  private final String from;

  /**
   * Instantiates a new Send request.
   *
   * @param payload the payload
   * @param from the from
   */
  protected SendRequest(final String payload, final String from) {
    this.payload = payload.getBytes(UTF_8);
    this.from = from;
  }

  /**
   * Get payload byte [ ].
   *
   * @return the byte [ ]
   */
  public byte[] getPayload() {
    return payload;
  }

  /**
   * Gets from.
   *
   * @return the from
   */
  public String getFrom() {
    return from;
  }
}
