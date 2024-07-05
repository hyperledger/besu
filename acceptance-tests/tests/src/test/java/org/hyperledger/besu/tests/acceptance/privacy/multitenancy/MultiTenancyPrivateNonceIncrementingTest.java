/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.plugin.data.Restriction;
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
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MultiTenancyPrivateNonceIncrementingTest extends AcceptanceTestBase {
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
  private static final String PARTICIPANT_ENCLAVE_KEY1 =
      "sgFkVOyFndZe/5SAZJO5UYbrl7pezHetveriBBWWnE8=";
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
            false,
            true);
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
  public void validateUnsuccessfulPrivateTransactionsNonceIncrementation()
      throws JsonProcessingException {
    executePrivateFailingTransaction(0, 0, 1);
    executePrivateValidTransaction(1, 1, 2);
    executePrivateFailingTransaction(2, 2, 3);
    executePrivateFailingTransaction(3, 3, 4);
    executePrivateValidTransaction(4, 4, 5);
  }

  private void executePrivateValidTransaction(
      final int nonce,
      final int expectedTransactionCountBeforeExecution,
      final int expectedTransactionCountAfterExecution)
      throws JsonProcessingException {
    final PrivateTransaction validSignedPrivateTransaction =
        getValidSignedPrivateTransaction(senderAddress, nonce);

    final String accountAddress = validSignedPrivateTransaction.getSender().toHexString();
    final BytesValueRLPOutput rlpOutput = getRLPOutput(validSignedPrivateTransaction);

    processEnclaveStub(validSignedPrivateTransaction);

    node.verify(
        priv.getTransactionCount(
            accountAddress, PRIVACY_GROUP_ID, expectedTransactionCountBeforeExecution));

    final Hash transactionReceipt =
        node.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));

    node.verify(priv.getSuccessfulTransactionReceipt(transactionReceipt));
    node.verify(
        priv.getTransactionCount(
            accountAddress, PRIVACY_GROUP_ID, expectedTransactionCountAfterExecution));
  }

  private void executePrivateFailingTransaction(
      final int nonce,
      final int expectedTransactionCountBeforeExecution,
      final int expectedTransactionCountAfterExecution)
      throws JsonProcessingException {
    final PrivateTransaction invalidSignedPrivateTransaction =
        getInvalidSignedPrivateTransaction(senderAddress, nonce);
    final String accountAddress = invalidSignedPrivateTransaction.getSender().toHexString();
    final BytesValueRLPOutput invalidTxRlp = getRLPOutput(invalidSignedPrivateTransaction);

    processEnclaveStub(invalidSignedPrivateTransaction);

    node.verify(
        priv.getTransactionCount(
            accountAddress, PRIVACY_GROUP_ID, expectedTransactionCountBeforeExecution));
    final Hash invalidTransactionReceipt =
        node.execute(privacyTransactions.sendRawTransaction(invalidTxRlp.encoded().toHexString()));

    node.verify(priv.getFailedTransactionReceipt(invalidTransactionReceipt));
    node.verify(
        priv.getTransactionCount(
            accountAddress, PRIVACY_GROUP_ID, expectedTransactionCountAfterExecution));
  }

  private void processEnclaveStub(final PrivateTransaction validSignedPrivateTransaction)
      throws JsonProcessingException {
    retrievePrivacyGroupEnclaveStub();
    sendEnclaveStub();
    receiveEnclaveStub(validSignedPrivateTransaction);
  }

  private void retrievePrivacyGroupEnclaveStub() throws JsonProcessingException {
    final String retrieveGroupResponse =
        mapper.writeValueAsString(
            createPrivacyGroup(
                List.of(PARTICIPANT_ENCLAVE_KEY0, PARTICIPANT_ENCLAVE_KEY1),
                PrivacyGroup.Type.PANTHEON));
    stubFor(post("/retrievePrivacyGroup").willReturn(ok(retrieveGroupResponse)));
  }

  private void sendEnclaveStub() throws JsonProcessingException {
    final String sendResponse =
        mapper.writeValueAsString(new SendResponse(PARTICIPANT_ENCLAVE_KEY1));
    stubFor(post("/send").willReturn(ok(sendResponse)));
  }

  private void receiveEnclaveStub(final PrivateTransaction privTx) throws JsonProcessingException {
    final BytesValueRLPOutput rlpOutput = getRLPOutput(privTx);
    final String senderKey = privTx.getPrivateFrom().toBase64String();
    final String receiveResponse =
        mapper.writeValueAsString(
            new ReceiveResponse(
                rlpOutput.encoded().toBase64String().getBytes(UTF_8), PRIVACY_GROUP_ID, senderKey));
    stubFor(post("/receive").willReturn(ok(receiveResponse)));
  }

  private BytesValueRLPOutput getRLPOutput(final PrivateTransaction privateTransaction) {
    final BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlpo);
    return bvrlpo;
  }

  private PrivacyGroup createPrivacyGroup(
      final List<String> groupMembers, final PrivacyGroup.Type groupType) {
    return new PrivacyGroup(PRIVACY_GROUP_ID, groupType, "test", "testGroup", groupMembers);
  }

  private static PrivateTransaction getInvalidSignedPrivateTransaction(
      final Address senderAddress, final int nonce) {
    return PrivateTransaction.builder()
        .nonce(nonce)
        .gasPrice(Wei.ZERO)
        .gasLimit(3000000)
        .to(null)
        .value(Wei.ZERO)
        .payload(Bytes.fromHexString("0x1234"))
        .sender(senderAddress)
        .chainId(BigInteger.valueOf(1337))
        .privateFrom(Bytes.fromBase64String(PARTICIPANT_ENCLAVE_KEY0))
        .restriction(Restriction.RESTRICTED)
        .privacyGroupId(Bytes.fromBase64String(PRIVACY_GROUP_ID))
        .signAndBuild(TEST_KEY);
  }

  private static PrivateTransaction getValidSignedPrivateTransaction(
      final Address senderAddress, final int nonce) {
    return PrivateTransaction.builder()
        .nonce(nonce)
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
}
