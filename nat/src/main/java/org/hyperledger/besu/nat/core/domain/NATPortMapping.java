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

/** This class describes a NAT configuration. */
public class NATPortMapping {

  private final NATServiceType natServiceType;
  private final NetworkProtocol protocol;
  private final String internalHost;
  private final String remoteHost;
  private final int externalPort;
  private final int internalPort;

  public NATPortMapping(
      final NATServiceType natServiceType,
      final NetworkProtocol protocol,
      final String internalHost,
      final String remoteHost,
      final int externalPort,
      final int internalPort) {
    this.natServiceType = natServiceType;
    this.protocol = protocol;
    this.internalHost = internalHost;
    this.remoteHost = remoteHost;
    this.externalPort = externalPort;
    this.internalPort = internalPort;
  }

  /**
   * Builds an empty representation of a {@link NATPortMapping}
   *
   * @param protocol the {@link NetworkProtocol} associated to the representation.
   * @return the built {@link NATPortMapping}
   */
  public static NATPortMapping emptyPortMapping(final NetworkProtocol protocol) {
    return new NATPortMapping(null, protocol, "", "", 0, 0);
  }

  public NATServiceType getNatServiceType() {
    return natServiceType;
  }

  public NetworkProtocol getProtocol() {
    return protocol;
  }

  public String getInternalHost() {
    return internalHost;
  }

  public String getRemoteHost() {
    return remoteHost;
  }

  public int getExternalPort() {
    return externalPort;
  }

  public int getInternalPort() {
    return internalPort;
  }

  @Override
  public String toString() {
    return String.format(
        "[%s - %s] %s:%d ==> %s:%d",
        natServiceType, protocol, internalHost, internalPort, remoteHost, externalPort);
  }
}
