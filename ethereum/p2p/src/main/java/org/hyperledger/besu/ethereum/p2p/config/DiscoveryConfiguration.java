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
package org.hyperledger.besu.ethereum.p2p.config;

import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.util.NetworkUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DiscoveryConfiguration {

  private boolean enabled = true;
  private String bindHost = NetworkUtility.INADDR_ANY;
  private int bindPort = 30303;
  private String advertisedHost = "127.0.0.1";
  private int bucketSize = 16;
  private List<EnodeURL> bootnodes = new ArrayList<>();
  private String dnsDiscoveryURL;
  private boolean discoveryV5Enabled = false;
  private boolean filterOnEnrForkId = NetworkingConfiguration.DEFAULT_FILTER_ON_ENR_FORK_ID;
  private boolean includeBootnodesOnPeerRefresh = true;

  public static DiscoveryConfiguration create() {
    return new DiscoveryConfiguration();
  }

  public static void assertValidBootnodes(final List<EnodeURL> bootnodes) {
    final List<EnodeURL> invalidEnodes =
        bootnodes.stream().filter(e -> !e.isRunningDiscovery()).collect(Collectors.toList());

    if (invalidEnodes.size() > 0) {
      final String invalidBootnodes =
          invalidEnodes.stream().map(EnodeURL::toString).collect(Collectors.joining(","));
      final String errorMsg =
          "Bootnodes must have discovery enabled. Invalid bootnodes: " + invalidBootnodes + ".";
      throw new IllegalArgumentException(errorMsg);
    }
  }

  public String getBindHost() {
    return bindHost;
  }

  public DiscoveryConfiguration setBindHost(final String bindHost) {
    this.bindHost = bindHost;
    return this;
  }

  public int getBindPort() {
    return bindPort;
  }

  public DiscoveryConfiguration setBindPort(final int bindPort) {
    this.bindPort = bindPort;
    return this;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public DiscoveryConfiguration setEnabled(final boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public List<EnodeURL> getBootnodes() {
    return bootnodes;
  }

  public DiscoveryConfiguration setBootnodes(final List<EnodeURL> bootnodes) {
    assertValidBootnodes(bootnodes);
    this.bootnodes = bootnodes;
    return this;
  }

  public boolean getIncludeBootnodesOnPeerRefresh() {
    return includeBootnodesOnPeerRefresh;
  }

  public DiscoveryConfiguration setIncludeBootnodesOnPeerRefresh(
      final boolean includeBootnodesOnPeerRefresh) {
    this.includeBootnodesOnPeerRefresh = includeBootnodesOnPeerRefresh;
    return this;
  }

  public String getAdvertisedHost() {
    return advertisedHost;
  }

  public DiscoveryConfiguration setAdvertisedHost(final String advertisedHost) {
    this.advertisedHost = advertisedHost;
    return this;
  }

  public int getBucketSize() {
    return bucketSize;
  }

  public DiscoveryConfiguration setBucketSize(final int bucketSize) {
    this.bucketSize = bucketSize;
    return this;
  }

  public String getDNSDiscoveryURL() {
    return dnsDiscoveryURL;
  }

  public DiscoveryConfiguration setDnsDiscoveryURL(final String dnsDiscoveryURL) {
    this.dnsDiscoveryURL = dnsDiscoveryURL;
    return this;
  }

  public void setDiscoveryV5Enabled(final boolean discoveryV5Enabled) {
    this.discoveryV5Enabled = discoveryV5Enabled;
  }

  public boolean isDiscoveryV5Enabled() {
    return discoveryV5Enabled;
  }

  public void setFilterOnEnrForkId(final boolean filterOnEnrForkId) {
    this.filterOnEnrForkId = filterOnEnrForkId;
  }

  public boolean isFilterOnEnrForkIdEnabled() {
    return filterOnEnrForkId;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DiscoveryConfiguration)) {
      return false;
    }
    final DiscoveryConfiguration that = (DiscoveryConfiguration) o;
    return enabled == that.enabled
        && bindPort == that.bindPort
        && bucketSize == that.bucketSize
        && Objects.equals(bindHost, that.bindHost)
        && Objects.equals(advertisedHost, that.advertisedHost)
        && Objects.equals(bootnodes, that.bootnodes)
        && Objects.equals(dnsDiscoveryURL, that.dnsDiscoveryURL);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        enabled, bindHost, bindPort, advertisedHost, bucketSize, bootnodes, dnsDiscoveryURL);
  }

  @Override
  public String toString() {
    return "DiscoveryConfiguration{"
        + "enabled="
        + enabled
        + ", bindHost='"
        + bindHost
        + '\''
        + ", bindPort="
        + bindPort
        + ", advertisedHost='"
        + advertisedHost
        + '\''
        + ", bucketSize="
        + bucketSize
        + ", bootnodes="
        + bootnodes
        + ", dnsDiscoveryURL="
        + dnsDiscoveryURL
        + ", isDiscoveryV5Enabled="
        + discoveryV5Enabled
        + ", isFilterOnEnrForkIdEnabled="
        + filterOnEnrForkId
        + '}';
  }
}
