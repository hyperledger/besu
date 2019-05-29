/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.tests.acceptance.jsonrpc;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Before;
import org.junit.Test;

public class WebsocketServiceLoginAcceptanceTest extends AcceptanceTestBase {
  private PantheonNode node;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    node = pantheon.createArchiveNodeWithAuthenticationOverWebSocket("node1");
    cluster.start(node);
    node.useWebSocketsForJsonRpc();
  }

  @Test
  public void shouldFailLoginWithWrongCredentials() {
    node.verify(login.failure("user", "badpassword"));
  }

  @Test
  public void shouldSucceedLoginWithCorrectCredentials() {
    node.verify(login.success("user", "pegasys"));
  }

  @Test
  public void jsonRpcMethodShouldSucceedWithAuthenticatedUserAndPermission() {
    final String token =
        node.execute(permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    node.useAuthenticationTokenInHeaderForJsonRpc(token);
    node.verify(net.awaitPeerCount(0));
    node.verify(net.netVersionUnauthorizedResponse());
  }
}
