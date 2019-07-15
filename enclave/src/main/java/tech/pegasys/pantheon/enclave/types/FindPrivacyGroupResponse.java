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
package tech.pegasys.pantheon.enclave.types;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FindPrivacyGroupResponse implements Serializable {

  private String privacyGroupId;
  private String name;
  private String description;
  private String[] members;

  @JsonCreator
  public FindPrivacyGroupResponse(
      @JsonProperty("privacyGroupId") final String privacyGroupId,
      @JsonProperty("name") final String name,
      @JsonProperty("description") final String description,
      @JsonProperty("members") final String[] members) {
    this.privacyGroupId = privacyGroupId;
    this.name = name;
    this.description = description;
    this.members = members;
  }

  @JsonProperty("privacyGroupId")
  public String privacyGroupId() {
    return privacyGroupId;
  }

  @JsonProperty("name")
  public String name() {
    return name;
  }

  @JsonProperty("description")
  public String description() {
    return description;
  }

  @JsonProperty("members")
  public String[] members() {
    return members;
  }

  public FindPrivacyGroupResponse() {}
}
