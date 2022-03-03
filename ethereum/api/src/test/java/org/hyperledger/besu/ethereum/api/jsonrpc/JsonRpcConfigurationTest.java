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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.DEFAULT_RPC_APIS;

import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.JwtAlgorithm;

import java.util.Optional;

import com.google.common.collect.Lists;
import org.junit.Test;

public class JsonRpcConfigurationTest {

  @Test
  public void defaultConfiguration() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();

    assertThat(configuration.isEnabled()).isFalse();
    assertThat(configuration.getHost()).isEqualTo("127.0.0.1");
    assertThat(configuration.getPort()).isEqualTo(8545);
    assertThat(configuration.getCorsAllowedDomains()).isEmpty();
    assertThat(configuration.getRpcApis()).containsExactlyInAnyOrderElementsOf(DEFAULT_RPC_APIS);
    assertThat(configuration.getNoAuthRpcApis()).isEmpty();
    assertThat(configuration.getMaxActiveConnections())
        .isEqualTo(JsonRpcConfiguration.DEFAULT_MAX_ACTIVE_CONNECTIONS);
    assertThat(configuration.getAuthenticationAlgorithm()).isEqualTo(JwtAlgorithm.RS256);
  }

  @Test
  public void corsAllowedOriginsDefaultShouldBeEmptyList() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();
    assertThat(configuration.getCorsAllowedDomains()).isEmpty();
  }

  @Test
  public void rpcApiDefaultShouldBePredefinedList() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();
    assertThat(configuration.getRpcApis()).containsExactlyElementsOf(DEFAULT_RPC_APIS);
  }

  @Test
  public void settingCorsAllowedOriginsShouldOverridePreviousValues() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();

    configuration.setCorsAllowedDomains(Lists.newArrayList("foo", "bar"));
    assertThat(configuration.getCorsAllowedDomains()).containsExactly("foo", "bar");

    configuration.setCorsAllowedDomains(Lists.newArrayList("zap"));
    assertThat(configuration.getCorsAllowedDomains()).containsExactly("zap");
  }

  @Test
  public void settingRpcApisShouldOverridePreviousValues() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();

    configuration.setRpcApis(Lists.newArrayList(RpcApis.ETH.name(), RpcApis.MINER.name()));
    assertThat(configuration.getRpcApis())
        .containsExactly(RpcApis.ETH.name(), RpcApis.MINER.name());

    configuration.setRpcApis(Lists.newArrayList(RpcApis.DEBUG.name()));
    assertThat(configuration.getRpcApis()).containsExactly(RpcApis.DEBUG.name());
  }

  @Test
  public void settingNoAuthRpcApisShouldOverridePreviousValues() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();

    configuration.setNoAuthRpcApis(
        Lists.newArrayList(RpcMethod.ADMIN_ADD_PEER.name(), RpcMethod.ADMIN_PEERS.name()));
    assertThat(configuration.getNoAuthRpcApis())
        .containsExactly(RpcMethod.ADMIN_ADD_PEER.name(), RpcMethod.ADMIN_PEERS.name());

    configuration.setNoAuthRpcApis(Lists.newArrayList(RpcMethod.MINER_SET_COINBASE.name()));
    assertThat(configuration.getNoAuthRpcApis())
        .containsExactly(RpcMethod.MINER_SET_COINBASE.name());
  }

  @Test
  public void tlsConfigurationDefaultShouldBeEmpty() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();
    assertThat(configuration.getTlsConfiguration()).isEqualTo(Optional.empty());
  }
}
