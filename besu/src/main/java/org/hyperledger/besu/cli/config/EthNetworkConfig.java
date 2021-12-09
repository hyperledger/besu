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
package org.hyperledger.besu.cli.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.ASTOR_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.CLASSIC_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.GOERLI_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.GOERLI_DISCOVERY_URL;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.KOTTI_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.MAINNET_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.MAINNET_DISCOVERY_URL;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.MORDOR_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.RINKEBY_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.RINKEBY_DISCOVERY_URL;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.ROPSTEN_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.ROPSTEN_DISCOVERY_URL;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.SEPOLIA_BOOTSTRAP_NODES;

import org.hyperledger.besu.plugin.data.EnodeURL;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Preconditions;
import com.google.common.io.Resources;

public class EthNetworkConfig {
  public static final BigInteger MAINNET_NETWORK_ID = BigInteger.valueOf(1);
  public static final BigInteger ROPSTEN_NETWORK_ID = BigInteger.valueOf(3);
  public static final BigInteger RINKEBY_NETWORK_ID = BigInteger.valueOf(4);
  public static final BigInteger GOERLI_NETWORK_ID = BigInteger.valueOf(5);
  public static final BigInteger SEPOLIA_NETWORK_ID = BigInteger.valueOf(11155111);
  public static final BigInteger DEV_NETWORK_ID = BigInteger.valueOf(2018);
  public static final BigInteger ECIP1049_DEV_NETWORK_ID = BigInteger.valueOf(2021);
  public static final BigInteger CLASSIC_NETWORK_ID = BigInteger.valueOf(1);
  public static final BigInteger KOTTI_NETWORK_ID = BigInteger.valueOf(6);
  public static final BigInteger MORDOR_NETWORK_ID = BigInteger.valueOf(7);
  private static final BigInteger ASTOR_NETWORK_ID = BigInteger.valueOf(212);
  private static final String MAINNET_GENESIS = "/mainnet.json";
  private static final String ROPSTEN_GENESIS = "/ropsten.json";
  private static final String RINKEBY_GENESIS = "/rinkeby.json";
  private static final String GOERLI_GENESIS = "/goerli.json";
  private static final String SEPOLIA_GENESIS = "/sepolia.json";
  private static final String DEV_GENESIS = "/dev.json";
  private static final String PREMERGE_GENESIS = "/preMerge.json";
  private static final String DEV_ECIP1049_GENESIS = "/ecip1049_dev.json";
  private static final String CLASSIC_GENESIS = "/classic.json";
  private static final String KOTTI_GENESIS = "/kotti.json";
  private static final String MORDOR_GENESIS = "/mordor.json";
  private static final String ASTOR_GENESIS = "/astor.json";

  private final String genesisConfig;
  private final BigInteger networkId;
  private final List<EnodeURL> bootNodes;
  private final String dnsDiscoveryUrl;

  public EthNetworkConfig(
      final String genesisConfig,
      final BigInteger networkId,
      final List<EnodeURL> bootNodes,
      final String dnsDiscoveryUrl) {
    Preconditions.checkNotNull(genesisConfig);
    Preconditions.checkNotNull(bootNodes);
    this.genesisConfig = genesisConfig;
    this.networkId = networkId;
    this.bootNodes = bootNodes;
    this.dnsDiscoveryUrl = dnsDiscoveryUrl;
  }

  public String getGenesisConfig() {
    return genesisConfig;
  }

  public BigInteger getNetworkId() {
    return networkId;
  }

  public List<EnodeURL> getBootNodes() {
    return bootNodes;
  }

  public String getDnsDiscoveryUrl() {
    return dnsDiscoveryUrl;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EthNetworkConfig that = (EthNetworkConfig) o;
    return networkId.equals(that.networkId)
        && Objects.equals(genesisConfig, that.genesisConfig)
        && Objects.equals(bootNodes, that.bootNodes)
        && Objects.equals(dnsDiscoveryUrl, that.dnsDiscoveryUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(genesisConfig, networkId, bootNodes, dnsDiscoveryUrl);
  }

  @Override
  public String toString() {
    return "EthNetworkConfig{"
        + "genesisConfig="
        + genesisConfig
        + ", networkId="
        + networkId
        + ", bootNodes="
        + bootNodes
        + ", dnsDiscoveryUrl="
        + dnsDiscoveryUrl
        + '}';
  }

  public static EthNetworkConfig getNetworkConfig(final NetworkName networkName) {
    switch (networkName) {
      case ROPSTEN:
        return new EthNetworkConfig(
            jsonConfig(ROPSTEN_GENESIS),
            ROPSTEN_NETWORK_ID,
            ROPSTEN_BOOTSTRAP_NODES,
            ROPSTEN_DISCOVERY_URL);
      case RINKEBY:
        return new EthNetworkConfig(
            jsonConfig(RINKEBY_GENESIS),
            RINKEBY_NETWORK_ID,
            RINKEBY_BOOTSTRAP_NODES,
            RINKEBY_DISCOVERY_URL);
      case GOERLI:
        return new EthNetworkConfig(
            jsonConfig(GOERLI_GENESIS),
            GOERLI_NETWORK_ID,
            GOERLI_BOOTSTRAP_NODES,
            GOERLI_DISCOVERY_URL);
      case SEPOLIA:
        return new EthNetworkConfig(
            jsonConfig(SEPOLIA_GENESIS), SEPOLIA_NETWORK_ID, SEPOLIA_BOOTSTRAP_NODES, null);
      case DEV:
        return new EthNetworkConfig(
            jsonConfig(DEV_GENESIS), DEV_NETWORK_ID, new ArrayList<>(), null);
      case PREMERGE:
        return new EthNetworkConfig(
            jsonConfig(PREMERGE_GENESIS), DEV_NETWORK_ID, new ArrayList<>(), null);
      case CLASSIC:
        return new EthNetworkConfig(
            jsonConfig(CLASSIC_GENESIS), CLASSIC_NETWORK_ID, CLASSIC_BOOTSTRAP_NODES, null);
      case ECIP1049_DEV:
        return new EthNetworkConfig(
            jsonConfig(DEV_ECIP1049_GENESIS), ECIP1049_DEV_NETWORK_ID, new ArrayList<>(), null);
      case KOTTI:
        return new EthNetworkConfig(
            jsonConfig(KOTTI_GENESIS), KOTTI_NETWORK_ID, KOTTI_BOOTSTRAP_NODES, null);
      case MORDOR:
        return new EthNetworkConfig(
            jsonConfig(MORDOR_GENESIS), MORDOR_NETWORK_ID, MORDOR_BOOTSTRAP_NODES, null);
      case ASTOR:
        return new EthNetworkConfig(
            jsonConfig(ASTOR_GENESIS), ASTOR_NETWORK_ID, ASTOR_BOOTSTRAP_NODES, null);
      case MAINNET:
      default:
        return new EthNetworkConfig(
            jsonConfig(MAINNET_GENESIS),
            MAINNET_NETWORK_ID,
            MAINNET_BOOTSTRAP_NODES,
            MAINNET_DISCOVERY_URL);
    }
  }

  private static String jsonConfig(final String resourceName) {
    try {
      final URI uri = EthNetworkConfig.class.getResource(resourceName).toURI();
      return Resources.toString(uri.toURL(), UTF_8);
    } catch (final URISyntaxException | IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String jsonConfig(final NetworkName network) {
    switch (network) {
      case MAINNET:
        return jsonConfig(MAINNET_GENESIS);
      case ROPSTEN:
        return jsonConfig(ROPSTEN_GENESIS);
      case RINKEBY:
        return jsonConfig(RINKEBY_GENESIS);
      case GOERLI:
        return jsonConfig(GOERLI_GENESIS);
      case DEV:
        return jsonConfig(DEV_GENESIS);
      case PREMERGE:
        return jsonConfig(PREMERGE_GENESIS);
      case ECIP1049_DEV:
        return jsonConfig(DEV_ECIP1049_GENESIS);
      case CLASSIC:
        return jsonConfig(CLASSIC_GENESIS);
      case KOTTI:
        return jsonConfig(KOTTI_GENESIS);
      case MORDOR:
        return jsonConfig(MORDOR_GENESIS);
      default:
        throw new IllegalArgumentException("Unknown network:" + network);
    }
  }

  public static class Builder {

    private String dnsDiscoveryUrl;
    private String genesisConfig;
    private BigInteger networkId;
    private List<EnodeURL> bootNodes;

    public Builder(final EthNetworkConfig ethNetworkConfig) {
      this.genesisConfig = ethNetworkConfig.genesisConfig;
      this.networkId = ethNetworkConfig.networkId;
      this.bootNodes = ethNetworkConfig.bootNodes;
      this.dnsDiscoveryUrl = ethNetworkConfig.dnsDiscoveryUrl;
    }

    public Builder setGenesisConfig(final String genesisConfig) {
      this.genesisConfig = genesisConfig;
      return this;
    }

    public Builder setNetworkId(final BigInteger networkId) {
      this.networkId = networkId;
      return this;
    }

    public Builder setBootNodes(final List<EnodeURL> bootNodes) {
      this.bootNodes = bootNodes;
      return this;
    }

    public Builder setDnsDiscoveryUrl(final String dnsDiscoveryUrl) {
      this.dnsDiscoveryUrl = dnsDiscoveryUrl;
      return this;
    }

    public EthNetworkConfig build() {
      return new EthNetworkConfig(genesisConfig, networkId, bootNodes, dnsDiscoveryUrl);
    }
  }
}
