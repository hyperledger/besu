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
package tech.pegasys.pantheon.tests.web3j.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.pantheon.tests.acceptance.dsl.WaitUtils.waitFor;

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.SendRequest;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;

import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PrivacyClusterAcceptanceTest extends PrivateAcceptanceTestBase {
  // Contract address is generated from sender address and transaction nonce and privacy group id
  private static final Address CONTRACT_ADDRESS =
      Address.fromHexString("0x2f351161a80d74047316899342eedc606b13f9f8");

  private static final String PUBLIC_KEY_1 = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String PUBLIC_KEY_2 = "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";
  private static final String PUBLIC_KEY_3 = "k2zXEin4Ip/qBGlRkJejnGWdP9cjkK+DAvKNW31L2C8=";
  private SECP256K1.KeyPair keypair1 =
      SECP256K1.KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

  private SECP256K1.KeyPair keypair2 =
      SECP256K1.KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3", 16)));

  private SECP256K1.KeyPair keypair3 =
      SECP256K1.KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "ae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f", 16)));
  private PantheonNode node1;
  private PantheonNode node2;
  private PantheonNode node3;
  private static OrionTestHarness enclave1;
  private static OrionTestHarness enclave2;
  private static OrionTestHarness enclave3;

  private String deployContractFromNode1;
  private String storeValueFromNode2;
  private String getValueFromNode2;
  private String getValueFromNode3;

  @Before
  public void setUp() throws Exception {
    enclave1 = createEnclave("orion_key_0.pub", "orion_key_0.key");
    enclave2 = createEnclave("orion_key_1.pub", "orion_key_1.key", enclave1.nodeUrl());
    enclave3 = createEnclave("orion_key_2.pub", "orion_key_2.key", enclave2.nodeUrl());
    node1 =
        pantheon.createPrivateTransactionEnabledMinerNode(
            "node1", getPrivacyParams(enclave1), "key");
    node2 =
        pantheon.createPrivateTransactionEnabledMinerNode(
            "node2", getPrivacyParams(enclave2), "key1");
    node3 =
        pantheon.createPrivateTransactionEnabledNode("node3", getPrivacyParams(enclave3), "key2");

    cluster.start(node1, node2, node3);

    // Wait for enclave 1 and enclave 2 to connect
    Enclave orion1 = new Enclave(enclave1.clientUrl());
    SendRequest sendRequest1 =
        new SendRequest(
            "SGVsbG8sIFdvcmxkIQ==", enclave1.getPublicKeys().get(0), enclave2.getPublicKeys());
    waitFor(() -> orion1.send(sendRequest1));

    // Wait for enclave 2 and enclave 3 to connect
    Enclave orion2 = new Enclave(enclave2.clientUrl());
    SendRequest sendRequest2 =
        new SendRequest(
            "SGVsbG8sIFdvcmxkIQ==", enclave2.getPublicKeys().get(0), enclave3.getPublicKeys());
    waitFor(() -> orion2.send(sendRequest2));

    deployContractFromNode1 =
        PrivateAcceptanceTestBase.builder()
            .nonce(0)
            .from(node1.getAddress())
            .to(null)
            .privateFrom(BytesValue.wrap(PUBLIC_KEY_1.getBytes(UTF_8)))
            .privateFor(Lists.newArrayList(BytesValue.wrap(PUBLIC_KEY_2.getBytes(UTF_8))))
            .keyPair(keypair1)
            .build(TransactionType.CREATE_CONTRACT);

    storeValueFromNode2 =
        PrivateAcceptanceTestBase.builder()
            .nonce(0)
            .from(node2.getAddress())
            .to(CONTRACT_ADDRESS)
            .privateFrom(BytesValue.wrap(PUBLIC_KEY_2.getBytes(UTF_8)))
            .privateFor(Lists.newArrayList(BytesValue.wrap(PUBLIC_KEY_1.getBytes(UTF_8))))
            .keyPair(keypair2)
            .build(TransactionType.STORE);

    getValueFromNode2 =
        PrivateAcceptanceTestBase.builder()
            .nonce(1)
            .from(node2.getAddress())
            .to(CONTRACT_ADDRESS)
            .privateFrom(BytesValue.wrap(PUBLIC_KEY_2.getBytes(UTF_8)))
            .privateFor(Lists.newArrayList(BytesValue.wrap(PUBLIC_KEY_1.getBytes(UTF_8))))
            .keyPair(keypair2)
            .build(TransactionType.GET);

    getValueFromNode3 =
        PrivateAcceptanceTestBase.builder()
            .nonce(0)
            .from(node3.getAddress())
            .to(CONTRACT_ADDRESS)
            .privateFrom(BytesValue.wrap(PUBLIC_KEY_3.getBytes(UTF_8)))
            .privateFor(Lists.newArrayList(BytesValue.wrap(PUBLIC_KEY_2.getBytes(UTF_8))))
            .keyPair(keypair3)
            .build(TransactionType.GET);
  }

  @Test
  public void node2CanSeeContract() {

    String transactionHash =
        node1.execute(transactions.deployPrivateSmartContract(deployContractFromNode1));

    privateTransactionVerifier
        .validPrivateContractDeployed(CONTRACT_ADDRESS.toString())
        .verify(node2, transactionHash, PUBLIC_KEY_2);
  }

  @Test
  public void node2CanExecuteContract() {
    String transactionHash =
        node1.execute(transactions.deployPrivateSmartContract(deployContractFromNode1));

    privateTransactionVerifier
        .validPrivateContractDeployed(CONTRACT_ADDRESS.toString())
        .verify(node2, transactionHash, PUBLIC_KEY_2);

    transactionHash = node2.execute(transactions.createPrivateRawTransaction(storeValueFromNode2));

    privateTransactionVerifier
        .validEventReturned("1000")
        .verify(node1, transactionHash, PUBLIC_KEY_1);
  }

  @Test
  public void node2CanSeePrivateTransactionReceipt() {
    String transactionHash =
        node1.execute(transactions.deployPrivateSmartContract(deployContractFromNode1));

    privateTransactionVerifier
        .validPrivateContractDeployed(CONTRACT_ADDRESS.toString())
        .verify(node2, transactionHash, PUBLIC_KEY_2);

    transactionHash = node2.execute(transactions.createPrivateRawTransaction(storeValueFromNode2));

    privateTransactionVerifier
        .validEventReturned("1000")
        .verify(node1, transactionHash, PUBLIC_KEY_1);

    transactionHash = node2.execute(transactions.createPrivateRawTransaction(getValueFromNode2));

    privateTransactionVerifier
        .validOutputReturned("1000")
        .verify(node2, transactionHash, PUBLIC_KEY_2);

    privateTransactionVerifier
        .validOutputReturned("1000")
        .verify(node1, transactionHash, PUBLIC_KEY_1);
  }

  @Test
  public void node3CannotSeeContract() {
    final String transactionHash =
        node1.execute(transactions.deployPrivateSmartContract(deployContractFromNode1));

    privateTransactionVerifier
        .noPrivateContractDeployed()
        .verify(node3, transactionHash, PUBLIC_KEY_3);
  }

  @Test
  public void node3CannotExecuteContract() {
    node1.execute(transactions.deployPrivateSmartContract(deployContractFromNode1));

    final String transactionHash =
        node3.execute(transactions.createPrivateRawTransaction(getValueFromNode3));

    privateTransactionVerifier.noValidOutputReturned().verify(node3, transactionHash, PUBLIC_KEY_3);
  }

  @Test(expected = RuntimeException.class)
  public void node2ExpectError() {
    node1.execute(transactions.deployPrivateSmartContract(deployContractFromNode1));

    String invalidStoreValueFromNode2 =
        PrivateAcceptanceTestBase.builder()
            .nonce(0)
            .from(node2.getAddress())
            .to(CONTRACT_ADDRESS)
            .privateFrom(BytesValue.wrap(PUBLIC_KEY_1.getBytes(UTF_8))) // wrong public key
            .privateFor(Lists.newArrayList(BytesValue.wrap(PUBLIC_KEY_2.getBytes(UTF_8))))
            .keyPair(keypair2)
            .build(TransactionType.STORE);

    node2.execute(transactions.createPrivateRawTransaction(invalidStoreValueFromNode2));
  }

  @After
  public void tearDown() {
    enclave1.getOrion().stop();
    enclave2.getOrion().stop();
    enclave3.getOrion().stop();
    cluster.stop();
  }
}
