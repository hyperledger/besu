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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** The Send request Legacy. */
@JsonPropertyOrder({"payload", "from", "to"})
public class SendRequestLegacy extends SendRequest {
  private final List<String> to;

  /**
   * Instantiates a new Send request legacy.
   *
   * @param payload the payload
   * @param from the from
   * @param to the to
   */
  public SendRequestLegacy(
      @JsonProperty(value = "payload") final String payload,
      @JsonProperty(value = "from") final String from,
      @JsonProperty(value = "to") final List<String> to) {
    super(payload, from);
    this.to = to;
  }

  /**
   * Gets to.
   *
   * @return the to
   */
  public List<String> getTo() {
    return to;
  }
}
