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
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class JsonRpcWebsocketAuthenticationAcceptanceTest extends AcceptanceTestBase {
  private BesuNode nodeUsingAuthFile;
  private BesuNode nodeUsingRsaJwtPublicKey;
  private BesuNode nodeUsingEcdsaJwtPublicKey;
  private BesuNode nodeUsingAuthFileWithNoAuthApi;
  private Cluster authenticatedCluster;
  private static final String AUTH_FILE = "authentication/auth.toml";

  private static final List<String> NO_AUTH_API_METHODS = Arrays.asList("net_services");

  // token with payload{"iat": 1516239022,"exp": 4729363200,"permissions": ["net:peerCount"]}
  private static final String RSA_TOKEN_ALLOWING_NET_PEER_COUNT =
      "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE1MTYyMzkwMjIsImV4cCI6NDcyOTM2MzIwMCwicGVybWl"
          + "zc2lvbnMiOlsibmV0OnBlZXJDb3VudCJdfQ.Y6mNV0nvjzOdqAgMgxknFAOUTKoeRAo4aifNgNrWtuXbJJgz6-"
          + "H_0GvLgjlToohPiDZbBJXJJlgb4zzLLB-sRtFnGoPaMgz_d_6z958GjFD7x_Fl0HW-WrTjRNenZNfTyD86OEAf"
          + "XHy-7N3OYY2a5yeDbppTJy6nnHTq9hY-ad22-oWL1RbK3T_hnUJII_uXCZ9bJggSfu5m-NNUrm3TeqdnQzIaIz"
          + "DqHlL0wNZwVPB4cFGN7zKghReBpkRJ8OFlxexQ491Q5eSpuYquhef-yGCIaMfy7GVtpDSD3Y-hjOErr7gUNCUh"
          + "1wlc3Rb7ru_0qNgCWTBPJeRK32GppYotwQ";

  private static final String ECDSA_TOKEN_ALLOWING_NET_PEER_COUNT =
      "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE1MTYyMzkwMjIsImV4cCI6NDcyOTM2MzIwMCwicGVybWlz"
          + "c2lvbnMiOlsibmV0OnBlZXJDb3VudCJdfQ.pWXniN6XQ7G8b1nawy8sviPCMxrfbcI6c7UFzeXm26CMGMUEZxiC"
          + "JjRntB8ueuZcsxnGlEhCHt-KngpFEmx5TA";

  @Before
  public void setUp() throws IOException, URISyntaxException {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    authenticatedCluster = new Cluster(clusterConfiguration, net);

    nodeUsingAuthFile = besu.createNodeWithAuthentication("node1", AUTH_FILE);
    nodeUsingRsaJwtPublicKey = besu.createNodeWithAuthenticationUsingRsaJwtPublicKey("node2");
    nodeUsingEcdsaJwtPublicKey = besu.createNodeWithAuthenticationUsingEcdsaJwtPublicKey("node3");
    nodeUsingAuthFileWithNoAuthApi =
        besu.createWsNodeWithAuthFileAndNoAuthApi("node4", AUTH_FILE, NO_AUTH_API_METHODS);
    authenticatedCluster.start(
        nodeUsingAuthFile,
        nodeUsingRsaJwtPublicKey,
        nodeUsingEcdsaJwtPublicKey,
        nodeUsingAuthFileWithNoAuthApi);

    nodeUsingAuthFile.useWebSocketsForJsonRpc();
    nodeUsingRsaJwtPublicKey.useWebSocketsForJsonRpc();
    nodeUsingEcdsaJwtPublicKey.useWebSocketsForJsonRpc();
    nodeUsingAuthFileWithNoAuthApi.useWebSocketsForJsonRpc();
    nodeUsingAuthFile.verify(login.awaitResponse("user", "badpassword"));
    nodeUsingRsaJwtPublicKey.verify(login.awaitResponse("user", "badpassword"));
    nodeUsingEcdsaJwtPublicKey.verify(login.awaitResponse("user", "badpassword"));
    nodeUsingAuthFileWithNoAuthApi.verify(login.awaitResponse("user", "badpassword"));
  }

  @Test
  public void shouldFailLoginWithWrongCredentials() {
    nodeUsingAuthFile.verify(login.failure("user", "badpassword"));
    nodeUsingAuthFileWithNoAuthApi.verify(login.failure("user", "badpassword"));
  }

  @Test
  public void shouldSucceedLoginWithCorrectCredentials() {
    nodeUsingAuthFile.verify(login.success("user", "pegasys"));
    nodeUsingAuthFileWithNoAuthApi.verify(login.success("user", "pegasys"));
  }

  @Test
  public void jsonRpcMethodShouldSucceedWithAuthenticatedUserAndPermission() {
    String token =
        nodeUsingAuthFile.execute(
            permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    nodeUsingAuthFile.useAuthenticationTokenInHeaderForJsonRpc(token);
    nodeUsingAuthFile.verify(net.awaitPeerCount(3));

    token =
        nodeUsingAuthFileWithNoAuthApi.execute(
            permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    nodeUsingAuthFileWithNoAuthApi.useAuthenticationTokenInHeaderForJsonRpc(token);
    nodeUsingAuthFileWithNoAuthApi.verify(net.awaitPeerCount(3));
  }

  @Test
  public void jsonRpcMethodShouldFailOnNonPermittedMethod() {
    String token =
        nodeUsingAuthFile.execute(
            permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    nodeUsingAuthFile.useAuthenticationTokenInHeaderForJsonRpc(token);
    nodeUsingAuthFile.verify(net.netVersionUnauthorized());
    nodeUsingAuthFile.verify(net.netServicesUnauthorized());

    token =
        nodeUsingAuthFileWithNoAuthApi.execute(
            permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    nodeUsingAuthFileWithNoAuthApi.useAuthenticationTokenInHeaderForJsonRpc(token);
    nodeUsingAuthFileWithNoAuthApi.verify(net.netVersionUnauthorized());
  }

  @Test
  public void jsonRpcMethodsNotIncludedInNoAuthListShouldFailWithoutToken() {
    nodeUsingAuthFile.verify(net.netVersionUnauthorized());
    nodeUsingAuthFileWithNoAuthApi.verify(net.netVersionUnauthorized());
  }

  @Test
  public void noAuthJsonRpcMethodShouldSucceedWithoutToken() {
    nodeUsingAuthFileWithNoAuthApi.verify(net.netServicesAllActive());
  }

  @Test
  public void noAuthJsonRpcConfiguredNodeShouldWorkAsIntended() {
    // No token -> all methods other than specified no auth methods should fail
    nodeUsingAuthFileWithNoAuthApi.verify(net.netVersionUnauthorized());
    nodeUsingAuthFileWithNoAuthApi.verify(net.netServicesAllActive());

    // Should behave the same with valid token
    String token =
        nodeUsingAuthFileWithNoAuthApi.execute(
            permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    nodeUsingAuthFileWithNoAuthApi.useAuthenticationTokenInHeaderForJsonRpc(token);
    nodeUsingAuthFileWithNoAuthApi.verify(net.netVersionUnauthorized());
    nodeUsingAuthFileWithNoAuthApi.verify(net.netServicesAllActive());
    nodeUsingAuthFileWithNoAuthApi.verify(net.awaitPeerCount(3));
  }

  @Test
  public void externalRsaJwtPublicKeyUsedOnJsonRpcMethodShouldSucceed() {
    nodeUsingRsaJwtPublicKey.useAuthenticationTokenInHeaderForJsonRpc(
        RSA_TOKEN_ALLOWING_NET_PEER_COUNT);
    nodeUsingRsaJwtPublicKey.verify(net.awaitPeerCount(3));
  }

  @Test
  public void externalRsaJwtPublicKeyUsedOnJsonRpcMethodShouldFailOnNonPermittedMethod() {
    nodeUsingRsaJwtPublicKey.useAuthenticationTokenInHeaderForJsonRpc(
        RSA_TOKEN_ALLOWING_NET_PEER_COUNT);
    nodeUsingRsaJwtPublicKey.verify(net.netVersionUnauthorized());
    nodeUsingAuthFile.verify(net.netServicesUnauthorized());
  }

  @Test
  public void externalEcdsaJwtPublicKeyUsedOnJsonRpcMethodShouldSucceed() {
    nodeUsingEcdsaJwtPublicKey.useAuthenticationTokenInHeaderForJsonRpc(
        ECDSA_TOKEN_ALLOWING_NET_PEER_COUNT);
    nodeUsingEcdsaJwtPublicKey.verify(net.awaitPeerCount(3));
  }

  @Test
  public void externalEcdsaJwtPublicKeyUsedOnJsonRpcMethodShouldFailOnNonPermittedMethod() {
    nodeUsingEcdsaJwtPublicKey.useAuthenticationTokenInHeaderForJsonRpc(
        ECDSA_TOKEN_ALLOWING_NET_PEER_COUNT);
    nodeUsingEcdsaJwtPublicKey.verify(net.netVersionUnauthorized());
    nodeUsingEcdsaJwtPublicKey.verify(net.netServicesUnauthorized());
  }

  @Test
  public void jsonRpcMethodShouldFailWhenThereIsNoToken() {
    nodeUsingRsaJwtPublicKey.verify(net.netVersionUnauthorized());
    nodeUsingRsaJwtPublicKey.verify(net.netServicesUnauthorized());
  }

  @Test
  public void loginShouldBeDisabledWhenUsingExternalJwtPublicKey() {
    nodeUsingRsaJwtPublicKey.verify(login.disabled());
  }

  @Override
  public void tearDownAcceptanceTestBase() {
    authenticatedCluster.stop();
    super.tearDownAcceptanceTestBase();
  }
}
