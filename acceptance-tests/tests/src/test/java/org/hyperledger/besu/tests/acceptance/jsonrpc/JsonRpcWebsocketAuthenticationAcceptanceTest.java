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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JsonRpcWebsocketAuthenticationAcceptanceTest extends AcceptanceTestBase {
  private BesuNode nodeUsingAuthFile;
  private BesuNode nodeUsingAuthFileWithNoAuthApi;
  private Cluster authenticatedCluster;
  private static final String AUTH_FILE = "authentication/auth.toml";

  private static final List<String> NO_AUTH_API_METHODS = Arrays.asList("net_services");

  @BeforeEach
  public void setUp() throws IOException, URISyntaxException {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    authenticatedCluster = new Cluster(clusterConfiguration, net);

    nodeUsingAuthFile = besu.createNodeWithAuthentication("nodeAuth", AUTH_FILE);
    nodeUsingAuthFileWithNoAuthApi =
        besu.createWsNodeWithAuthFileAndNoAuthApi("nodeNoAuth", AUTH_FILE, NO_AUTH_API_METHODS);
    authenticatedCluster.start(nodeUsingAuthFile, nodeUsingAuthFileWithNoAuthApi);

    nodeUsingAuthFile.useWebSocketsForJsonRpc();
    nodeUsingAuthFileWithNoAuthApi.useWebSocketsForJsonRpc();
    nodeUsingAuthFile.verify(login.awaitResponse("user", "badpassword"));
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

  @AfterEach
  @Override
  public void tearDownAcceptanceTestBase() {
    authenticatedCluster.stop();
    super.tearDownAcceptanceTestBase();
  }
}
