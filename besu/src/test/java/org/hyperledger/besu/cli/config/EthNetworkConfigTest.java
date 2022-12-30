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
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.GOERLI_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.GOERLI_DISCOVERY_URL;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.MAINNET_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.MAINNET_DISCOVERY_URL;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.RINKEBY_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DefaultDiscoveryConfiguration.RINKEBY_DISCOVERY_URL;

import java.math.BigInteger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthNetworkConfigTest {

  @Test
  public void testDefaultMainnetConfig() {
    EthNetworkConfig config = EthNetworkConfig.getNetworkConfig(NetworkName.MAINNET);
    assertThat(config.getDnsDiscoveryUrl()).isEqualTo(MAINNET_DISCOVERY_URL);
    assertThat(config.getBootNodes()).isEqualTo(MAINNET_BOOTSTRAP_NODES);
    assertThat(config.getNetworkId()).isEqualTo(BigInteger.ONE);
  }

  @Test
  public void testDefaultRinkebyConfig() {
    EthNetworkConfig config = EthNetworkConfig.getNetworkConfig(NetworkName.RINKEBY);
    assertThat(config.getDnsDiscoveryUrl()).isEqualTo(RINKEBY_DISCOVERY_URL);
    assertThat(config.getBootNodes()).isEqualTo(RINKEBY_BOOTSTRAP_NODES);
    assertThat(config.getNetworkId()).isEqualTo(BigInteger.valueOf(4));
  }

  @Test
  public void testDefaultGoerliConfig() {
    EthNetworkConfig config = EthNetworkConfig.getNetworkConfig(NetworkName.GOERLI);
    assertThat(config.getDnsDiscoveryUrl()).isEqualTo(GOERLI_DISCOVERY_URL);
    assertThat(config.getBootNodes()).isEqualTo(GOERLI_BOOTSTRAP_NODES);
    assertThat(config.getNetworkId()).isEqualTo(BigInteger.valueOf(5));
  }

  @Test
  public void testDefaultDevConfig() {
    EthNetworkConfig config = EthNetworkConfig.getNetworkConfig(NetworkName.DEV);
    assertThat(config.getDnsDiscoveryUrl()).isNull();
    assertThat(config.getBootNodes()).isEmpty();
    assertThat(config.getNetworkId()).isEqualTo(BigInteger.valueOf(2018));
  }

  @Test
  public void testBuilderWithNetworkId() {
    EthNetworkConfig config =
        new EthNetworkConfig.Builder(EthNetworkConfig.getNetworkConfig(MAINNET))
            .setNetworkId(BigInteger.valueOf(42))
            .setGenesisConfig("{\"config\":{\"chainId\":\"1234567\"}")
            .build();
    assertThat(config.getGenesisConfig()).isEqualTo("{\"config\":{\"chainId\":\"1234567\"}");
    assertThat(config.getDnsDiscoveryUrl()).isNotNull();
    assertThat(config.getBootNodes()).isNotEmpty();
    assertThat(config.getNetworkId()).isEqualTo(BigInteger.valueOf(42));
  }
}
