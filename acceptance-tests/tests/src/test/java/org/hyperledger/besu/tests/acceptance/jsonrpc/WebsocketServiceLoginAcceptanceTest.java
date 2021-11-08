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
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Before;
import org.junit.Test;

public class WebsocketServiceLoginAcceptanceTest extends AcceptanceTestBase {
  private BesuNode nodeUsingAuthFile;
  private BesuNode nodeUsingJwtPublicKey;
  private Cluster authenticatedCluster;
  private static final String AUTH_FILE = "authentication/auth.toml";

  // token with payload{"iat": 1516239022,"exp": 4729363200,"permissions": ["net:peerCount"]}
  private static final String TOKEN_ALLOWING_NET_PEER_COUNT =
      "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE1MTYyMzkwMjIsImV4cCI6NDcyOTM2MzIwMCwicGVybWl"
          + "zc2lvbnMiOlsibmV0OnBlZXJDb3VudCJdfQ.Y6mNV0nvjzOdqAgMgxknFAOUTKoeRAo4aifNgNrWtuXbJJgz6-"
          + "H_0GvLgjlToohPiDZbBJXJJlgb4zzLLB-sRtFnGoPaMgz_d_6z958GjFD7x_Fl0HW-WrTjRNenZNfTyD86OEAf"
          + "XHy-7N3OYY2a5yeDbppTJy6nnHTq9hY-ad22-oWL1RbK3T_hnUJII_uXCZ9bJggSfu5m-NNUrm3TeqdnQzIaIz"
          + "DqHlL0wNZwVPB4cFGN7zKghReBpkRJ8OFlxexQ491Q5eSpuYquhef-yGCIaMfy7GVtpDSD3Y-hjOErr7gUNCUh"
          + "1wlc3Rb7ru_0qNgCWTBPJeRK32GppYotwQ";

  @Before
  public void setUp() throws IOException, URISyntaxException {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    authenticatedCluster = new Cluster(clusterConfiguration, net);

    nodeUsingAuthFile = besu.createNodeWithAuthentication("node1", AUTH_FILE);
    nodeUsingJwtPublicKey = besu.createNodeWithAuthenticationUsingRSAJwtPublicKey("node2");
    authenticatedCluster.start(nodeUsingAuthFile, nodeUsingJwtPublicKey);

    nodeUsingAuthFile.useWebSocketsForJsonRpc();
    nodeUsingJwtPublicKey.useWebSocketsForJsonRpc();
    nodeUsingAuthFile.verify(login.awaitResponse("user", "badpassword"));
    nodeUsingJwtPublicKey.verify(login.awaitResponse("user", "badpassword"));
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
        nodeUsingAuthFile.execute(
            permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    nodeUsingAuthFile.useAuthenticationTokenInHeaderForJsonRpc(token);
    nodeUsingAuthFile.verify(net.awaitPeerCount(1));
  }

  @Test
  public void jsonRpcMethodShouldFailOnNonPermittedMethod() {
    final String token =
        nodeUsingAuthFile.execute(
            permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    nodeUsingAuthFile.useAuthenticationTokenInHeaderForJsonRpc(token);
    nodeUsingAuthFile.verify(net.netVersionUnauthorized());
    nodeUsingAuthFile.verify(net.netServicesUnauthorized());
  }

  @Test
  public void externalJwtPublicKeyUsedOnJsonRpcMethodShouldSucceed() {
    nodeUsingJwtPublicKey.useAuthenticationTokenInHeaderForJsonRpc(TOKEN_ALLOWING_NET_PEER_COUNT);
    nodeUsingJwtPublicKey.verify(net.awaitPeerCount(1));
  }

  @Test
  public void externalJwtPublicKeyUsedOnJsonRpcMethodShouldFailOnNonPermittedMethod() {
    nodeUsingJwtPublicKey.useAuthenticationTokenInHeaderForJsonRpc(TOKEN_ALLOWING_NET_PEER_COUNT);
    nodeUsingJwtPublicKey.verify(net.netVersionUnauthorized());
    nodeUsingAuthFile.verify(net.netServicesUnauthorized());
  }

  @Test
  public void jsonRpcMethodShouldFailWhenThereIsNoToken() {
    nodeUsingJwtPublicKey.verify(net.netVersionUnauthorized());
    nodeUsingJwtPublicKey.verify(net.netServicesUnauthorized());
  }

  @Test
  public void loginShouldBeDisabledWhenUsingExternalJwtPublicKey() {
    nodeUsingJwtPublicKey.verify(login.disabled());
  }

  @Override
  public void tearDownAcceptanceTestBase() {
    authenticatedCluster.stop();
    super.tearDownAcceptanceTestBase();
  }
}
