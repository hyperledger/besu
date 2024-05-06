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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.cli.config.NetworkName.MAINNET;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.MAINNET_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.MAINNET_DISCOVERY_URL;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.SEPOLIA_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.SEPOLIA_DISCOVERY_URL;

import org.hyperledger.besu.config.GenesisConfigFile;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EthNetworkConfigTest {

  @Test
  public void testDefaultMainnetConfig() {
    EthNetworkConfig config = EthNetworkConfig.getNetworkConfig(NetworkName.MAINNET);
    assertThat(config.dnsDiscoveryUrl()).isEqualTo(MAINNET_DISCOVERY_URL);
    assertThat(config.bootNodes()).isEqualTo(MAINNET_BOOTSTRAP_NODES);
    assertThat(config.networkId()).isEqualTo(BigInteger.ONE);
  }

  @Test
  public void testDefaultSepoliaConfig() {
    EthNetworkConfig config = EthNetworkConfig.getNetworkConfig(NetworkName.SEPOLIA);
    assertThat(config.dnsDiscoveryUrl()).isEqualTo(SEPOLIA_DISCOVERY_URL);
    assertThat(config.bootNodes()).isEqualTo(SEPOLIA_BOOTSTRAP_NODES);
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(11155111));
  }

  @Test
  public void testDefaultDevConfig() {
    EthNetworkConfig config = EthNetworkConfig.getNetworkConfig(NetworkName.DEV);
    assertThat(config.dnsDiscoveryUrl()).isNull();
    assertThat(config.bootNodes()).isEmpty();
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(2018));
  }

  @Test
  public void testDefaultFutureConfig() {
    EthNetworkConfig config = EthNetworkConfig.getNetworkConfig(NetworkName.FUTURE_EIPS);
    assertThat(config.dnsDiscoveryUrl()).isNull();
    assertThat(config.bootNodes()).isEmpty();
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(2022));
  }

  @Test
  public void testDefaultExperimentalConfig() {
    EthNetworkConfig config = EthNetworkConfig.getNetworkConfig(NetworkName.EXPERIMENTAL_EIPS);
    assertThat(config.dnsDiscoveryUrl()).isNull();
    assertThat(config.bootNodes()).isEmpty();
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(2023));
  }

  @Test
  public void testBuilderWithNetworkId() {
    EthNetworkConfig config =
        new EthNetworkConfig.Builder(EthNetworkConfig.getNetworkConfig(MAINNET))
            .setNetworkId(BigInteger.valueOf(42))
            .setGenesisConfigFile(
                GenesisConfigFile.fromConfig(
                    """
            {
              "config":{
                "chainId":"1234567"
              }
            }
            """))
            .build();
    assertThat(config.genesisConfigFile().getConfigOptions().getChainId())
        .contains(BigInteger.valueOf(1234567));
    assertThat(config.dnsDiscoveryUrl()).isNotNull();
    assertThat(config.bootNodes()).isNotEmpty();
    assertThat(config.networkId()).isEqualTo(BigInteger.valueOf(42));
  }
}
