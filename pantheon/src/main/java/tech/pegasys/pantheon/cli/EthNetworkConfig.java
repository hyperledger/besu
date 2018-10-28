/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.cli;

import static tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration.MAINNET_BOOTSTRAP_NODES;
import static tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration.RINKEBY_BOOTSTRAP_NODES;
import static tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration.ROPSTEN_BOOTSTRAP_NODES;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Objects;

import com.google.common.base.Preconditions;
import com.google.common.io.Resources;

public class EthNetworkConfig {
  private static final int MAINNET_NETWORK_ID = 1;
  private static final int RINKEBY_NETWORK_ID = 4;
  private static final int ROPSTEN_NETWORK_ID = 3;
  private static final String MAINNET_GENESIS = "mainnet.json";
  private static final String RINKEBY_GENESIS = "rinkeby.json";
  private static final String ROPSTEN_GENESIS = "ropsten.json";
  private final URI genesisConfig;
  private final int networkId;
  private final Collection<?> bootNodes;

  public EthNetworkConfig(
      final URI genesisConfig, final int networkId, final Collection<?> bootNodes) {
    Preconditions.checkNotNull(genesisConfig);
    Preconditions.checkNotNull(bootNodes);
    this.genesisConfig = genesisConfig;
    this.networkId = networkId;
    this.bootNodes = bootNodes;
  }

  public URI getGenesisConfig() {
    return genesisConfig;
  }

  public int getNetworkId() {
    return networkId;
  }

  public Collection<?> getBootNodes() {
    return bootNodes;
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
    return networkId == that.networkId
        && Objects.equals(genesisConfig, that.genesisConfig)
        && Objects.equals(bootNodes, that.bootNodes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(genesisConfig, networkId, bootNodes);
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
        + '}';
  }

  public static EthNetworkConfig mainnet() {
    final URI genesisConfig = jsonConfigURI(MAINNET_GENESIS);
    return new EthNetworkConfig(genesisConfig, MAINNET_NETWORK_ID, MAINNET_BOOTSTRAP_NODES);
  }

  public static EthNetworkConfig rinkeby() {
    final URI genesisConfig = jsonConfigURI(RINKEBY_GENESIS);
    return new EthNetworkConfig(genesisConfig, RINKEBY_NETWORK_ID, RINKEBY_BOOTSTRAP_NODES);
  }

  public static EthNetworkConfig ropsten() {
    final URI genesisConfig = jsonConfigURI(ROPSTEN_GENESIS);
    return new EthNetworkConfig(genesisConfig, ROPSTEN_NETWORK_ID, ROPSTEN_BOOTSTRAP_NODES);
  }

  private static URI jsonConfigURI(final String resourceName) {
    try {
      return Resources.getResource(resourceName).toURI();
    } catch (final URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  public static class Builder {

    private URI genesisConfig;
    private int networkId;
    private Collection<?> bootNodes;

    public Builder(final EthNetworkConfig ethNetworkConfig) {
      this.genesisConfig = ethNetworkConfig.genesisConfig;
      this.networkId = ethNetworkConfig.networkId;
      this.bootNodes = ethNetworkConfig.bootNodes;
    }

    public Builder setGenesisConfig(final URI genesisConfig) {
      this.genesisConfig = genesisConfig;
      return this;
    }

    public Builder setNetworkId(final int networkId) {
      this.networkId = networkId;
      return this;
    }

    public Builder setBootNodes(final Collection<?> bootNodes) {
      this.bootNodes = bootNodes;
      return this;
    }

    public EthNetworkConfig build() {
      return new EthNetworkConfig(genesisConfig, networkId, bootNodes);
    }
  }
}
