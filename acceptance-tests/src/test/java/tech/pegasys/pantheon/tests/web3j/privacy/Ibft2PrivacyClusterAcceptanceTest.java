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

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyNet;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.PrivateTransactionBuilder;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.PrivateTransactionBuilder.TransactionType;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class Ibft2PrivacyClusterAcceptanceTest extends PrivacyAcceptanceTestBase {
  private static final String CONTRACT_NAME = "Event Emmiter";

  private EventEmitterHarness eventEmitterHarness;
  private PrivacyNet privacyNet;

  @Before
  public void setUp() throws Exception {
    privacyNet =
        PrivacyNet.builder(privacy, privacyPantheon, cluster, false)
            .addMinerNode("Alice")
            .addMinerNode("Bob")
            .addMinerNode("Charlie")
            .build();
    privacyNet.startPrivacyNet();
    eventEmitterHarness =
        new EventEmitterHarness(
            privateTransactionBuilder,
            privacyNet,
            privateTransactions,
            privateTransactionVerifier,
            eea);
  }

  @Test
  public void node2CanSeeContract() {
    eventEmitterHarness.deploy(CONTRACT_NAME, "Alice", "Bob");
  }

  @Test
  public void node2CanExecuteContract() {
    eventEmitterHarness.deploy(CONTRACT_NAME, "Alice", "Bob");
    eventEmitterHarness.store(CONTRACT_NAME, "Bob", "Alice");
  }

  @Test
  public void node2CanSeePrivateTransactionReceipt() {
    eventEmitterHarness.deploy(CONTRACT_NAME, "Alice", "Bob");
    eventEmitterHarness.store(CONTRACT_NAME, "Bob", "Alice");
    eventEmitterHarness.get(CONTRACT_NAME, "Bob", "Alice");
  }

  @Test(expected = RuntimeException.class)
  public void node2ExpectError() {
    eventEmitterHarness.deploy(CONTRACT_NAME, "Alice", "Bob");

    String invalidStoreValueFromNode2 =
        PrivateTransactionBuilder.builder()
            .nonce(0)
            .from(privacyNet.getPantheon("Bob").getAddress())
            .to(Address.fromHexString(eventEmitterHarness.resolveContractAddress(CONTRACT_NAME)))
            .privateFrom(
                BytesValue.wrap(
                    privacyNet
                        .getEnclave("Alice")
                        .getPublicKeys()
                        .get(0)
                        .getBytes(UTF_8))) // wrong public key
            .privateFor(
                Lists.newArrayList(
                    BytesValue.wrap(
                        privacyNet.getEnclave("Bob").getPublicKeys().get(0).getBytes(UTF_8))))
            .keyPair(privacyNet.getPantheon("Bob").keyPair())
            .build(TransactionType.STORE);

    privacyNet
        .getPantheon("Bob")
        .execute(privateTransactions.createPrivateRawTransaction(invalidStoreValueFromNode2));
  }

  @Test
  public void node1CanDeployMultipleTimes() {

    eventEmitterHarness.deploy(CONTRACT_NAME, "Alice", "Bob");
    eventEmitterHarness.store(CONTRACT_NAME, "Bob", "Alice");

    final String secondContract = "Event Emitter 2";

    eventEmitterHarness.deploy(secondContract, "Alice", "Bob");
    eventEmitterHarness.store(secondContract, "Bob", "Alice");
  }

  @Test
  public void node1CanInteractWithMultiplePrivacyGroups() {

    eventEmitterHarness.deploy(CONTRACT_NAME, "Alice", "Bob", "Charlie");
    eventEmitterHarness.store(CONTRACT_NAME, "Alice", "Bob", "Charlie");

    final String secondContract = "Event Emitter 2";

    eventEmitterHarness.store(
        secondContract,
        privateTransactionVerifier.noValidEventReturned(),
        privateTransactionVerifier.noValidEventReturned(),
        "Alice",
        "Bob");
    eventEmitterHarness.deploy(secondContract, "Alice", "Bob");
    eventEmitterHarness.store(secondContract, "Alice", "Bob");
    eventEmitterHarness.get(secondContract, "Alice", "Bob");
  }

  @After
  public void tearDown() {
    privacyNet.stopPrivacyNet();
  }
}
