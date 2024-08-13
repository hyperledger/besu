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
package org.hyperledger.besu.tests.acceptance.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.web3j.utils.Restriction.UNRESTRICTED;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.ParameterizedEnclaveTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.web3j.generated.SimpleStorage;
import org.hyperledger.enclave.testutil.EnclaveEncryptorType;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.web3j.utils.Restriction;

public class PrivTraceTransactionAcceptanceTest extends ParameterizedEnclaveTestBase {

  private final PrivacyNode node;

  private final PrivacyNode wrongNode;

  public PrivTraceTransactionAcceptanceTest(
      final Restriction restriction,
      final EnclaveType enclaveType,
      final EnclaveEncryptorType enclaveEncryptorType)
      throws IOException {

    super(restriction, enclaveType, enclaveEncryptorType);

    node =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            restriction + "-node",
            PrivacyAccountResolver.ALICE.resolve(enclaveEncryptorType),
            enclaveType,
            Optional.empty(),
            false,
            false,
            restriction == UNRESTRICTED);

    wrongNode =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            restriction + "-node",
            PrivacyAccountResolver.BOB.resolve(enclaveEncryptorType),
            enclaveType,
            Optional.empty(),
            false,
            false,
            restriction == UNRESTRICTED);

    privacyCluster.start(node);
    privacyCluster.start(wrongNode);
  }

  @Test
  public void getTransactionTrace() throws JsonProcessingException {
    final String privacyGroupId = createPrivacyGroup();
    final SimpleStorage simpleStorageContract = deploySimpleStorageContract(privacyGroupId);

    Hash transactionHash =
        Hash.fromHexString(doTransaction(privacyGroupId, simpleStorageContract, 0));

    final String result =
        node.execute(privacyTransactions.privTraceTransaction(privacyGroupId, transactionHash));

    assertThat(result).isNotNull();
    ObjectMapper mapper = new ObjectMapper();

    JsonNode rootNode = mapper.readTree(result);
    JsonNode resultNode = rootNode.get("result");

    assertThat(resultNode).isNotNull();
    assertThat(resultNode.isArray()).isTrue();
    assertThat(resultNode.size()).isGreaterThan(0);

    JsonNode trace = resultNode.get(0);
    assertThat(trace.get("action").get("callType").asText()).isEqualTo("call");
    assertThat(trace.get("action").get("from").asText()).isEqualTo(node.getAddress().toString());
    assertThat(trace.get("action").get("input").asText()).startsWith("0x60fe47b1");
    assertThat(trace.get("action").get("to").asText())
        .isEqualTo(simpleStorageContract.getContractAddress());
    assertThat(trace.get("action").get("value").asText()).isEqualTo("0x0");
    assertThat(trace.get("blockHash").asText()).isNotEmpty();
    assertThat(trace.get("blockNumber").asInt()).isGreaterThan(0);
    assertThat(trace.get("transactionHash").asText()).isEqualTo(transactionHash.toString());
    assertThat(trace.get("type").asText()).isEqualTo("call");

    final String wrongPrivacyGroupId = createWrongPrivacyGroup();

    final String resultEmpty =
        wrongNode.execute(
            privacyTransactions.privTraceTransaction(wrongPrivacyGroupId, transactionHash));

    ObjectMapper mapperEmpty = new ObjectMapper();

    JsonNode rootNodeEmpty = mapperEmpty.readTree(resultEmpty);
    JsonNode resultNodeEmpty = rootNodeEmpty.get("result");

    assertThat(resultNodeEmpty).isNotNull();
    assertThat(resultNodeEmpty.isArray()).isTrue();
    assertThat(resultNodeEmpty.isEmpty()).isTrue();

    final String resultWrongHash =
        wrongNode.execute(privacyTransactions.privTraceTransaction(privacyGroupId, Hash.EMPTY));

    ObjectMapper mapperWrongHash = new ObjectMapper();

    JsonNode rootNodeWrongHash = mapperWrongHash.readTree(resultWrongHash);
    JsonNode resultNodeWrongHash = rootNodeWrongHash.get("result");

    assertThat(resultNodeWrongHash).isNotNull();
    assertThat(resultNodeWrongHash.isArray()).isTrue();
    assertThat(resultNodeWrongHash.isEmpty()).isTrue();
  }

  private String createPrivacyGroup() {
    return node.execute(createPrivacyGroup("myGroupName", "my group description", node));
  }

  private String createWrongPrivacyGroup() {
    return wrongNode.execute(createPrivacyGroup("myGroupName", "my group description", wrongNode));
  }

  private SimpleStorage deploySimpleStorageContract(final String privacyGroupId) {
    final SimpleStorage simpleStorage =
        node.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                SimpleStorage.class,
                node.getTransactionSigningKey(),
                restriction,
                node.getEnclaveKey(),
                privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(
            simpleStorage.getContractAddress(), node.getAddress().toString())
        .verify(simpleStorage);

    return simpleStorage;
  }

  private String doTransaction(
      final String privacyGroupId, final SimpleStorage simpleStorageContract, final int value) {
    return node.execute(
        privateContractTransactions.callSmartContractWithPrivacyGroupId(
            simpleStorageContract.getContractAddress(),
            simpleStorageContract.set(BigInteger.valueOf(value)).encodeFunctionCall(),
            node.getTransactionSigningKey(),
            restriction,
            node.getEnclaveKey(),
            privacyGroupId));
  }
}
