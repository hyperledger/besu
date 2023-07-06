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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.DEFAULT_RPC_APIS;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;

import com.google.common.collect.Lists;
import org.junit.Test;

public class WebSocketConfigurationTest {

  @Test
  public void defaultConfiguration() {
    final WebSocketConfiguration configuration = WebSocketConfiguration.createDefault();

    assertThat(configuration.isEnabled()).isFalse();
    assertThat(configuration.getHost()).isEqualTo("127.0.0.1");
    assertThat(configuration.getPort()).isEqualTo(8546);
    assertThat(configuration.getRpcApis()).containsExactlyInAnyOrderElementsOf(DEFAULT_RPC_APIS);
    assertThat(configuration.getRpcApisNoAuth()).isEmpty();
    assertThat(configuration.getMaxActiveConnections())
        .isEqualTo(WebSocketConfiguration.DEFAULT_MAX_ACTIVE_CONNECTIONS);
  }

  @Test
  public void settingRpcApisShouldOverridePreviousValues() {
    final WebSocketConfiguration configuration = WebSocketConfiguration.createDefault();

    configuration.setRpcApis(Lists.newArrayList(RpcApis.ETH.name(), RpcApis.MINER.name()));
    assertThat(configuration.getRpcApis())
        .containsExactly(RpcApis.ETH.name(), RpcApis.MINER.name());

    configuration.setRpcApis(Lists.newArrayList(RpcApis.DEBUG.name()));
    assertThat(configuration.getRpcApis()).containsExactly(RpcApis.DEBUG.name());
  }

  @Test
  public void settingNoAuthRpcApisShouldOverridePreviousValues() {
    final WebSocketConfiguration configuration = WebSocketConfiguration.createDefault();

    configuration.setRpcApisNoAuth(
        Lists.newArrayList(RpcMethod.ADMIN_ADD_PEER.name(), RpcMethod.ADMIN_PEERS.name()));
    assertThat(configuration.getRpcApisNoAuth())
        .containsExactly(RpcMethod.ADMIN_ADD_PEER.name(), RpcMethod.ADMIN_PEERS.name());

    configuration.setRpcApisNoAuth(Lists.newArrayList(RpcMethod.MINER_SET_COINBASE.name()));
    assertThat(configuration.getRpcApisNoAuth())
        .containsExactly(RpcMethod.MINER_SET_COINBASE.name());
  }
}
