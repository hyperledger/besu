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
package org.hyperledger.besu.nat.core.domain;

/**
 * This enum describes the types of services that could be impacted by the {@link
 * org.hyperledger.besu.nat.NatMethod}* used by the Besu node.
 *
 * <ul>
 *   <li><b>JSON_RPC:</b> Ethereum JSON-RPC HTTP service.
 *   <li><b>RLPX:</b> Peer to Peer network layer.
 *   <li><b>DISCOVERY:</b> Peer to Peer discovery layer.
 * </ul>
 */
public enum NatServiceType {
  /** Json rpc nat service type. */
  JSON_RPC("json-rpc"),
  /** Rlpx nat service type. */
  RLPX("rlpx"),
  /** Discovery nat service type. */
  DISCOVERY("discovery");

  private final String value;

  NatServiceType(final String value) {
    this.value = value;
  }

  /**
   * Parses and returns corresponding enum value to the passed method name. This method throws an
   * {@link IllegalStateException} if the method name is invalid.
   *
   * @param natServiceTypeName The name of the NAT service type.
   * @return The corresponding {@link NatServiceType}
   */
  public static NatServiceType fromString(final String natServiceTypeName) {
    for (final NatServiceType mode : NatServiceType.values()) {
      if (mode.getValue().equalsIgnoreCase(natServiceTypeName)) {
        return mode;
      }
    }
    throw new IllegalStateException(
        String.format("Invalid NAT service type provided: %s", natServiceTypeName));
  }

  /**
   * Gets value.
   *
   * @return the value
   */
  public String getValue() {
    return value;
  }
}
