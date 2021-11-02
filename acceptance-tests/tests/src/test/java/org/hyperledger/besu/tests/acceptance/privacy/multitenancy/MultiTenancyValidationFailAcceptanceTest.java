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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.DELETE_PRIVACY_GROUP_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.ENCLAVE_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.FIND_PRIVACY_GROUP_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY;

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.plugin.data.Restriction;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

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

public class MultiTenancyValidationFailAcceptanceTest extends AcceptanceTestBase {
  private BesuNode node;
  private final ObjectMapper mapper = new ObjectMapper();
  private Cluster multiTenancyCluster;

  private static final String PRIVACY_GROUP_ID = "B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String ENCLAVE_PUBLIC_KEY = "B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String OTHER_ENCLAVE_PUBLIC_KEY =
      "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private final Address senderAddress =
      Address.wrap(Bytes.fromHexString(accounts.getPrimaryBenefactor().getAddress()));

  @Rule public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort());

  @Before
  public void setUp() throws Exception {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    multiTenancyCluster = new Cluster(clusterConfiguration, net);
    node =
        besu.createNodeWithMultiTenantedPrivacy(
            "node1",
            "http://127.0.0.1:" + wireMockRule.port(),
            "authentication/auth_priv.toml",
            "authentication/auth_priv_key",
            false);
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
  public void sendRawTransactionShouldFailWhenPrivateFromNotMatchEnclaveKey()
      throws JsonProcessingException {
    final PrivateTransaction validSignedPrivateTransaction =
        getValidSignedPrivateTransaction(senderAddress, OTHER_ENCLAVE_PUBLIC_KEY);
    retrievePrivacyGroupEnclaveStub();
    final Transaction<Hash> transaction =
        privacyTransactions.sendRawTransaction(
            getRLPOutput(validSignedPrivateTransaction).encoded().toHexString());
    node.verify(
        priv.multiTenancyValidationFail(
            transaction, JsonRpcError.PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY));
  }

  @Test
  public void sendRawTransactionShouldFailWhenPrivacyGroupDoesNotContainEnclaveKey()
      throws JsonProcessingException {
    final PrivateTransaction validSignedPrivateTransaction =
        getValidSignedPrivateTransaction(senderAddress, ENCLAVE_PUBLIC_KEY);
    retrievePrivacyGroupEnclaveStub();
    final Transaction<Hash> transaction =
        privacyTransactions.sendRawTransaction(
            getRLPOutput(validSignedPrivateTransaction).encoded().toHexString());
    node.verify(priv.multiTenancyValidationFail(transaction, ENCLAVE_ERROR));
  }

  @Test
  public void distributeRawTransactionShouldFailWhenPrivateFromNotMatchEnclaveKey() {
    final Address senderAddress =
        Address.wrap(Bytes.fromHexString(accounts.getPrimaryBenefactor().getAddress()));

    final Transaction<String> transaction =
        privacyTransactions.distributeRawTransaction(
            getRLPOutput(getValidSignedPrivateTransaction(senderAddress, OTHER_ENCLAVE_PUBLIC_KEY))
                .encoded()
                .toHexString());
    node.verify(
        priv.multiTenancyValidationFail(
            transaction, PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY));
  }

  @Test
  public void distributeRawTransactionShouldFailWhenPrivacyGroupDoesNotContainEnclaveKey()
      throws JsonProcessingException {
    final Address senderAddress =
        Address.wrap(Bytes.fromHexString(accounts.getPrimaryBenefactor().getAddress()));

    retrievePrivacyGroupEnclaveStub();

    final Transaction<String> transaction =
        privacyTransactions.distributeRawTransaction(
            getRLPOutput(getValidSignedPrivateTransaction(senderAddress, ENCLAVE_PUBLIC_KEY))
                .encoded()
                .toHexString());
    node.verify(priv.multiTenancyValidationFail(transaction, ENCLAVE_ERROR));
  }

  @Test
  public void deletePrivacyGroupShouldFailWhenEnclaveKeyNotInPrivacyGroup()
      throws JsonProcessingException {
    retrievePrivacyGroupEnclaveStub();
    final Transaction<String> transaction =
        privacyTransactions.deletePrivacyGroup(PRIVACY_GROUP_ID);
    node.verify(priv.multiTenancyValidationFail(transaction, DELETE_PRIVACY_GROUP_ERROR));
  }

  @Test
  public void findPrivacyGroupShouldFailWhenEnclaveKeyNotInPrivacyGroup() {
    final Transaction<PrivacyGroup[]> transaction =
        privacyTransactions.findPrivacyGroup(new String[] {OTHER_ENCLAVE_PUBLIC_KEY});
    node.verify(priv.multiTenancyValidationFail(transaction, FIND_PRIVACY_GROUP_ERROR));
  }

  @Test
  public void determineEeaNonceShouldFailWhenPrivateFromNotMatchEnclaveKey() {
    final String accountAddress = Address.ZERO.toHexString();
    final String senderAddressBase64 = Bytes.fromHexString(accountAddress).toBase64String();
    final String[] privateFor = {senderAddressBase64};
    final Transaction<Integer> transaction =
        privacyTransactions.getEeaTransactionCount(
            accountAddress, OTHER_ENCLAVE_PUBLIC_KEY, privateFor);
    node.verify(
        priv.multiTenancyValidationFail(
            transaction, PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY));
  }

  @Test
  public void determineBesuNonceShouldFailWhenEnclaveKeyNotInPrivacyGroup()
      throws JsonProcessingException {
    retrievePrivacyGroupEnclaveStub();
    final String accountAddress = Address.ZERO.toHexString();
    final Transaction<Integer> transaction =
        privacyTransactions.getTransactionCount(accountAddress, PRIVACY_GROUP_ID);
    node.verify(priv.multiTenancyValidationFail(transaction, GET_PRIVATE_TRANSACTION_NONCE_ERROR));
  }

  private void retrievePrivacyGroupEnclaveStub() throws JsonProcessingException {
    final String retrieveGroupResponse =
        mapper.writeValueAsString(
            testPrivacyGroup(List.of(OTHER_ENCLAVE_PUBLIC_KEY), PrivacyGroup.Type.PANTHEON));
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

    final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();

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
            signatureAlgorithm.createKeyPair(
                signatureAlgorithm.createPrivateKey(
                    new BigInteger(
                        "853d7f0010fd86d0d7811c1f9d968ea89a24484a8127b4a483ddf5d2cfec766d", 16))));
  }
}
