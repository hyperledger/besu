/*
 * Copyright contributors to Besu.
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JsonRpcWebsocketAuthenticationEcdsaAcceptanceTest extends AcceptanceTestBase {
  private BesuNode nodeUsingEcdsaJwtPublicKey;
  private Cluster authenticatedCluster;

  // token with payload{"iat": 1516239022,"exp": 4729363200,"permissions": ["net:peerCount"]}
  private static final String ECDSA_TOKEN_ALLOWING_NET_PEER_COUNT =
      "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE1MTYyMzkwMjIsImV4cCI6NDcyOTM2MzIwMCwicGVybWlz"
          + "c2lvbnMiOlsibmV0OnBlZXJDb3VudCJdfQ.pWXniN6XQ7G8b1nawy8sviPCMxrfbcI6c7UFzeXm26CMGMUEZxiC"
          + "JjRntB8ueuZcsxnGlEhCHt-KngpFEmx5TA";

  @BeforeEach
  public void setUp() throws IOException, URISyntaxException {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    authenticatedCluster = new Cluster(clusterConfiguration, net);

    nodeUsingEcdsaJwtPublicKey =
        besu.createNodeWithAuthenticationUsingEcdsaJwtPublicKey("nodeEcdsa");
    authenticatedCluster.start(nodeUsingEcdsaJwtPublicKey);

    nodeUsingEcdsaJwtPublicKey.useWebSocketsForJsonRpc();
    nodeUsingEcdsaJwtPublicKey.verify(login.awaitResponse("user", "badpassword"));
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

  @AfterEach
  @Override
  public void tearDownAcceptanceTestBase() {
    authenticatedCluster.stop();
    super.tearDownAcceptanceTestBase();
  }
}
