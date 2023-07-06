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
import static java.util.Collections.singletonList;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.DEFAULT_PRIVACY;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.privacy.PrivacyGroupUtil;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.plugin.data.Restriction;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MultiTenancyAcceptanceTest extends AcceptanceTestBase {
  private BesuNode node;
  private final ObjectMapper mapper = new ObjectMapper();
  private Cluster multiTenancyCluster;

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private static final KeyPair TEST_KEY =
      SIGNATURE_ALGORITHM
          .get()
          .createKeyPair(
              SIGNATURE_ALGORITHM
                  .get()
                  .createPrivateKey(
                      new BigInteger(
                          "853d7f0010fd86d0d7811c1f9d968ea89a24484a8127b4a483ddf5d2cfec766d", 16)));
  private static final String PRIVACY_GROUP_ID = "B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String PARTICIPANT_ENCLAVE_KEY0 =
      "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final Bytes LEAGCY_PRIVATE_FROM = Bytes.fromBase64String(PARTICIPANT_ENCLAVE_KEY0);
  private static final String PARTICIPANT_ENCLAVE_KEY1 =
      "sgFkVOyFndZe/5SAZJO5UYbrl7pezHetveriBBWWnE8=";
  private static final List<Bytes> LEGACY_PRIVATE_FOR =
      List.of(Bytes.fromBase64String(PARTICIPANT_ENCLAVE_KEY1));
  private static final String PARTICIPANT_ENCLAVE_KEY2 =
      "R1kW75NQC9XX3kwNpyPjCBFflM29+XvnKKS9VLrUkzo=";
  private static final String PARTICIPANT_ENCLAVE_KEY3 =
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
        node.execute(permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    node.useAuthenticationTokenInHeaderForJsonRpc(token);
  }

  @After
  public void tearDown() {
    multiTenancyCluster.close();
  }

  @Test
  public void privGetPrivacyPrecompileAddressShouldReturnExpectedAddress() {
    node.verify(priv.getPrivacyPrecompileAddress(DEFAULT_PRIVACY));
  }

  @Test
  public void privGetPrivateTransactionSuccessShouldReturnExpectedPrivateTransaction()
      throws JsonProcessingException {
    final PrivateTransaction validSignedPrivateTransaction =
        getValidSignedPrivateTransaction(senderAddress);

    receiveEnclaveStub(validSignedPrivateTransaction);
    retrievePrivacyGroupEnclaveStub();
    sendEnclaveStub(PARTICIPANT_ENCLAVE_KEY1);

    final Hash transactionHash =
        node.execute(
            privacyTransactions.sendRawTransaction(
                getRLPOutput(validSignedPrivateTransaction).encoded().toHexString()));
    node.verify(priv.getSuccessfulTransactionReceipt(transactionHash));
    node.verify(priv.getPrivateTransaction(transactionHash, validSignedPrivateTransaction));
  }

  @Test
  public void privCreatePrivacyGroupSuccessShouldReturnNewId() throws JsonProcessingException {
    createPrivacyGroupEnclaveStub();

    node.verify(
        priv.createPrivacyGroup(
            List.of(PARTICIPANT_ENCLAVE_KEY1, PARTICIPANT_ENCLAVE_KEY2, PARTICIPANT_ENCLAVE_KEY3),
            "GroupName",
            "Group description.",
            PRIVACY_GROUP_ID));
  }

  @Test
  public void privDeletePrivacyGroupSuccessShouldReturnId() throws JsonProcessingException {
    retrievePrivacyGroupEnclaveStub();
    deletePrivacyGroupEnclaveStub();

    node.verify(priv.deletePrivacyGroup(PRIVACY_GROUP_ID));
  }

  @Test
  public void privFindPrivacyGroupSuccessShouldReturnExpectedGroupMembership()
      throws JsonProcessingException {
    final List<PrivacyGroup> groupMembership =
        List.of(
            testPrivacyGroup(singletonList(PARTICIPANT_ENCLAVE_KEY0), PrivacyGroup.Type.PANTHEON),
            testPrivacyGroup(singletonList(PARTICIPANT_ENCLAVE_KEY0), PrivacyGroup.Type.PANTHEON),
            testPrivacyGroup(singletonList(PARTICIPANT_ENCLAVE_KEY0), PrivacyGroup.Type.PANTHEON));

    findPrivacyGroupEnclaveStub(groupMembership);

    node.verify(priv.findPrivacyGroup(groupMembership.size(), PARTICIPANT_ENCLAVE_KEY0));
  }

  @Test
  public void eeaSendRawTransactionSuccessShouldReturnPrivateTransactionHash()
      throws JsonProcessingException {
    final PrivateTransaction validSignedPrivateTransaction =
        getValidSignedPrivateTransaction(senderAddress);

    retrievePrivacyGroupEnclaveStub();
    sendEnclaveStub(PARTICIPANT_ENCLAVE_KEY1);
    receiveEnclaveStub(validSignedPrivateTransaction);

    node.verify(
        priv.eeaSendRawTransaction(
            getRLPOutput(validSignedPrivateTransaction).encoded().toHexString()));
  }

  @Test
  public void privGetTransactionCountSuccessShouldReturnExpectedTransactionCount()
      throws JsonProcessingException {
    final PrivateTransaction validSignedPrivateTransaction =
        getValidSignedPrivateTransaction(senderAddress);
    final String accountAddress = validSignedPrivateTransaction.getSender().toHexString();
    final BytesValueRLPOutput rlpOutput = getRLPOutput(validSignedPrivateTransaction);

    retrievePrivacyGroupEnclaveStub();
    sendEnclaveStub(PARTICIPANT_ENCLAVE_KEY1);
    receiveEnclaveStub(validSignedPrivateTransaction);

    node.verify(priv.getTransactionCount(accountAddress, PRIVACY_GROUP_ID, 0));
    final Hash transactionReceipt =
        node.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));

    node.verify(priv.getSuccessfulTransactionReceipt(transactionReceipt));
    node.verify(priv.getTransactionCount(accountAddress, PRIVACY_GROUP_ID, 1));
  }

  @Test
  public void privDistributeRawTransactionSuccessShouldReturnEnclaveKey()
      throws JsonProcessingException {
    final String enclaveResponseKeyBytes =
        Bytes.wrap(Bytes.fromBase64String(PARTICIPANT_ENCLAVE_KEY1)).toString();

    retrievePrivacyGroupEnclaveStub();
    sendEnclaveStub(PARTICIPANT_ENCLAVE_KEY1);

    node.verify(
        priv.distributeRawTransaction(
            getRLPOutput(getValidSignedPrivateTransaction(senderAddress)).encoded().toHexString(),
            enclaveResponseKeyBytes));
  }

  @Test
  public void privGetTransactionReceiptSuccessShouldReturnTransactionReceiptAfterMined()
      throws JsonProcessingException {
    final PrivateTransaction validSignedPrivateTransaction =
        getValidSignedPrivateTransaction(senderAddress);
    final BytesValueRLPOutput rlpOutput = getRLPOutput(validSignedPrivateTransaction);

    retrievePrivacyGroupEnclaveStub();
    sendEnclaveStub(PARTICIPANT_ENCLAVE_KEY1);
    receiveEnclaveStub(validSignedPrivateTransaction);

    final Hash transactionReceipt =
        node.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));

    node.verify(priv.getSuccessfulTransactionReceipt(transactionReceipt));
  }

  @Test
  public void privGetEeaTransactionCountSuccessShouldReturnExpectedTransactionCount()
      throws JsonProcessingException {
    final PrivateTransaction validSignedPrivateTransaction =
        getValidLegacySignedPrivateTransaction(senderAddress);
    final String accountAddress = validSignedPrivateTransaction.getSender().toHexString();
    final String privateTxRlp = getRLPOutput(validSignedPrivateTransaction).encoded().toHexString();

    retrieveEeaPrivacyGroupEnclaveStub(validSignedPrivateTransaction);
    sendEnclaveStub(
        Bytes32.ZERO.toBase64String()); // can be any value, as we are stubbing the enclave
    receiveEnclaveStubEea(validSignedPrivateTransaction);

    final String privateFrom = validSignedPrivateTransaction.getPrivateFrom().toBase64String();
    final String[] privateFor =
        validSignedPrivateTransaction.getPrivateFor().orElseThrow().stream()
            .map(Bytes::toBase64String)
            .toArray(String[]::new);
    node.verify(priv.getEeaTransactionCount(accountAddress, privateFrom, privateFor, 0));

    final Hash transactionHash = node.execute(privacyTransactions.sendRawTransaction(privateTxRlp));

    node.verify(priv.getSuccessfulTransactionReceipt(transactionHash));

    node.verify(priv.getEeaTransactionCount(accountAddress, privateFrom, privateFor, 1));
  }

  @Nonnull
  private Bytes32 getPrivacyGroupIdFromEeaTransaction(
      final PrivateTransaction validSignedPrivateTransaction) {
    return PrivacyGroupUtil.calculateEeaPrivacyGroupId(
        validSignedPrivateTransaction.getPrivateFrom(),
        validSignedPrivateTransaction.getPrivateFor().get());
  }

  private void findPrivacyGroupEnclaveStub(final List<PrivacyGroup> groupMembership)
      throws JsonProcessingException {
    final String findGroupResponse = mapper.writeValueAsString(groupMembership);
    stubFor(post("/findPrivacyGroup").willReturn(ok(findGroupResponse)));
  }

  private void createPrivacyGroupEnclaveStub() throws JsonProcessingException {
    final String createGroupResponse =
        mapper.writeValueAsString(testPrivacyGroup(emptyList(), PrivacyGroup.Type.PANTHEON));
    stubFor(post("/createPrivacyGroup").willReturn(ok(createGroupResponse)));
  }

  private void deletePrivacyGroupEnclaveStub() throws JsonProcessingException {
    final String deleteGroupResponse = mapper.writeValueAsString(PRIVACY_GROUP_ID);
    stubFor(post("/deletePrivacyGroup").willReturn(ok(deleteGroupResponse)));
  }

  private void retrievePrivacyGroupEnclaveStub() throws JsonProcessingException {
    final String retrieveGroupResponse =
        mapper.writeValueAsString(
            testPrivacyGroup(
                List.of(PARTICIPANT_ENCLAVE_KEY0, PARTICIPANT_ENCLAVE_KEY1),
                PrivacyGroup.Type.PANTHEON));
    stubFor(post("/retrievePrivacyGroup").willReturn(ok(retrieveGroupResponse)));
  }

  private void retrieveEeaPrivacyGroupEnclaveStub(final PrivateTransaction tx)
      throws JsonProcessingException {
    final ArrayList<String> members = new ArrayList<>();
    members.add(tx.getPrivateFrom().toBase64String());
    members.addAll(
        tx.getPrivateFor().orElseThrow().stream()
            .map(Bytes::toBase64String)
            .collect(Collectors.toList()));
    final String retrieveGroupResponse =
        mapper.writeValueAsString(testPrivacyGroupEea(members, PrivacyGroup.Type.LEGACY));
    stubFor(post("/retrievePrivacyGroup").willReturn(ok(retrieveGroupResponse)));
  }

  private void sendEnclaveStub(final String testKey) throws JsonProcessingException {
    final String sendResponse = mapper.writeValueAsString(new SendResponse(testKey));
    stubFor(post("/send").willReturn(ok(sendResponse)));
  }

  private void receiveEnclaveStub(final PrivateTransaction privTx) throws JsonProcessingException {
    final BytesValueRLPOutput rlpOutput = getRLPOutputForReceiveResponse(privTx);
    final String senderKey = privTx.getPrivateFrom().toBase64String();
    final String receiveResponse =
        mapper.writeValueAsString(
            new ReceiveResponse(
                rlpOutput.encoded().toBase64String().getBytes(UTF_8), PRIVACY_GROUP_ID, senderKey));
    stubFor(post("/receive").willReturn(ok(receiveResponse)));
  }

  private void receiveEnclaveStubEea(final PrivateTransaction privTx)
      throws JsonProcessingException {
    final BytesValueRLPOutput rlpOutput = getRLPOutputForReceiveResponse(privTx);
    final String senderKey = privTx.getPrivateFrom().toBase64String();
    final String receiveResponse =
        mapper.writeValueAsString(
            new ReceiveResponse(
                rlpOutput.encoded().toBase64String().getBytes(UTF_8),
                getPrivacyGroupIdFromEeaTransaction(privTx).toBase64String(),
                senderKey));
    stubFor(post("/receive").willReturn(ok(receiveResponse)));
  }

  private BytesValueRLPOutput getRLPOutputForReceiveResponse(
      final PrivateTransaction privateTransaction) {
    final BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlpo);
    return bvrlpo;
  }

  private BytesValueRLPOutput getRLPOutput(final PrivateTransaction privateTransaction) {
    final BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlpo);
    return bvrlpo;
  }

  private PrivacyGroup testPrivacyGroup(
      final List<String> groupMembers, final PrivacyGroup.Type groupType) {
    return new PrivacyGroup(PRIVACY_GROUP_ID, groupType, "test", "testGroup", groupMembers);
  }

  private PrivacyGroup testPrivacyGroupEea(
      final List<String> groupMembers, final PrivacyGroup.Type groupType) {
    final Bytes32 privacyGroupId =
        PrivacyGroupUtil.calculateEeaPrivacyGroupId(
            Bytes.fromBase64String(groupMembers.get(0)),
            groupMembers.stream()
                .map(gm -> Bytes.fromBase64String(gm))
                .collect(Collectors.toList()));
    return new PrivacyGroup(
        privacyGroupId.toBase64String(), groupType, "test", "testGroup", groupMembers);
  }

  private static PrivateTransaction getValidSignedPrivateTransaction(final Address senderAddress) {
    return PrivateTransaction.builder()
        .nonce(0)
        .gasPrice(Wei.ZERO)
        .gasLimit(3000000)
        .to(null)
        .value(Wei.ZERO)
        .payload(Bytes.wrap(new byte[] {}))
        .sender(senderAddress)
        .chainId(BigInteger.valueOf(1337))
        .privateFrom(Bytes.fromBase64String(PARTICIPANT_ENCLAVE_KEY0))
        .restriction(Restriction.RESTRICTED)
        .privacyGroupId(Bytes.fromBase64String(PRIVACY_GROUP_ID))
        .signAndBuild(TEST_KEY);
  }

  private static PrivateTransaction getValidLegacySignedPrivateTransaction(
      final Address senderAddress) {
    return PrivateTransaction.builder()
        .nonce(0)
        .gasPrice(Wei.ZERO)
        .gasLimit(3000000)
        .to(null)
        .value(Wei.ZERO)
        .payload(Bytes.wrap(new byte[] {}))
        .sender(senderAddress)
        .chainId(BigInteger.valueOf(1337))
        .privateFrom(LEAGCY_PRIVATE_FROM)
        .privateFor(LEGACY_PRIVATE_FOR)
        .restriction(Restriction.RESTRICTED)
        .signAndBuild(TEST_KEY);
  }
}
