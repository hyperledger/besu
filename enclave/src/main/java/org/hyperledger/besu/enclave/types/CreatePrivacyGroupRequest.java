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

public class CreatePrivacyGroupRequest {

  private final String[] addresses;
  private final String from;
  private final String name;
  private final String description;

  @JsonCreator
  public CreatePrivacyGroupRequest(
      @JsonProperty("addresses") final String[] addresses,
      @JsonProperty("from") final String from,
      @JsonProperty("name") final String name,
      @JsonProperty("description") final String description) {
    this.addresses = addresses;
    this.from = from;
    this.name = name;
    this.description = description;
  }

  @JsonProperty("addresses")
  public String[] addresses() {
    return addresses;
  }

  @JsonProperty("from")
  public String from() {
    return from;
  }

  @JsonProperty("name")
  public String name() {
    return name;
  }

  @JsonProperty("description")
  public String description() {
    return description;
  }
}
