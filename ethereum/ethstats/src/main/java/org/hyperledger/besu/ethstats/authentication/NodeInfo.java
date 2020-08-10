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

@Value.Immutable
@JsonSerialize(as = ImmutableNodeInfo.class)
@JsonDeserialize(as = ImmutableNodeInfo.class)
@Value.Style(allParameters = true)
public interface NodeInfo {

  @JsonProperty("name")
  String getName();

  @JsonProperty("node")
  String getNode();

  @JsonProperty("port")
  String getPort();

  @JsonProperty("net")
  String getNetwork();

  @JsonProperty("protocol")
  String getProtocol();

  @JsonProperty("api")
  String getApi();

  @JsonProperty("os")
  String getOs();

  @JsonProperty("os_v")
  String getOsVer();

  @JsonProperty("client")
  String getClient();

  @JsonProperty("canUpdateHistory")
  Boolean getCanUpdateHistory();

  @JsonProperty("contact")
  String getContact();
}
