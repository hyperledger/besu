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
package org.hyperledger.besu.ethereum.p2p.config;

/**
 * Defines preferences for IP version selection when connecting to peers that advertise multiple IP
 * addresses (both IPv4 and IPv6).
 *
 * <p>Note: This preference is independent of local binding configuration (what interfaces the local
 * node listens on). This controls which remote peer address to use for outbound connections.
 */
public enum IpVersionPreference {
  /**
   * Always prefer IPv4 addresses for outbound connections. Only use IPv6 if IPv4 is not available.
   */
  IPV4_PREFERRED,

  /**
   * Always prefer IPv6 addresses for outbound connections. Only use IPv4 if IPv6 is not available.
   */
  IPV6_PREFERRED,

  /** Only use IPv4 addresses for outbound connections, never use IPv6. */
  IPV4_ONLY,

  /** Only use IPv6 addresses for outbound connections, never use IPv4. */
  IPV6_ONLY;

  /**
   * Determines whether to use IPv6 for a peer based on this preference.
   *
   * @param hasIpv4 whether the peer advertises an IPv4 address
   * @param hasIpv6 whether the peer advertises an IPv6 address
   * @return true if IPv6 should be used, false if IPv4 should be used
   * @throws IllegalStateException if the preference cannot be satisfied (e.g., IPV6_ONLY but peer
   *     only has IPv4)
   */
  public boolean shouldUseIpv6(final boolean hasIpv4, final boolean hasIpv6) {
    return switch (this) {
      case IPV4_ONLY -> {
        if (!hasIpv4) {
          throw new IllegalStateException(
              "IPV4_ONLY preference set but peer does not advertise IPv4 address");
        }
        yield false;
      }
      case IPV6_ONLY -> {
        if (!hasIpv6) {
          throw new IllegalStateException(
              "IPV6_ONLY preference set but peer does not advertise IPv6 address");
        }
        yield true;
      }
      case IPV4_PREFERRED -> hasIpv6 && !hasIpv4; // Only use IPv6 if IPv4 is unavailable
      case IPV6_PREFERRED -> hasIpv6; // Use IPv6 if available, fallback to IPv4
    };
  }
}
