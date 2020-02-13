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
package org.hyperledger.besu.tests.acceptance.privacy.multitenancy;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import java.math.BigInteger;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.tuweni.bytes.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MultiTenancyFailAcceptanceTest extends AcceptanceTestBase {
  private BesuNode node;
  private final ObjectMapper mapper = new ObjectMapper();
  private Cluster multiTenancyCluster;

  private static final int ENCLAVE_PORT = 1080;
  private static final String PRIVACY_GROUP_ID = "Z3JvdXBJZA==";
  private static final String ENCLAVE_KEY = "B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String OTHER_ENCLAVE_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private final Address senderAddress =
      Address.wrap(Bytes.fromHexString(accounts.getPrimaryBenefactor().getAddress()));

  @Rule public WireMockRule wireMockRule = new WireMockRule(options().port(ENCLAVE_PORT));

  @Before
  public void setUp() throws Exception {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    multiTenancyCluster = new Cluster(clusterConfiguration, net);
    node =
        besu.createNodeWithMultiTenantedPrivacy(
            "node1",
            "http://127.0.0.1:" + ENCLAVE_PORT,
            "authentication/auth_priv.toml",
            "authentication/auth_priv_key");
    multiTenancyCluster.start(node);

    final String token =
        node.execute(permissioningTransactions.createSuccessfulLogin("failUser", "pegasys"));
    node.useAuthenticationTokenInHeaderForJsonRpc(token);
  }

  @After
  public void tearDown() {
    multiTenancyCluster.close();
  }

  @Test
  public void sendTransactionShouldFailWhenPrivateFromNotMatchEnclaveKey()
      throws JsonProcessingException {
    final PrivateTransaction validSignedPrivateTransaction =
        getValidSignedPrivateTransaction(senderAddress, OTHER_ENCLAVE_KEY);
    retrievePrivacyGroupEnclaveStub();
    node.verify(
        priv.eeaSendRawTransactionPrivateFromNotMatchEnclaveKey(
            getRLPOutput(validSignedPrivateTransaction).encoded().toHexString()));
  }

    @Test
    public void sendTransactionShouldFailWhenPrivacyGroupDoesNotContainEnclaveKey() throws JsonProcessingException {
      final PrivateTransaction validSignedPrivateTransaction =
              getValidSignedPrivateTransaction(senderAddress, ENCLAVE_KEY);
      retrievePrivacyGroupEnclaveStub();
      node.verify(
              priv.eeaSendRawTransactionPrivateFromNotMatchEnclaveKey(
                      getRLPOutput(validSignedPrivateTransaction).encoded().toHexString()));
    }

  @Test
  public void deletePrivacyGroupShouldFailWhenEnclaveKeyNotInPrivacyGroup()
      throws JsonProcessingException {
    retrievePrivacyGroupEnclaveStub();
    node.verify(priv.deletePrivacyGroupFailEnclaveKeyNotInGroup(PRIVACY_GROUP_ID));
  }

  @Test
  public void findPrivacyGroupShouldFailWhenEnclaveKeyNotInPrivacyGroup() {
    node.verify(priv.findPrivacyGroupFailEnclaveKeyNotInGroup(OTHER_ENCLAVE_KEY));
  }

  @Test
  public void determineEeaNonceShouldFailWhenPrivateFromNotMatchEnclaveKey() {
    final String accountAddress = Address.ZERO.toHexString();
    final String senderAddressBase64 = Bytes.fromHexString(accountAddress).toBase64String();
    final String[] privateFor = {senderAddressBase64};
    node.verify(
        priv.getEeaTransactionCountFailPrivateFromNotMatchEnclaveKey(
            accountAddress, OTHER_ENCLAVE_KEY, privateFor));
  }

  @Test
  public void determineBesuNonceShouldFailWhenEnclaveKeyNotInPrivacyGroup()
      throws JsonProcessingException {
    retrievePrivacyGroupEnclaveStub();
    final String accountAddress = Address.ZERO.toHexString();
    node.verify(
        priv.getTransactionCountFailWhenEnclaveKeyNotInPrivacyGroup(
            accountAddress, PRIVACY_GROUP_ID));
  }

  private void retrievePrivacyGroupEnclaveStub() throws JsonProcessingException {
    final String retrieveGroupResponse =
        mapper.writeValueAsString(
            testPrivacyGroup(List.of(OTHER_ENCLAVE_KEY), PrivacyGroup.Type.PANTHEON));
    stubFor(post("/retrievePrivacyGroup").willReturn(ok(retrieveGroupResponse)));
  }

  private PrivacyGroup testPrivacyGroup(
      final List<String> groupMembers, final PrivacyGroup.Type groupType) {
    return new PrivacyGroup(PRIVACY_GROUP_ID, groupType, "test", "testGroup", groupMembers);
  }

  private BytesValueRLPOutput getRLPOutput(final PrivateTransaction validSignedPrivateTransaction) {
    final BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    validSignedPrivateTransaction.writeTo(bvrlpo);
    return bvrlpo;
  }

  private static PrivateTransaction getValidSignedPrivateTransaction(
      final Address senderAddress, final String privateFrom) {
    return PrivateTransaction.builder()
        .nonce(0)
        .gasPrice(Wei.ZERO)
        .gasLimit(3000000)
        .to(null)
        .value(Wei.ZERO)
        .payload(Bytes.wrap(new byte[] {}))
        .sender(senderAddress)
        .chainId(BigInteger.valueOf(2018))
        .privateFrom(Bytes.fromBase64String(privateFrom))
        .restriction(Restriction.RESTRICTED)
        .privacyGroupId(Bytes.fromBase64String(PRIVACY_GROUP_ID))
        .signAndBuild(
            SECP256K1.KeyPair.create(
                SECP256K1.PrivateKey.create(
                    new BigInteger(
                        "853d7f0010fd86d0d7811c1f9d968ea89a24484a8127b4a483ddf5d2cfec766d", 16))));
  }
}
