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

/** The Send request Besu. */
@JsonPropertyOrder({"payload", "from", "privacyGroupId"})
public class SendRequestBesu extends SendRequest {
  private final String privacyGroupId;

  /**
   * Instantiates a new Send request Besu.
   *
   * @param payload the payload
   * @param from the from
   * @param privacyGroupId the privacy group id
   */
  public SendRequestBesu(
      @JsonProperty(value = "payload") final String payload,
      @JsonProperty(value = "from") final String from,
      @JsonProperty(value = "to") final String privacyGroupId) {
    super(payload, from);

    this.privacyGroupId = privacyGroupId;
  }

  /**
   * Gets privacy group id.
   *
   * @return the privacy group id
   */
  public String getPrivacyGroupId() {
    return privacyGroupId;
  }
}
