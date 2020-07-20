/*
 * Copyright (C) 2008 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.hyperledger.besu.ethstats.authentication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NodeInfo {

  @JsonProperty("name")
  private final String name;

  @JsonProperty("node")
  private final String node;

  @JsonProperty("port")
  private final String port;

  @JsonProperty("net")
  private final String network;

  @JsonProperty("protocol")
  private final String protocol;

  @JsonProperty("api")
  private final String api;

  @JsonProperty("os")
  private final String os;

  @JsonProperty("os_v")
  private final String osVer;

  @JsonProperty("client")
  private final String client;

  @JsonProperty("canUpdateHistory")
  private final Boolean canUpdateHistory;

  @JsonProperty("contact")
  private final String contact;

  @JsonCreator
  public NodeInfo(
      final String name,
      final String node,
      final String port,
      final String net,
      final String protocol,
      final String api,
      final String os,
      final String osVer,
      final String client,
      final Boolean canUpdateHistory,
      final String contact) {
    this.name = name;
    this.node = node;
    this.port = port;
    this.network = net;
    this.protocol = protocol;
    this.api = api;
    this.os = os;
    this.osVer = osVer;
    this.client = client;
    this.canUpdateHistory = canUpdateHistory;
    this.contact = contact;
  }

  public String getName() {
    return name;
  }

  public String getNode() {
    return node;
  }

  public String getPort() {
    return port;
  }

  public String getNetwork() {
    return network;
  }

  public String getProtocol() {
    return protocol;
  }

  public String getApi() {
    return api;
  }

  public String getOs() {
    return os;
  }

  public String getOsVer() {
    return osVer;
  }

  public String getClient() {
    return client;
  }

  public Boolean getCanUpdateHistory() {
    return canUpdateHistory;
  }

  public String getContact() {
    return contact;
  }
}
