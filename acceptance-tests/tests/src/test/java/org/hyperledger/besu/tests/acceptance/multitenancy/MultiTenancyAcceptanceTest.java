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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.parameters.CreatePrivacyGroupParameter;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
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
  private Cluster multiTenancyCluster;
  private String token;
  private static final int enclavePort = 1080;
  private final String privacyGroupId = "groupId";
  private final String enclaveKey = "negmDcN2P4ODpqn/6WkJ02zT/0w0bjhGpkZ8UP6vARk=";

  private final String key1 = "sgFkVOyFndZe/5SAZJO5UYbrl7pezHetveriBBWWnE8=";
  private final String key2 = "R1kW75NQC9XX3kwNpyPjCBFflM29+XvnKKS9VLrUkzo=";
  private final String key3 = "QzHuACXpfhoGAgrQriWJcDJ6MrUwcCvutKMoAn9KplQ=";

  @Rule public WireMockRule wireMockRule = new WireMockRule(options().port(enclavePort));

  @Before
  public void setUp() throws Exception {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    multiTenancyCluster = new Cluster(clusterConfiguration, net);
    node =
        besu.createNodeWithMultiTenancyEnabled(
            "node1", "http://127.0.0.1:" + enclavePort, "authentication/auth_priv.toml");
    this.multiTenancyCluster.start(node);
    token = node.execute(permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
  }

  @Test
  public void shouldGetPrivacyPrecompileAddress() {
    node.useAuthenticationTokenInHeaderForJsonRpc(token);
    node.verify(priv.privGetPrivacyPrecompileAddressSuccess());
  }

  @Test
  public void shouldGetPrivateTransaction() {
    node.useAuthenticationTokenInHeaderForJsonRpc(token);

    final Account sender = accounts.createAccount("account1");
    cluster.verify(sender.balanceEquals(0));
    Hash transactionHash = node.execute(accountTransactions.createTransfer(sender, 50));
    cluster.verify(sender.balanceEquals(50));

    node.verify(priv.privGetPrivateTransaction(transactionHash.toString()));
  }

  @Test
  public void shouldCreatePrivacyGroup() throws JsonProcessingException {
    final ObjectMapper mapper = new ObjectMapper();
    final String groupId = "groupId";
    final String createGroupResponse =
        mapper.writeValueAsString(
            new PrivacyGroup(
                groupId, PrivacyGroup.Type.PANTHEON, "test", "testGroup", List.of(enclaveKey)));

    CreatePrivacyGroupParameter params =
        new CreatePrivacyGroupParameter(
            List.of(key1, key2, key3), "GroupName", "Group description.");
    stubFor(post("/createPrivacyGroup").willReturn(ok(createGroupResponse)));

    node.useAuthenticationTokenInHeaderForJsonRpc(token);
    node.verify(priv.privCreatePrivacyGroup(params, groupId));
  }

  @Test
  public void shouldDeletePrivacyGroup() throws JsonProcessingException {
    final ObjectMapper mapper = new ObjectMapper();
    final String retrieveGroupResponse =
        mapper.writeValueAsString(
            new PrivacyGroup(
                "groupId", PrivacyGroup.Type.PANTHEON, "test", "testGroup", List.of(enclaveKey)));
    stubFor(post("/retrievePrivacyGroup").willReturn(ok(retrieveGroupResponse)));

    final String deleteGroupResponse = mapper.writeValueAsString(privacyGroupId);
    stubFor(post("/deletePrivacyGroup").willReturn(ok(deleteGroupResponse)));

    node.useAuthenticationTokenInHeaderForJsonRpc(token);
    node.verify(priv.privDeletePrivacyGroup(privacyGroupId));
  }

  @Test
  public void shouldFindPrivacyGroup() throws JsonProcessingException {
    final ObjectMapper mapper = new ObjectMapper();
    final String findGroupResponse =
        mapper.writeValueAsString(
            List.of(
                new PrivacyGroup(
                    "groupId",
                    PrivacyGroup.Type.PANTHEON,
                    "test",
                    "testGroup",
                    List.of(enclaveKey))));
    stubFor(post("/findPrivacyGroup").willReturn(ok(findGroupResponse)));

    final String[] paramArray = {enclaveKey};

    node.useAuthenticationTokenInHeaderForJsonRpc(token);
    node.verify(priv.privFindPrivacyGroup(paramArray));
  }

  @Test
  public void shouldGetTransactionCount() throws JsonProcessingException {
    final ObjectMapper mapper = new ObjectMapper();
    final String retrieveGroupResponse =
        mapper.writeValueAsString(
            new PrivacyGroup(
                "groupId", PrivacyGroup.Type.PANTHEON, "test", "testGroup", List.of(enclaveKey)));
    stubFor(post("/retrievePrivacyGroup").willReturn(ok(retrieveGroupResponse)));

    final Object[] params = new Object[] {"0xebf56429e6500e84442467292183d4d621359838", "groupId"};

    node.useAuthenticationTokenInHeaderForJsonRpc(token);
    node.verify(priv.privGetTransactionCount(params));
  }
}
