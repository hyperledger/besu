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

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class EthNetworkConfig {

  private final String genesisConfig;
  private final BigInteger networkId;
  private final List<EnodeURL> bootNodes;
  private final String dnsDiscoveryUrl;

  public EthNetworkConfig(
      final String genesisConfig,
      final BigInteger networkId,
      final List<EnodeURL> bootNodes,
      final String dnsDiscoveryUrl) {
    Objects.requireNonNull(genesisConfig);
    Objects.requireNonNull(bootNodes);
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
    final String genesisContent = jsonConfig(networkName.getGenesisFile());
    final GenesisConfigOptions genesisConfigOptions =
        GenesisConfigFile.fromConfig(genesisContent).getConfigOptions();
    final Optional<List<String>> rawBootNodes =
        genesisConfigOptions.getDiscoveryOptions().getBootNodes();
    final List<EnodeURL> bootNodes =
        rawBootNodes
            .map(
                strings ->
                    strings.stream().map(EnodeURLImpl::fromString).collect(Collectors.toList()))
            .orElse(Collections.emptyList());
    return new EthNetworkConfig(
        genesisContent,
        networkName.getNetworkId(),
        bootNodes,
        genesisConfigOptions.getDiscoveryOptions().getDiscoveryDnsUrl().orElse(null));
  }

  private static String jsonConfig(final String resourceName) {
    try (final InputStream genesisFileInputStream =
        EthNetworkConfig.class.getResourceAsStream(resourceName)) {
      return new String(genesisFileInputStream.readAllBytes(), UTF_8);
    } catch (IOException | NullPointerException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String jsonConfig(final NetworkName network) {
    return jsonConfig(network.getGenesisFile());
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
