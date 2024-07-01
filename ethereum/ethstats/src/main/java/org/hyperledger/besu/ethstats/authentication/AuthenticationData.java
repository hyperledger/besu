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
 * This interface represents the authentication data. It provides methods to get the id, info, and
 * secret of the authentication data.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableAuthenticationData.class)
@JsonDeserialize(as = ImmutableAuthenticationData.class)
@Value.Style(allParameters = true)
public interface AuthenticationData {

  /**
   * Gets the id of the authentication data.
   *
   * @return the id of the authentication data.
   */
  @JsonProperty("id")
  String getId();

  /**
   * Gets the info of the authentication data.
   *
   * @return the info of the authentication data.
   */
  @JsonProperty("info")
  NodeInfo getInfo();

  /**
   * Gets the secret of the authentication data.
   *
   * @return the secret of the authentication data.
   */
  @JsonProperty("secret")
  String getSecret();
}
