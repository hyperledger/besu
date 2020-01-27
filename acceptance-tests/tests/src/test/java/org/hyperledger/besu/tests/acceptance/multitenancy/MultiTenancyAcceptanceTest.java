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
import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
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
  public void shouldGetPrivateTransaction() throws JsonProcessingException {
    node.useAuthenticationTokenInHeaderForJsonRpc(token);

    String base64SignedPrivateTransactionRLP =
        "+MyAAYJSCJQJXnuupqbHxMLf65d++sMmr1Uth6D//////////////////////////////////////////4AboEi1W/qRWseVxDGXjYpqmStijVV9pf91mzB9SVo2ZJNToB//0xCsdD83HeO59/nLVsCyitQ2AbSrlJ9T+qB70sgEoANWlbTMSwlB5gVR16Gc8wYD21v8I+WsQ6VvV/JfdUhqoA8gDohf8p6XPiV2tmABgdGworUpTjDZvkoZgf+zOguMinJlc3RyaWN0ZWQ=";

    // privateFrom value from above transaction
    final String privateFrom = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

    // Create a transaction
    final Account sender = accounts.createAccount("account1");
    final Hash transactionHash = node.execute(accountTransactions.createTransfer(sender, 50));
    multiTenancyCluster.verify(sender.balanceEquals(50));

    final ObjectMapper mapper = new ObjectMapper();
    final String receiveResponse =
        mapper.writeValueAsString(
            new ReceiveResponse(
                base64SignedPrivateTransactionRLP.getBytes(UTF_8), privacyGroupId, "senderKey"));

    stubFor(post("/receive").willReturn(ok(receiveResponse)));

    node.verify(priv.privGetPrivateTransaction(transactionHash.toString(), privateFrom));
  }

  @Test
  public void shouldCreatePrivacyGroup() throws JsonProcessingException {
    final ObjectMapper mapper = new ObjectMapper();
    final String groupId = "groupId";
    final String createGroupResponse = mapper.writeValueAsString(testPrivacyGroup());

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
    final String retrieveGroupResponse = mapper.writeValueAsString(testPrivacyGroup());
    stubFor(post("/retrievePrivacyGroup").willReturn(ok(retrieveGroupResponse)));

    final String deleteGroupResponse = mapper.writeValueAsString(privacyGroupId);
    stubFor(post("/deletePrivacyGroup").willReturn(ok(deleteGroupResponse)));

    node.useAuthenticationTokenInHeaderForJsonRpc(token);
    node.verify(priv.privDeletePrivacyGroup(privacyGroupId));
  }

  @Test
  public void shouldFindPrivacyGroup() throws JsonProcessingException {
    final ObjectMapper mapper = new ObjectMapper();
    final String findGroupResponse = mapper.writeValueAsString(List.of(testPrivacyGroup()));
    stubFor(post("/findPrivacyGroup").willReturn(ok(findGroupResponse)));

    final String[] paramArray = {enclaveKey};

    node.useAuthenticationTokenInHeaderForJsonRpc(token);
    node.verify(priv.privFindPrivacyGroup(paramArray));
  }

  private PrivacyGroup testPrivacyGroup() {
    return new PrivacyGroup(
        privacyGroupId, PrivacyGroup.Type.PANTHEON, "test", "testGroup", List.of(enclaveKey));
  }
}
