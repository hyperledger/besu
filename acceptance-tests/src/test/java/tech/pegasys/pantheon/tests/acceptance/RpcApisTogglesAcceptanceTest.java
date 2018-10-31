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
package tech.pegasys.pantheon.tests.acceptance;

import static tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonNode;
import static tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonRpcDisabledNode;
import static tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.patheonNodeWithRpcApis;

import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApis;
import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;

import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class RpcApisTogglesAcceptanceTest extends AcceptanceTestBase {

  private PantheonNode rpcEnabledNode;
  private PantheonNode rpcDisabledNode;
  private PantheonNode ethApiDisabledNode;

  @Before
  public void before() throws Exception {
    rpcEnabledNode = cluster.create(pantheonNode("rpc-enabled"));
    rpcDisabledNode = cluster.create(pantheonRpcDisabledNode("rpc-disabled"));
    ethApiDisabledNode = cluster.create(patheonNodeWithRpcApis("eth-api-disabled", RpcApis.NET));
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

    // TODO previously this test was calling netVersion using the HTTP not WebSockets, now failing
    rpcEnabledNode.verify(net.netVersion());
  }

  @Test
  public void shouldFailConnectingToNodeWithWsRpcDisabled() {
    rpcDisabledNode.useWebSocketsForJsonRpc();

    rpcDisabledNode.verify(net.netVersionExceptional(WebsocketNotConnectedException.class));
  }

  @Test
  public void shouldSucceedCallingMethodFromEnabledApiGroup() {
    ethApiDisabledNode.verify(net.netVersion());
  }

  @Test
  public void shouldFailCallingMethodFromDisabledApiGroup() {
    final String expectedMessage = "Invalid response received: 400";

    ethApiDisabledNode.verify(eth.accountsExceptional(expectedMessage));
  }
}
