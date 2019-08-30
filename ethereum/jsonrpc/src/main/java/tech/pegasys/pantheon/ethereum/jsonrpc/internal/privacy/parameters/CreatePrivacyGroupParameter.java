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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.privacy.parameters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CreatePrivacyGroupParameter {

  private final String[] addresses;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final String name;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final String description;

  @JsonCreator
  public CreatePrivacyGroupParameter(
      @JsonProperty(value = "addresses", required = true) final String[] addresses,
      @JsonProperty("name") final String name,
      @JsonProperty("description") final String description) {
    this.addresses = addresses;
    this.name = name;
    this.description = description;
  }

  public String[] getAddresses() {
    return addresses;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }
}
