/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.p2p.discovery;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.util.NetworkUtility;

/**
 * Groups the network endpoint information (host address and ports) for a discovery node.
 *
 * @param host the advertised host or IP address
 * @param discoveryPort the UDP discovery port
 * @param tcpPort the TCP listening port
 * @param isIpv4 {@code true} if the host address is IPv4, {@code false} if IPv6
 */
public record HostEndpoint(String host, int discoveryPort, int tcpPort, boolean isIpv4) {

  /**
   * Convenience constructor that derives {@code isIpv4} from the host address.
   *
   * @param host the advertised host or IP address
   * @param discoveryPort the UDP discovery port
   * @param tcpPort the TCP listening port
   */
  public HostEndpoint(final String host, final int discoveryPort, final int tcpPort) {
    this(host, discoveryPort, tcpPort, NetworkUtility.isIpV4Address(host));
    // host is already meant to be filtered at cli validation level to be an IP address
    checkArgument(
        NetworkUtility.isIpV4Address(host) || NetworkUtility.isIpV6Address(host),
        "host must be an IP address, not a hostname: %s",
        host);
  }
}
