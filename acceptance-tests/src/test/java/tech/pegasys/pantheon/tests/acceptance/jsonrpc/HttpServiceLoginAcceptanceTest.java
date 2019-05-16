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
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.Cluster;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Before;
import org.junit.Test;

public class HttpServiceLoginAcceptanceTest extends AcceptanceTestBase {
  private Cluster authenticatedCluster;
  private Node node;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();

    authenticatedCluster = new Cluster(clusterConfiguration, net);
    node = pantheon.createArchiveNodeWithAuthentication("node1");
    authenticatedCluster.start(node);
    node.verify(login.awaitLoginResponse("user", "badpassword"));
  }

  @Test
  public void shouldFailLoginWithWrongCredentials() {
    node.verify(login.loginFails("user", "badpassword"));
  }

  @Test
  public void shouldSucceedLoginWithCorrectCredentials() {
    node.verify(login.loginSucceeds("user", "pegasys"));
  }

  @Test
  public void jsonRpcMethodShouldSucceedWithAuthenticatedUserAndPermission() {
    node.verify(login.loginSucceedsAndSetsAuthenticationToken("user", "pegasys"));
    node.verify(net.awaitPeerCount(0));
    node.verify(net.netVersionUnauthorizedExceptional("Unauthorized"));
  }

  @Override
  public void tearDownAcceptanceTestBase() {
    authenticatedCluster.stop();
    super.tearDownAcceptanceTestBase();
  }
}
