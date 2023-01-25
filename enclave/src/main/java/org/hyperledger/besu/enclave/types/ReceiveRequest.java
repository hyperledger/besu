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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** The Receive request. */
@JsonPropertyOrder({"key", "to"})
public class ReceiveRequest {
  private final String key;
  private final String to;

  /**
   * Instantiates a new Receive request.
   *
   * @param key the key
   * @param to the to
   */
  public ReceiveRequest(
      @JsonProperty(value = "key") final String key, @JsonProperty(value = "to") final String to) {
    this.key = key;
    this.to = to;
  }

  /**
   * Instantiates a new Receive request.
   *
   * @param key the key
   */
  public ReceiveRequest(final String key) {
    this(key, null);
  }

  /**
   * Gets key.
   *
   * @return the key
   */
  public String getKey() {
    return key;
  }

  /**
   * Gets to.
   *
   * @return the to
   */
  public String getTo() {
    return to;
  }
}
