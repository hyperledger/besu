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

import static org.hyperledger.besu.ethereum.p2p.config.AutoDiscoverDefaultIP.autoDiscoverDefaultIP;
import static org.hyperledger.besu.ethereum.p2p.config.RlpxConfiguration.DEFAULT_FRACTION_REMOTE_CONNECTIONS_ALLOWED;

import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.util.NetworkUtility;
import org.hyperledger.besu.util.number.Fraction;
import org.hyperledger.besu.util.number.Percentage;

import java.util.List;
import java.util.Objects;

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
  private final List<Bytes> bannedNodeIds;
  private final List<SubnetInfo> allowedSubnets;

  private P2PConfiguration(
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
      final List<Bytes> bannedNodeIds,
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

  public String getDiscoveryDnsUrl() {
    return discoveryDnsUrl;
  }

  public boolean isRandomPeerPriority() {
    return randomPeerPriority;
  }

  public List<Bytes> getBannedNodeIds() {
    return bannedNodeIds;
  }

  public List<SubnetInfo> getAllowedSubnets() {
    return allowedSubnets;
  }

  public int getMaxRemoteInitiatedPeers() {
    if (isLimitRemoteWireConnectionsEnabled) {
      final float fraction = Fraction.fromPercentage(maxRemoteConnectionsPercentage).getValue();
      return Math.round(fraction * maxPeers);
    }
    return maxPeers;
  }

  public static P2PConfiguration createDefault() {
    return new Builder()
        .p2pEnabled(true)
        .discoveryEnabled(true)
        .host(autoDiscoverDefaultIP().getHostAddress())
        .p2pInterface(NetworkUtility.INADDR_ANY)
        .port(EnodeURLImpl.DEFAULT_LISTENING_PORT)
        .maxPeers(25)
        .maxRemoteConnectionsPercentage(
            Fraction.fromFloat(DEFAULT_FRACTION_REMOTE_CONNECTIONS_ALLOWED).toPercentage())
        .isLimitRemoteWireConnectionsEnabled(true)
        .build();
  }

  public static P2PConfiguration.Builder builder() {
    return new P2PConfiguration.Builder();
  }

  public static class Builder {
    private boolean p2pEnabled = true;
    private boolean discoveryEnabled = true;
    private List<String> bootNodes;
    private String host;
    private String p2pInterface = NetworkUtility.INADDR_ANY;
    private int p2pPort = EnodeURLImpl.DEFAULT_LISTENING_PORT;
    private int maxPeers;
    private boolean isLimitRemoteWireConnectionsEnabled = true;
    private Percentage maxRemoteConnectionsPercentage =
        Fraction.fromFloat(DEFAULT_FRACTION_REMOTE_CONNECTIONS_ALLOWED).toPercentage();
    private String discoveryDnsUrl;
    private boolean randomPeerPriority = false;
    private List<Bytes> bannedNodeIds = List.of();
    private List<SubnetInfo> allowedSubnets;

    public Builder p2pEnabled(final boolean p2pEnabled) {
      this.p2pEnabled = p2pEnabled;
      return this;
    }

    public Builder discoveryEnabled(final boolean discoveryEnabled) {
      this.discoveryEnabled = discoveryEnabled;
      return this;
    }

    public Builder bootNodes(final List<String> bootNodes) {
      this.bootNodes = bootNodes;
      return this;
    }

    public Builder host(final String host) {
      this.host = host;
      return this;
    }

    public Builder p2pInterface(final String p2pInterface) {
      this.p2pInterface = p2pInterface;
      return this;
    }

    public Builder port(final int listeningPort) {
      this.p2pPort = listeningPort;
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

    public Builder bannedNodeIds(final List<Bytes> bannedNodeIds) {
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
          discoveryEnabled,
          bootNodes,
          host,
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

  @Override
  public String toString() {
    return "P2PConfiguration{"
        + "p2pEnabled="
        + p2pEnabled
        + ", discoveryEnabled="
        + discoveryEnabled
        + ", bootNodes="
        + bootNodes
        + ", host='"
        + host
        + '\''
        + ", p2pInterface='"
        + p2pInterface
        + '\''
        + ", port="
        + port
        + ", maxPeers="
        + maxPeers
        + ", isLimitRemoteWireConnectionsEnabled="
        + isLimitRemoteWireConnectionsEnabled
        + ", maxRemoteConnectionsPercentage="
        + maxRemoteConnectionsPercentage
        + ", discoveryDnsUrl='"
        + discoveryDnsUrl
        + '\''
        + ", randomPeerPriority="
        + randomPeerPriority
        + ", bannedNodeIds="
        + bannedNodeIds
        + ", allowedSubnets="
        + allowedSubnets
        + '}';
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        p2pEnabled,
        discoveryEnabled,
        bootNodes,
        host,
        p2pInterface,
        port,
        maxPeers,
        isLimitRemoteWireConnectionsEnabled,
        maxRemoteConnectionsPercentage,
        discoveryDnsUrl,
        randomPeerPriority,
        bannedNodeIds,
        allowedSubnets);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    P2PConfiguration that = (P2PConfiguration) o;
    return p2pEnabled == that.p2pEnabled
        && discoveryEnabled == that.discoveryEnabled
        && port == that.port
        && maxPeers == that.maxPeers
        && isLimitRemoteWireConnectionsEnabled == that.isLimitRemoteWireConnectionsEnabled
        && randomPeerPriority == that.randomPeerPriority
        && Objects.equals(bootNodes, that.bootNodes)
        && Objects.equals(host, that.host)
        && Objects.equals(p2pInterface, that.p2pInterface)
        && Objects.equals(maxRemoteConnectionsPercentage, that.maxRemoteConnectionsPercentage)
        && Objects.equals(discoveryDnsUrl, that.discoveryDnsUrl)
        && Objects.equals(bannedNodeIds, that.bannedNodeIds)
        && Objects.equals(allowedSubnets, that.allowedSubnets);
  }
}
