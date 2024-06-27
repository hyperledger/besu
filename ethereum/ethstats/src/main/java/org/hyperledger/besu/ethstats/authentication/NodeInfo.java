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
package org.hyperledger.besu.ethstats.authentication;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/**
 * This interface represents the information of a node. It provides methods to get the name, node,
 * port, network, protocol, api, os, os version, client, update history capability, and contact of
 * the node.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableNodeInfo.class)
@JsonDeserialize(as = ImmutableNodeInfo.class)
@Value.Style(allParameters = true)
public interface NodeInfo {

  /**
   * Gets the name of the node.
   *
   * @return the name of the node.
   */
  @JsonProperty("name")
  String getName();

  /**
   * Gets the node.
   *
   * @return the node.
   */
  @JsonProperty("node")
  String getNode();

  /**
   * Gets the port of the node.
   *
   * @return the port of the node.
   */
  @JsonProperty("port")
  String getPort();

  /**
   * Gets the network of the node.
   *
   * @return the network of the node.
   */
  @JsonProperty("net")
  String getNetwork();

  /**
   * Gets the protocol of the node.
   *
   * @return the protocol of the node.
   */
  @JsonProperty("protocol")
  String getProtocol();

  /**
   * Gets the api of the node.
   *
   * @return the api of the node.
   */
  @JsonProperty("api")
  String getApi();

  /**
   * Gets the operating system of the node.
   *
   * @return the operating system of the node.
   */
  @JsonProperty("os")
  String getOs();

  /**
   * Gets the operating system version of the node.
   *
   * @return the operating system version of the node.
   */
  @JsonProperty("os_v")
  String getOsVer();

  /**
   * Gets the client of the node.
   *
   * @return the client of the node.
   */
  @JsonProperty("client")
  String getClient();

  /**
   * Gets the update history capability of the node.
   *
   * @return the update history capability of the node.
   */
  @JsonProperty("canUpdateHistory")
  Boolean getCanUpdateHistory();

  /**
   * Gets the contact of the node.
   *
   * @return the contact of the node.
   */
  @JsonProperty("contact")
  String getContact();
}
