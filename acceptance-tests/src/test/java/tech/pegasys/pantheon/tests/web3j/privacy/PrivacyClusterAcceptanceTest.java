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

import static tech.pegasys.pantheon.tests.acceptance.dsl.WaitUtils.waitFor;

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.SendRequest;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PrivacyClusterAcceptanceTest extends PrivateAcceptanceTestBase {
  // Contract address is generated from sender address and transaction nonce and privacy group id
  protected static final Address CONTRACT_ADDRESS =
      Address.fromHexString("0x2f351161a80d74047316899342eedc606b13f9f8");

  protected static final String PUBLIC_KEY_1 = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  protected static final String PUBLIC_KEY_2 = "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";
  protected static final String PUBLIC_KEY_3 = "k2zXEin4Ip/qBGlRkJejnGWdP9cjkK+DAvKNW31L2C8=";
  private PantheonNode node1;
  private PantheonNode node2;
  private PantheonNode node3;
  private static OrionTestHarness enclave1;
  private static OrionTestHarness enclave2;
  private static OrionTestHarness enclave3;

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
  }

  @Test
  public void node2CanSeeContract() {

    final String transactionHash =
        node1.execute(transactions.deployPrivateSmartContract(getDeployEventEmitterCluster()));

    privateTransactionVerifier
        .validPrivateContractDeployed(CONTRACT_ADDRESS.toString())
        .verify(node2, transactionHash, PUBLIC_KEY_2);
  }

  @Test
  public void node2CanExecuteContract() {
    String transactionHash =
        node1.execute(transactions.deployPrivateSmartContract(getDeployEventEmitterCluster()));

    privateTransactionVerifier
        .validPrivateContractDeployed(CONTRACT_ADDRESS.toString())
        .verify(node2, transactionHash, PUBLIC_KEY_2);

    transactionHash =
        node2.execute(transactions.createPrivateRawTransaction(getExecuteStoreFuncCluster(0)));

    privateTransactionVerifier
        .validEventReturned("1000")
        .verify(node1, transactionHash, PUBLIC_KEY_1);
  }

  @Test
  public void node2CanSeePrivateTransactionReceipt() {
    String transactionHash =
        node1.execute(transactions.deployPrivateSmartContract(getDeployEventEmitterCluster()));

    privateTransactionVerifier
        .validPrivateContractDeployed(CONTRACT_ADDRESS.toString())
        .verify(node2, transactionHash, PUBLIC_KEY_2);

    transactionHash =
        node2.execute(transactions.createPrivateRawTransaction(getExecuteStoreFuncCluster(0)));

    privateTransactionVerifier
        .validEventReturned("1000")
        .verify(node1, transactionHash, PUBLIC_KEY_1);

    transactionHash =
        node2.execute(transactions.createPrivateRawTransaction(getExecuteGetFuncCluster(1)));

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
        node1.execute(transactions.deployPrivateSmartContract(getDeployEventEmitterCluster()));

    privateTransactionVerifier
        .noPrivateContractDeployed()
        .verify(node3, transactionHash, PUBLIC_KEY_3);
  }

  @Test
  public void node3CannotExecuteContract() {
    node1.execute(transactions.deployPrivateSmartContract(getDeployEventEmitterCluster()));

    final String transactionHash =
        node3.execute(transactions.createPrivateRawTransaction(getExecuteGetFuncClusterNode3()));

    privateTransactionVerifier.noValidOutputReturned().verify(node3, transactionHash, PUBLIC_KEY_3);
  }

  @After
  public void tearDown() {
    enclave1.getOrion().stop();
    enclave2.getOrion().stop();
    enclave3.getOrion().stop();
    cluster.stop();
  }
}
