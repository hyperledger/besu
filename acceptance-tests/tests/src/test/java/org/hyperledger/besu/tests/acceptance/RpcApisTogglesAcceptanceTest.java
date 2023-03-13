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
package org.hyperledger.besu.tests.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RpcApisTogglesAcceptanceTest extends AcceptanceTestBase {

  private BesuNode rpcEnabledNode;
  private BesuNode rpcDisabledNode;
  private BesuNode ethApiDisabledNode;

  @BeforeEach
  public void before() throws Exception {
    rpcEnabledNode = besu.createArchiveNode("rpc-enabled");
    rpcDisabledNode = besu.createArchiveNodeWithRpcDisabled("rpc-disabled");
    ethApiDisabledNode = besu.createArchiveNodeWithRpcApis("eth-api-disabled", RpcApis.NET.name());
    cluster.start(rpcEnabledNode, rpcDisabledNode, ethApiDisabledNode);
  }

  @Test
  public void shouldSucceedConnectingToNodeWithJsonRpcEnabled() {
    rpcEnabledNode.verify(net.netVersion());
  }

  @Test
  public void shouldFailConnectingToNodeWithJsonRpcDisabled() {
    final String expectedMessage = "Failed to connect to /127.0.0.1:8545";

    rpcDisabledNode.verify(net.netVersionExceptional(expectedMessage));
  }

  @Test
  public void shouldSucceedConnectingToNodeWithWsRpcEnabled() {
    rpcEnabledNode.useWebSocketsForJsonRpc();

    rpcEnabledNode.verify(net.netVersion());
  }

  @Test
  public void shouldFailConnectingToNodeWithWsRpcDisabled() {
    rpcDisabledNode.verify(
        node -> {
          final Throwable thrown = catchThrowable(() -> rpcDisabledNode.useWebSocketsForJsonRpc());
          assertThat(thrown).isInstanceOf(WebsocketNotConnectedException.class);
        });
  }

  @Test
  public void shouldSucceedCallingMethodFromEnabledApiGroup() {
    ethApiDisabledNode.verify(net.netVersion());
  }

  @Test
  public void shouldFailCallingMethodFromDisabledApiGroup() {
    final String expectedMessage = "Method not enabled";

    ethApiDisabledNode.verify(eth.accountsExceptional(expectedMessage));
  }
}
