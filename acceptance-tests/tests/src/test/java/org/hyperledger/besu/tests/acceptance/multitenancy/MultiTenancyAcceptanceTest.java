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

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MultiTenancyAcceptanceTest extends AcceptanceTestBase {
  private BesuNode node;
  private Cluster cluster;
  private static final int enclavePort = 1080;
  private final String enclaveKey = "negmDcN2P4ODpqn/6WkJ02zT/0w0bjhGpkZ8UP6vARk=";

  @Rule public WireMockRule wireMockRule = new WireMockRule(options().port(enclavePort));

  @Before
  public void setUp() throws Exception {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    cluster = new Cluster(clusterConfiguration, net);

    node =
        besu.createNodeWithMultiTenancyEnabled(
            "node1", "http://127.0.0.1:" + enclavePort, "authentication/auth_priv.toml");
    this.cluster.start(node);
  }

  @Test
  public void shouldSucceedWithValidTokenParams() {
    final String token =
        node.execute(permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    node.useAuthenticationTokenInHeaderForJsonRpc(token);
    node.verify(priv.privGetPrivacyPrecompileAddressSuccess());
  }

  @Test
  public void shouldDeletePrivacyGroup() throws JsonProcessingException {
    final ObjectMapper mapper = new ObjectMapper();
    final String retrieveJsonString =
        mapper.writeValueAsString(
            new PrivacyGroup(
                "groupId", PrivacyGroup.Type.PANTHEON, "test", "testGroup", List.of(enclaveKey)));
    stubFor(post("/retrievePrivacyGroup").willReturn(ok(retrieveJsonString)));

    final String deleteJsonString = mapper.writeValueAsString(enclaveKey);
    stubFor(post("/deletePrivacyGroup").willReturn(ok(deleteJsonString)));

    final String token =
        node.execute(permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    final String transactionHash = Hash.ZERO.toString();
    node.useAuthenticationTokenInHeaderForJsonRpc(token);
    node.verify(priv.privDeletePrivacyGroup(transactionHash));
  }
}
