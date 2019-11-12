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
package org.hyperledger.besu.tests.acceptance.jsonrpc;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Before;
import org.junit.Test;

public class WebsocketServiceLoginAcceptanceTest extends AcceptanceTestBase {
  private BesuNode nodeUsingAuthFile;
  private BesuNode nodeUsingJwtPublicKey;

  private static final String TOKEN =
      "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE1MTYyMzkwMjI"
          + "sInBlcm1pc3Npb25zIjpbIm5ldDpwZWVyQ291bnQiXX0.fXi73v4UTEO3hiG0AzPaD-OjQy0rL0SY-tMNCfJMdiVde"
          + "im7Erwq4sVCrFtmx0tUs-e5Z1t_K-Gx6c_95911T2Jq2VLlwKJDs0FYEGgq2G3W-PMMrT21SPLJM-r7kl9_k51Xbww"
          + "D7Cku_JFaLmkhd_l8k-EmGCTCWar514HUTlH0pm4nYhDKa7SuMAqMUo8CSZRCEzSD_AeOShJTk02cPtkCqXzClK3XO"
          + "gfxsO5viuklX13VT35lyG-HNNuReLX6U4nWu_irHv0r7Gl8MVFz0Ohm0bA_G1OUh5ue6y7DcYADOoYTmvfSgkKD0hl"
          + "bKx3j3tp1PX6Cw_fZUjviFvwxEg";

  @Before
  public void setUp() throws IOException, URISyntaxException {
    nodeUsingAuthFile = besu.createArchiveNodeWithAuthenticationOverWebSocket("node1");
    nodeUsingJwtPublicKey =
        besu.createArchiveNodeWithAuthenticationUsingJwtPublicKeyOverWebSocket("node2");
    cluster.start(nodeUsingAuthFile, nodeUsingJwtPublicKey);
    nodeUsingAuthFile.useWebSocketsForJsonRpc();
    nodeUsingJwtPublicKey.useWebSocketsForJsonRpc();
  }

  @Test
  public void shouldFailLoginWithWrongCredentials() {
    nodeUsingAuthFile.verify(login.failure("user", "badpassword"));
  }

  @Test
  public void shouldSucceedLoginWithCorrectCredentials() {
    nodeUsingAuthFile.verify(login.success("user", "pegasys"));
  }

  @Test
  public void jsonRpcMethodShouldSucceedWithAuthenticatedUserAndPermission() {
    final String token =
        nodeUsingAuthFile.execute(permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    nodeUsingAuthFile.useAuthenticationTokenInHeaderForJsonRpc(token);
    nodeUsingAuthFile.verify(net.awaitPeerCount(1));
    nodeUsingAuthFile.verify(net.netVersionUnauthorizedResponse());
  }

  @Test
  public void externalJwtPublicKeyUsedOnJsonRpcMethodShouldSucceed() {
    nodeUsingJwtPublicKey.useAuthenticationTokenInHeaderForJsonRpc(TOKEN);

    nodeUsingJwtPublicKey.verify(net.awaitPeerCount(1));
    nodeUsingJwtPublicKey.verify(net.netVersionUnauthorizedResponse());
  }

  @Test
  public void loginShouldBeDisabledWhenUsingExternalJwtPublicKey() {
    nodeUsingJwtPublicKey.verify(login.disabled());
  }
}
