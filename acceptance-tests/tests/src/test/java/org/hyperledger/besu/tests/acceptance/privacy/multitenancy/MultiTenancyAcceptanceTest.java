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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static org.hyperledger.besu.ethereum.core.Address.DEFAULT_PRIVACY;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.io.Base64;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MultiTenancyAcceptanceTest extends AcceptanceTestBase {
  private BesuNode node;
  private Cluster multiTenancyCluster;
  private String token;
  private final ObjectMapper mapper = new ObjectMapper();

  private static final int ENCLAVE_PORT = 1080;
  private static final String PRIVACY_GROUP_ID = "Z3JvdXBJZA==";
  private static final String ENCLAVE_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String KEY1 = "sgFkVOyFndZe/5SAZJO5UYbrl7pezHetveriBBWWnE8=";
  private static final String KEY2 = "R1kW75NQC9XX3kwNpyPjCBFflM29+XvnKKS9VLrUkzo=";
  private static final String KEY3 = "QzHuACXpfhoGAgrQriWJcDJ6MrUwcCvutKMoAn9KplQ=";
  private final Account sender = accounts.createAccount("account1");
  private final Address senderAddress =
      Address.wrap(Bytes.fromHexString(accounts.getPrimaryBenefactor().getAddress()));

  @Rule public WireMockRule wireMockRule = new WireMockRule(options().port(ENCLAVE_PORT));

  @Before
  public void setUp() throws Exception {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    multiTenancyCluster = new Cluster(clusterConfiguration, net);
    node =
        besu.createNodeWithMultiTenancy(
            "node1",
            "http://127.0.0.1:" + ENCLAVE_PORT,
            "authentication/auth_priv.toml",
            "authentication/auth_pub_key",
            "authentication/auth_priv_key");
    multiTenancyCluster.start(node);
    token = node.execute(permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    node.useAuthenticationTokenInHeaderForJsonRpc(token);
  }

  @Test
  public void privGetPrivacyPrecompileAddressShouldReturnExpectedAddress() {
    node.verify(priv.privGetPrivacyPrecompileAddressSuccess(DEFAULT_PRIVACY));
  }

  @Test
  public void privGetPrivateTransactionSuccessShouldReturnExpectedPrivateTransaction()
      throws JsonProcessingException {
    final PrivateTransaction validSignedPrivateTransaction =
        getValidSignedPrivateTransaction(senderAddress);

    receiveEnclaveStub(getRLPOutput(validSignedPrivateTransaction));
    retrievePrivacyGroupEnclaveStub();
    sendEnclaveStub("testKey");

    final Hash transactionHash =
        node.execute(
            privacyTransactions.sendRawTransaction(
                getRLPOutput(validSignedPrivateTransaction).encoded().toHexString()));
    node.verify(priv.getTransactionReceiptSuccess(transactionHash));
    node.verify(
        priv.privGetPrivateTransactionSuccess(
            transactionHash, validSignedPrivateTransaction.getPrivateFrom().toBase64String()));
  }

  @Test
  public void privCreatePrivacyGroupSuccessShouldReturnNewId() throws JsonProcessingException {
    createPrivacyGroupEnclaveStub();

    node.verify(
        priv.privCreatePrivacyGroupSuccess(
            List.of(KEY1, KEY2, KEY3), "GroupName", "Group description.", PRIVACY_GROUP_ID));
  }

  @Test
  public void privDeletePrivacyGroupSuccessShouldReturnId() throws JsonProcessingException {
    retrievePrivacyGroupEnclaveStub();
    deletePrivacyGroupEnclaveStub();

    node.verify(priv.privDeletePrivacyGroupSuccess(PRIVACY_GROUP_ID));
  }

  @Test
  public void privFindPrivacyGroupSuccessShouldReturnExpectedGroupMembership()
      throws JsonProcessingException {
    final List<PrivacyGroup> groupMembership =
        List.of(
            testPrivacyGroup(emptyList()),
            testPrivacyGroup(emptyList()),
            testPrivacyGroup(emptyList()));

    findPrivacyGroupEnclaveStub(groupMembership);

    node.verify(priv.privFindPrivacyGroupSuccess(groupMembership.size(), ENCLAVE_KEY));
  }

  @Test
  public void eeaSendRawTransactionSuccessShouldReturnPrivateTransactionHash()
      throws JsonProcessingException {
    final PrivateTransaction validSignedPrivateTransaction =
        getValidSignedPrivateTransaction(senderAddress);

    retrievePrivacyGroupEnclaveStub();
    sendEnclaveStub("testKey");
    receiveEnclaveStub(getRLPOutput(validSignedPrivateTransaction));

    CompletableFuture<Hash> completableFuture = new CompletableFuture<>();

    node.verify(
        priv.eeaSendRawTransactionSuccess(
            getRLPOutput(validSignedPrivateTransaction).encoded().toHexString(),
            completableFuture));

    completableFuture.whenComplete(
        (hash, throwable) -> node.verify(priv.getTransactionReceiptSuccess(hash)));
  }

  @Test
  public void privGetTransactionCountSuccessShouldReturnExpectedTransactionCount()
      throws JsonProcessingException {
    final PrivateTransaction validSignedPrivateTransaction =
        getValidSignedPrivateTransaction(senderAddress);
    final String senderAddress = validSignedPrivateTransaction.getSender().toHexString();
    final BytesValueRLPOutput rlpOutput = getRLPOutput(validSignedPrivateTransaction);

    retrievePrivacyGroupEnclaveStub();
    sendEnclaveStub("testKey");
    receiveEnclaveStub(rlpOutput);

    node.verify(priv.privGetTransactionCountSuccess(senderAddress, PRIVACY_GROUP_ID, 0));
    final Hash transactionReceipt =
        node.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));

    node.verify(priv.getTransactionReceiptSuccess(transactionReceipt));
    node.verify(priv.privGetTransactionCountSuccess(senderAddress, PRIVACY_GROUP_ID, 1));
  }

  @Test
  public void privDistributeRawTransactionSuccessShouldReturnEnclaveKey()
      throws JsonProcessingException {
    final String enclaveResponseKey = "TestKey";
    final String enclaveResponseKeyBase64 =
        Base64.encode(Bytes.wrap(enclaveResponseKey.getBytes(UTF_8)));
    final String enclaveResponseKeyBytes =
        Bytes.wrap(Bytes.fromBase64String(enclaveResponseKeyBase64)).toString();

    retrievePrivacyGroupEnclaveStub();
    sendEnclaveStub(enclaveResponseKeyBase64);

    node.verify(
        priv.privDistributeRawTransaction(
            getRLPOutput(getValidSignedPrivateTransaction(senderAddress)).encoded().toHexString(),
            enclaveResponseKeyBytes));
  }

  private void findPrivacyGroupEnclaveStub(final List<PrivacyGroup> groupMembership)
      throws JsonProcessingException {
    final String findGroupResponse = mapper.writeValueAsString(groupMembership);
    stubFor(post("/findPrivacyGroup").willReturn(ok(findGroupResponse)));
  }

  private void createPrivacyGroupEnclaveStub() throws JsonProcessingException {
    final String createGroupResponse = mapper.writeValueAsString(testPrivacyGroup(emptyList()));
    stubFor(post("/createPrivacyGroup").willReturn(ok(createGroupResponse)));
  }

  private void deletePrivacyGroupEnclaveStub() throws JsonProcessingException {
    final String deleteGroupResponse = mapper.writeValueAsString(PRIVACY_GROUP_ID);
    stubFor(post("/deletePrivacyGroup").willReturn(ok(deleteGroupResponse)));
  }

  private void retrievePrivacyGroupEnclaveStub() throws JsonProcessingException {
    final String retrieveGroupResponse =
        mapper.writeValueAsString(testPrivacyGroup(List.of(ENCLAVE_KEY)));
    stubFor(post("/retrievePrivacyGroup").willReturn(ok(retrieveGroupResponse)));
  }

  private void sendEnclaveStub(final String testKey) throws JsonProcessingException {
    final String sendResponse = mapper.writeValueAsString(new SendResponse(testKey));
    stubFor(post("/send").willReturn(ok(sendResponse)));
  }

  private void receiveEnclaveStub(final BytesValueRLPOutput rlpOutput)
      throws JsonProcessingException {
    final String senderKey = "QTFhVnRNeExDVUhtQlZIWG9aenpCZ1BiVy93ajVheERwVzlYOGw5MVNHbz0=";
    final String receiveResponse =
        mapper.writeValueAsString(
            new ReceiveResponse(
                rlpOutput.encoded().toBase64String().getBytes(UTF_8), PRIVACY_GROUP_ID, senderKey));
    stubFor(post("/receive").willReturn(ok(receiveResponse)));
  }

  private BytesValueRLPOutput getRLPOutput(final PrivateTransaction validSignedPrivateTransaction) {
    final BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    validSignedPrivateTransaction.writeTo(bvrlpo);
    return bvrlpo;
  }

  private PrivacyGroup testPrivacyGroup(final List<String> groupMembers) {
    return new PrivacyGroup(
        PRIVACY_GROUP_ID, PrivacyGroup.Type.BESU, "test", "testGroup", groupMembers);
  }

  private static PrivateTransaction getValidSignedPrivateTransaction(final Address senderAddress) {
    return PrivateTransaction.builder()
        .nonce(0)
        .gasPrice(Wei.of(1000))
        .gasLimit(3000000)
        .to(null)
        .value(Wei.ZERO)
        .payload(Bytes.wrap(new byte[] {}))
        .sender(senderAddress)
        .chainId(BigInteger.valueOf(2018))
        .privateFrom(Bytes.fromBase64String(ENCLAVE_KEY))
        .restriction(Restriction.RESTRICTED)
        .privacyGroupId(Bytes.fromBase64String(PRIVACY_GROUP_ID))
        .signAndBuild(
            SECP256K1.KeyPair.create(
                SECP256K1.PrivateKey.create(
                    new BigInteger(
                        "853d7f0010fd86d0d7811c1f9d968ea89a24484a8127b4a483ddf5d2cfec766d", 16))));
  }
}
