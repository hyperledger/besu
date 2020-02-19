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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"payload", "privacyGroupId", "senderKey"})
public class ReceiveResponse {

  private final byte[] payload;
  private final String privacyGroupId;
  private final String senderKey;

  @JsonCreator
  public ReceiveResponse(
      @JsonProperty(value = "payload") final byte[] payload,
      @JsonProperty(value = "privacyGroupId") final String privacyGroupId,
      @JsonProperty(value = "senderKey") final String senderKey) {
    this.payload = payload;
    this.privacyGroupId = privacyGroupId;
    this.senderKey = senderKey;
  }

  public byte[] getPayload() {
    return payload;
  }

  public String getPrivacyGroupId() {
    return privacyGroupId;
  }

  public String getSenderKey() {
    return senderKey;
  }
}
