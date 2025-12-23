/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.p2p.discovery.v5;

import org.hyperledger.besu.crypto.KeyPair;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Configuration container for DiscV5 discovery in Besu. */
public final class DiscoveryV5Config {

  public static final int DEFAULT_PORT = 30303;
  private static final Logger LOG = LoggerFactory.getLogger(DiscoveryV5Config.class);

  private final KeyPair nodeKey;
  private final InetSocketAddress udpBindAddress;
  private final Optional<InetSocketAddress> tcpAdvertised;
  private final List<String> bootEnrs;
  private final List<String> bindInterfaces;
  private final List<String> advertisedIps;
  private final boolean preferIpv6;

  private DiscoveryV5Config(final Builder b) {
    this.nodeKey = b.nodeKey;
    this.udpBindAddress = b.udpBindAddress;
    this.tcpAdvertised = Optional.ofNullable(b.tcpAdvertised);
    this.bootEnrs = List.copyOf(b.bootEnrs);
    this.bindInterfaces = List.copyOf(b.bindInterfaces);
    this.advertisedIps = List.copyOf(b.advertisedIps);
    this.preferIpv6 = b.preferIpv6;
  }

  public KeyPair nodeKey() {
    return nodeKey;
  }

  public InetSocketAddress udpBindAddress() {
    return udpBindAddress;
  }

  public Optional<InetSocketAddress> tcpAdvertised() {
    return tcpAdvertised;
  }

  public List<String> bootEnrs() {
    return bootEnrs;
  }

  public List<String> bindInterfaces() {
    return bindInterfaces;
  }

  public List<String> advertisedIps() {
    return advertisedIps;
  }

  public boolean preferIpv6() {
    return preferIpv6;
  }

  /** Replace 0.0.0.0/:: with best local address. */
  public List<String> resolvedAdvertisedIpsOrDefault() {
    if (advertisedIps.isEmpty()) return List.of();
    final List<String> out = new ArrayList<>(advertisedIps.size());
    for (String ip : advertisedIps) {
      out.add(resolveAnyLocalAddress(ip));
    }
    return out;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private KeyPair nodeKey;
    private InetSocketAddress udpBindAddress = new InetSocketAddress("0.0.0.0", DEFAULT_PORT);
    private InetSocketAddress tcpAdvertised;
    private List<String> bootEnrs = List.of();
    private List<String> bindInterfaces = List.of("0.0.0.0");
    private List<String> advertisedIps = List.of();
    private boolean preferIpv6 = false;

    public Builder() {}

    public Builder nodeKey(final KeyPair nodeKey) {
      this.nodeKey = nodeKey;
      return this;
    }

    public Builder udpBindAddress(final InetSocketAddress udpBindAddress) {
      this.udpBindAddress = udpBindAddress;
      return this;
    }

    public Builder tcpAdvertised(final InetSocketAddress tcpAdvertised) {
      this.tcpAdvertised = tcpAdvertised;
      return this;
    }

    public Builder bootEnrs(final List<String> bootEnrs) {
      this.bootEnrs = bootEnrs == null ? List.of() : bootEnrs;
      return this;
    }

    public Builder bindInterfaces(final List<String> bindInterfaces) {
      this.bindInterfaces = bindInterfaces == null ? List.of("0.0.0.0") : bindInterfaces;
      return this;
    }

    public Builder advertisedIps(final List<String> advertisedIps) {
      this.advertisedIps = advertisedIps == null ? List.of() : advertisedIps;
      return this;
    }

    public Builder preferIpv6(final boolean preferIpv6) {
      this.preferIpv6 = preferIpv6;
      return this;
    }

    public DiscoveryV5Config build() {
      if (nodeKey == null) throw new IllegalStateException("nodeKey is required");
      if (udpBindAddress == null) throw new IllegalStateException("udpBindAddress is required");
      return new DiscoveryV5Config(this);
    }
  }

  // ---- helpers ----

  private static boolean isAnyLocal(final InetAddress addr) {
    return addr.isAnyLocalAddress();
  }

  private static String resolveAnyLocalAddress(final String ipAddress) {
    try {
      final InetAddress in = InetAddress.getByName(ipAddress);
      if (!isAnyLocal(in)) return ipAddress;
      final boolean wantV6 = isIpv6Literal(ipAddress);
      final String found = pickLocalAddress(wantV6);
      return found != null ? found : ipAddress;
    } catch (final UnknownHostException e) {
      return ipAddress;
    }
  }

  private static boolean isIpv6Literal(final String s) {
    return s != null && s.contains(":");
  }

  private static String pickLocalAddress(final boolean ipv6) {
    try {
      final Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
      while (nics.hasMoreElements()) {
        final NetworkInterface nic = nics.nextElement();
        final Enumeration<InetAddress> addrs = nic.getInetAddresses();
        while (addrs.hasMoreElements()) {
          final InetAddress a = addrs.nextElement();
          final boolean isV6 = a.getAddress().length == 16;
          if (ipv6 != isV6) continue;
          if (a.isSiteLocalAddress() || (!a.isAnyLocalAddress() && !a.isLoopbackAddress())) {
            return a.getHostAddress();
          }
        }
      }
    } catch (SocketException e) {

      // Can happen on containers or when no interfaces are up; safe to ignore.
      // We log at debug to satisfy Error Prone and keep behavior.
      LOG.debug("Ignoring SocketException while enumerating network interfaces", e);
    }
    return null;
  }
}
