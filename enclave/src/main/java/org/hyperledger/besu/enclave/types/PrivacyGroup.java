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

import java.io.Serializable;
import java.util.List;

/** The Privacy group. */
public class PrivacyGroup implements Serializable {

  /** Private Group Id */
  private String privacyGroupId;

  /** Name */
  private String name;

  /** Description */
  private String description;

  /** Type */
  private Type type;

  /** Members */
  private List<String> members;

  /**
   * Gets privacy group id.
   *
   * @return the privacy group id
   */
  public String getPrivacyGroupId() {
    return privacyGroupId;
  }

  /**
   * Sets privacy group id.
   *
   * @param privacyGroupId the privacy group id
   */
  public void setPrivacyGroupId(final String privacyGroupId) {
    this.privacyGroupId = privacyGroupId;
  }

  /**
   * Gets name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets name.
   *
   * @param name the name
   */
  public void setName(final String name) {
    this.name = name;
  }

  /**
   * Gets description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets description.
   *
   * @param description the description
   */
  public void setDescription(final String description) {
    this.description = description;
  }

  /**
   * Gets type.
   *
   * @return the type
   */
  public Type getType() {
    return type;
  }

  /**
   * Sets type.
   *
   * @param type the type
   */
  public void setType(final Type type) {
    this.type = type;
  }

  /**
   * Gets members.
   *
   * @return the members
   */
  public List<String> getMembers() {
    return members;
  }

  /**
   * Sets members.
   *
   * @param members the members
   */
  public void setMembers(final List<String> members) {
    this.members = members;
  }

  /**
   * Add members.
   *
   * @param participantsFromParameter the participants from parameter
   */
  public void addMembers(final List<String> participantsFromParameter) {
    members.addAll(participantsFromParameter);
  }

  /** Instantiates a new Privacy group. */
  public PrivacyGroup() {}

  /**
   * Instantiates a new Privacy group.
   *
   * @param privacyGroupId the privacy group id
   * @param type the type
   * @param name the name
   * @param description the description
   * @param members the members
   */
  public PrivacyGroup(
      final String privacyGroupId,
      final Type type,
      final String name,
      final String description,
      final List<String> members) {
    this.privacyGroupId = privacyGroupId;
    this.type = type;
    this.name = name;
    this.description = description;
    this.members = members;
  }

  /** The enum Type. */
  public enum Type {
    /** Legacy type. */
    LEGACY,
    /** Flexible type. */
    FLEXIBLE,
    /** Pantheon type. */
    PANTHEON
  }
}
