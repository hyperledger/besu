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
package org.hyperledger.besu.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

public class NetworkDefinitionTest {

  @Test
  public void isL1Network_returnsTrue_forEthereumMainnetAndTestnets() {
    assertThat(NetworkDefinition.MAINNET.isL1Network()).isTrue();
    assertThat(NetworkDefinition.SEPOLIA.isL1Network()).isTrue();
    assertThat(NetworkDefinition.HOLESKY.isL1Network()).isTrue();
    assertThat(NetworkDefinition.HOODI.isL1Network()).isTrue();
    assertThat(NetworkDefinition.EPHEMERY.isL1Network()).isTrue();
  }

  @Test
  public void isL1Network_returnsFalse_forL2Networks() {
    assertThat(NetworkDefinition.LINEA_MAINNET.isL1Network()).isFalse();
    assertThat(NetworkDefinition.LINEA_SEPOLIA.isL1Network()).isFalse();
  }

  @Test
  public void isL1Network_returnsTrue_forDevelopmentNetworks() {
    assertThat(NetworkDefinition.DEV.isL1Network()).isTrue();
    assertThat(NetworkDefinition.FUTURE_EIPS.isL1Network()).isTrue();
    assertThat(NetworkDefinition.EXPERIMENTAL_EIPS.isL1Network()).isTrue();
  }

  @Test
  public void isL1Network_returnsTrue_forOtherL1Networks() {
    // Ethereum Classic and related networks are L1 chains
    assertThat(NetworkDefinition.CLASSIC.isL1Network()).isTrue();
    assertThat(NetworkDefinition.MORDOR.isL1Network()).isTrue();
    assertThat(NetworkDefinition.LUKSO.isL1Network()).isTrue();
  }

  @Test
  public void isL1NetworkByChainId_returnsTrue_forKnownL1ChainIds() {
    // Mainnet
    assertThat(NetworkDefinition.isL1NetworkByChainId(BigInteger.valueOf(1))).isTrue();
    // Sepolia
    assertThat(NetworkDefinition.isL1NetworkByChainId(BigInteger.valueOf(11155111))).isTrue();
    // Holesky
    assertThat(NetworkDefinition.isL1NetworkByChainId(BigInteger.valueOf(17000))).isTrue();
    // Hoodi
    assertThat(NetworkDefinition.isL1NetworkByChainId(BigInteger.valueOf(560048))).isTrue();
    // Classic
    assertThat(NetworkDefinition.isL1NetworkByChainId(BigInteger.valueOf(1))).isTrue();
  }

  @Test
  public void isL1NetworkByChainId_returnsFalse_forL2ChainIds() {
    // Linea mainnet
    assertThat(NetworkDefinition.isL1NetworkByChainId(BigInteger.valueOf(59144))).isFalse();
    // Linea Sepolia
    assertThat(NetworkDefinition.isL1NetworkByChainId(BigInteger.valueOf(59141))).isFalse();
  }

  @Test
  public void isL1NetworkByChainId_returnsFalse_forUnknownChainIds() {
    // Unknown chain ID
    assertThat(NetworkDefinition.isL1NetworkByChainId(BigInteger.valueOf(999999))).isFalse();
    // Another unknown
    assertThat(NetworkDefinition.isL1NetworkByChainId(BigInteger.valueOf(123456789))).isFalse();
  }
}
