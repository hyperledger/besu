/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.enclave.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"payload", "privacyGroupId"})
public class ReceiveResponse {

  private byte[] payload;
  private String privacyGroupId;

  @JsonCreator
  public ReceiveResponse(
      @JsonProperty(value = "payload") final byte[] payload,
      @JsonProperty(value = "privacyGroupId") final String privacyGroupId) {
    this.payload = payload;
    this.privacyGroupId = privacyGroupId;
  }

  public byte[] getPayload() {
    return payload;
  }

  public String getPrivacyGroupId() {
    return privacyGroupId;
  }
}
