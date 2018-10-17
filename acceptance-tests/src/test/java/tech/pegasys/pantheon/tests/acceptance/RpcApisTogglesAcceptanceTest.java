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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonNode;
import static tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonRpcDisabledNode;
import static tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.patheonNodeWithRpcApis;

import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration.RpcApis;
import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.exceptions.ClientConnectionException;

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
    rpcEnabledNode.verifyJsonRpcEnabled();
  }

  @Test
  public void shouldFailConnectingToNodeWithJsonRpcDisabled() {
    rpcDisabledNode.verifyJsonRpcDisabled();
  }

  @Test
  public void shouldSucceedConnectingToNodeWithWsRpcEnabled() {
    rpcEnabledNode.verifyWsRpcEnabled();
  }

  @Test
  public void shouldFailConnectingToNodeWithWsRpcDisabled() {
    rpcDisabledNode.verifyWsRpcDisabled();
  }

  @Test
  public void shouldSucceedCallingMethodFromEnabledApiGroup() throws Exception {
    final Web3j web3j = ethApiDisabledNode.web3j();

    assertThat(web3j.netVersion().send().getError()).isNull();
  }

  @Test
  public void shouldFailCallingMethodFromDisabledApiGroup() {
    final Web3j web3j = ethApiDisabledNode.web3j();

    assertThat(catchThrowable(() -> web3j.ethAccounts().send()))
        .isInstanceOf(ClientConnectionException.class)
        .hasMessageContaining("Invalid response received: 400");
  }
}
