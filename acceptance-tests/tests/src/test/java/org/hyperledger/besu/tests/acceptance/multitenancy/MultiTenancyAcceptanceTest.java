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
package org.hyperledger.besu.tests.acceptance.multitenancy;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;

public class MultiTenancyAcceptanceTest extends AcceptanceTestBase {
  private BesuNode node;
  private ClientAndServer mockServer;
  private Cluster cluster;

  @Before
  public void setUp() throws Exception {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    cluster = new Cluster(clusterConfiguration, net);

    mockServer = startClientAndServer(1080);
    node =
        besu.createNodeWithMultiTenancyEnabled(
            "node1", "http://127.0.0.1:1080", "authentication/auth_priv.toml");
    this.cluster.start(node);
  }

  @After
  public void stopMockServer() {
    mockServer.stop();
  }

  @Test
  public void shouldSucceedWithValidToken() {
    final String token =
        node.execute(permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    node.useAuthenticationTokenInHeaderForJsonRpc(token);
    node.verify(priv.privGetPrivacyPrecompileAddressSuccess());
  }

  @Test
  public void shouldFailWithInvalidToken() {
    node.verify(priv.privGetPrivacyPrecompileAddressSuccess());
  }
}
