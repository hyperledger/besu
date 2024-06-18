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
package org.hyperledger.besu.ethereum.p2p.config;

import org.hyperledger.besu.util.number.Percentage;

import java.util.Collection;
import java.util.List;

import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.apache.tuweni.bytes.Bytes;

public class P2PConfiguration {
  // Public IP stored to prevent having to research it each time we need it.
  private final boolean p2pEnabled;
  private final boolean discoveryEnabled;
  private final List<String> bootNodes;
  private final String host;
  private final String p2pInterface;
  private final int port;
  private final int maxPeers;
  private final boolean isLimitRemoteWireConnectionsEnabled;
  private final Percentage maxRemoteConnectionsPercentage;
  private final String discoveryDnsUrl;
  private final boolean randomPeerPriority;
  private final Collection<Bytes> bannedNodeIds;
  private final List<SubnetInfo> allowedSubnets;

  public P2PConfiguration(
      final boolean p2pEnabled,
      final boolean peerDiscoveryEnabled,
      final List<String> bootNodes,
      final String host,
      final String p2pInterface,
      final int p2pPort,
      final int maxPeers,
      final boolean isLimitRemoteWireConnectionsEnabled,
      final Percentage maxRemoteConnectionsPercentage,
      final String discoveryDnsUrl,
      final boolean randomPeerPriority,
      final Collection<Bytes> bannedNodeIds,
      final List<SubnetInfo> allowedSubnets) {
    this.p2pEnabled = p2pEnabled;
    this.discoveryEnabled = peerDiscoveryEnabled;
    this.bootNodes = bootNodes;
    this.host = host;
    this.p2pInterface = p2pInterface;
    this.port = p2pPort;
    this.maxPeers = maxPeers;
    this.isLimitRemoteWireConnectionsEnabled = isLimitRemoteWireConnectionsEnabled;
    this.maxRemoteConnectionsPercentage = maxRemoteConnectionsPercentage;
    this.discoveryDnsUrl = discoveryDnsUrl;
    this.randomPeerPriority = randomPeerPriority;
    this.bannedNodeIds = bannedNodeIds;
    this.allowedSubnets = allowedSubnets;
  }

  public boolean isP2pEnabled() {
    return p2pEnabled;
  }

  public boolean isDiscoveryEnabled() {
    return discoveryEnabled;
  }

  public List<String> getBootNodes() {
    return bootNodes;
  }

  public String getHost() {
    return host;
  }

  public String getP2pInterface() {
    return p2pInterface;
  }

  public int getPort() {
    return port;
  }

  public int getMaxPeers() {
    return maxPeers;
  }

  public boolean isLimitRemoteWireConnectionsEnabled() {
    return isLimitRemoteWireConnectionsEnabled;
  }

  public Percentage getMaxRemoteConnectionsPercentage() {
    return maxRemoteConnectionsPercentage;
  }

  public String getDiscoveryDnsUrl() {
    return discoveryDnsUrl;
  }

  public boolean isRandomPeerPriority() {
    return randomPeerPriority;
  }

  public Collection<Bytes> getBannedNodeIds() {
    return bannedNodeIds;
  }

  public List<SubnetInfo> getAllowedSubnets() {
    return allowedSubnets;
  }

  public static P2PConfiguration.Builder builder() {
    return new P2PConfiguration.Builder();
  }

  public static class Builder {
    private boolean p2pEnabled = true;
    private boolean peerDiscoveryEnabled = true;
    private List<String> bootNodes;
    private String p2pHost;
    private String p2pInterface;
    private int p2pPort;
    private int maxPeers;
    private boolean isLimitRemoteWireConnectionsEnabled = true;
    private Percentage maxRemoteConnectionsPercentage;
    private String discoveryDnsUrl;
    private boolean randomPeerPriority = false;
    private Collection<Bytes> bannedNodeIds;
    private List<SubnetInfo> allowedSubnets;

    public Builder p2pEnabled(final boolean p2pEnabled) {
      this.p2pEnabled = p2pEnabled;
      return this;
    }

    public Builder peerDiscoveryEnabled(final boolean peerDiscoveryEnabled) {
      this.peerDiscoveryEnabled = peerDiscoveryEnabled;
      return this;
    }

    public Builder bootNodes(final List<String> bootNodes) {
      this.bootNodes = bootNodes;
      return this;
    }

    public Builder p2pHost(final String p2pHost) {
      this.p2pHost = p2pHost;
      return this;
    }

    public Builder p2pInterface(final String p2pInterface) {
      this.p2pInterface = p2pInterface;
      return this;
    }

    public Builder p2pPort(final int p2pPort) {
      this.p2pPort = p2pPort;
      return this;
    }

    public Builder maxPeers(final int maxPeers) {
      this.maxPeers = maxPeers;
      return this;
    }

    public Builder isLimitRemoteWireConnectionsEnabled(
        final boolean isLimitRemoteWireConnectionsEnabled) {
      this.isLimitRemoteWireConnectionsEnabled = isLimitRemoteWireConnectionsEnabled;
      return this;
    }

    public Builder maxRemoteConnectionsPercentage(final Percentage maxRemoteConnectionsPercentage) {
      this.maxRemoteConnectionsPercentage = maxRemoteConnectionsPercentage;
      return this;
    }

    public Builder discoveryDnsUrl(final String discoveryDnsUrl) {
      this.discoveryDnsUrl = discoveryDnsUrl;
      return this;
    }

    public Builder randomPeerPriority(final boolean randomPeerPriority) {
      this.randomPeerPriority = randomPeerPriority;
      return this;
    }

    public Builder bannedNodeIds(final Collection<Bytes> bannedNodeIds) {
      this.bannedNodeIds = bannedNodeIds;
      return this;
    }

    public Builder allowedSubnets(final List<SubnetInfo> allowedSubnets) {
      this.allowedSubnets = allowedSubnets;
      return this;
    }

    public P2PConfiguration build() {
      return new P2PConfiguration(
          p2pEnabled,
          peerDiscoveryEnabled,
          bootNodes,
          p2pHost,
          p2pInterface,
          p2pPort,
          maxPeers,
          isLimitRemoteWireConnectionsEnabled,
          maxRemoteConnectionsPercentage,
          discoveryDnsUrl,
          randomPeerPriority,
          bannedNodeIds,
          allowedSubnets);
    }
  }
}
