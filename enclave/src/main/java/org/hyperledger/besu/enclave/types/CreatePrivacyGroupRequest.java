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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The Create privacy group request. */
public class CreatePrivacyGroupRequest {

  private final List<String> addresses;
  private final String from;
  private final String name;
  private final String description;

  /**
   * Instantiates a new Create privacy group request.
   *
   * @param addresses the addresses
   * @param from the from
   * @param name the name
   * @param description the description
   */
  @JsonCreator
  public CreatePrivacyGroupRequest(
      @JsonProperty("addresses") final List<String> addresses,
      @JsonProperty("from") final String from,
      @JsonProperty("name") final String name,
      @JsonProperty("description") final String description) {
    this.addresses = addresses;
    this.from = from;
    this.name = name;
    this.description = description;
  }

  /**
   * Addresses list.
   *
   * @return the list
   */
  @JsonProperty("addresses")
  public List<String> addresses() {
    return addresses;
  }

  /**
   * From.
   *
   * @return the string
   */
  @JsonProperty("from")
  public String from() {
    return from;
  }

  /**
   * Name.
   *
   * @return the string
   */
  @JsonProperty("name")
  public String name() {
    return name;
  }

  /**
   * Description.
   *
   * @return the string
   */
  @JsonProperty("description")
  public String description() {
    return description;
  }
}
