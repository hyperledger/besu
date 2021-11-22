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
package org.hyperledger.besu.tests.acceptance.privacy;

import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.bft.ConsensusType;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.Network;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.utils.Restriction;

@RunWith(Parameterized.class)
public class BftPrivacyClusterAcceptanceTest extends PrivacyAcceptanceTestBase {
  private final BftPrivacyType bftPrivacyType;

  public static class BftPrivacyType {
    private final EnclaveType enclaveType;
    private final ConsensusType consensusType;
    private final Restriction restriction;

    public BftPrivacyType(
        final EnclaveType enclaveType,
        final ConsensusType consensusType,
        final Restriction restriction) {
      this.enclaveType = enclaveType;
      this.consensusType = consensusType;
      this.restriction = restriction;
    }

    @Override
    public String toString() {
      return String.join(
          ",", enclaveType.toString(), consensusType.toString(), restriction.toString());
    }
  }

  public BftPrivacyClusterAcceptanceTest(final BftPrivacyType bftPrivacyType) {
    this.bftPrivacyType = bftPrivacyType;
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<BftPrivacyType> bftPrivacyTypes() {
    final List<BftPrivacyType> bftPrivacyTypes = new ArrayList<>();
    for (EnclaveType x : EnclaveType.valuesForTests()) {
      for (ConsensusType consensusType : ConsensusType.values()) {
        bftPrivacyTypes.add(new BftPrivacyType(x, consensusType, Restriction.RESTRICTED));
      }
    }

    for (ConsensusType consensusType : ConsensusType.values()) {
      bftPrivacyTypes.add(
          new BftPrivacyType(EnclaveType.NOOP, consensusType, Restriction.UNRESTRICTED));
    }

    return bftPrivacyTypes;
  }

  private PrivacyNode alice;
  private PrivacyNode bob;
  private PrivacyNode charlie;

  @Before
  public void setUp() throws Exception {
    final Network containerNetwork = Network.newNetwork();

    alice = createNode(containerNetwork, "node1", 0);
    bob = createNode(containerNetwork, "node2", 1);
    charlie = createNode(containerNetwork, "node3", 2);

    privacyCluster.start(alice, bob, charlie);
  }

  private PrivacyNode createNode(
      final Network containerNetwork, final String nodeName, final int privacyAccount)
      throws IOException {
    if (bftPrivacyType.consensusType == ConsensusType.IBFT2) {
      return privacyBesu.createIbft2NodePrivacyEnabled(
          nodeName,
          privacyAccountResolver.resolve(privacyAccount),
          true,
          bftPrivacyType.enclaveType,
          Optional.of(containerNetwork),
          false,
          false,
          bftPrivacyType.restriction == Restriction.UNRESTRICTED,
          "0xAA");
    } else if (bftPrivacyType.consensusType == ConsensusType.QBFT) {
      return privacyBesu.createQbftNodePrivacyEnabled(
          nodeName,
          privacyAccountResolver.resolve(privacyAccount),
          bftPrivacyType.enclaveType,
          Optional.of(containerNetwork),
          false,
          false,
          bftPrivacyType.restriction == Restriction.UNRESTRICTED,
          "0xAA");
    } else {
      throw new IllegalStateException("Unknown consensus type " + bftPrivacyType.consensusType);
    }
  }

  @Test
  public void onlyAliceAndBobCanExecuteContract() {
    // Contract address is generated from sender address and transaction nonce
    final String contractAddress = "0xebf56429e6500e84442467292183d4d621359838";

    final EventEmitter eventEmitter =
        alice.execute(
            privateContractTransactions.createSmartContract(
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                alice.getEnclaveKey(),
                bob.getEnclaveKey()));

    privateContractVerifier
        .validPrivateContractDeployed(contractAddress, alice.getAddress().toString())
        .verify(eventEmitter);

    final String transactionHash =
        alice.execute(
            privateContractTransactions.callSmartContract(
                eventEmitter.getContractAddress(),
                eventEmitter.store(BigInteger.ONE).encodeFunctionCall(),
                alice.getTransactionSigningKey(),
                bftPrivacyType.restriction,
                alice.getEnclaveKey(),
                bob.getEnclaveKey()));

    final PrivateTransactionReceipt expectedReceipt =
        alice.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));

    bob.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            transactionHash, expectedReceipt));

    if (bftPrivacyType.restriction != Restriction.UNRESTRICTED) {
      charlie.verify(privateTransactionVerifier.noPrivateTransactionReceipt(transactionHash));
    }
  }

  @Test
  public void aliceCanDeployMultipleTimesInSingleGroup() {
    final String firstDeployedAddress = "0xebf56429e6500e84442467292183d4d621359838";

    privacyCluster.stopNode(charlie);

    final EventEmitter firstEventEmitter =
        alice.execute(
            privateContractTransactions.createSmartContract(
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                alice.getEnclaveKey(),
                bob.getEnclaveKey()));

    privateContractVerifier
        .validPrivateContractDeployed(firstDeployedAddress, alice.getAddress().toString())
        .verify(firstEventEmitter);

    final String secondDeployedAddress = "0x10f807f8a905da5bd319196da7523c6bd768690f";

    final EventEmitter secondEventEmitter =
        alice.execute(
            privateContractTransactions.createSmartContract(
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                alice.getEnclaveKey(),
                bob.getEnclaveKey()));

    privateContractVerifier
        .validPrivateContractDeployed(secondDeployedAddress, alice.getAddress().toString())
        .verify(secondEventEmitter);
  }

  @Test
  public void canInteractWithMultiplePrivacyGroups() {
    // alice deploys contract
    final String firstDeployedAddress = "0xff206d21150a8da5b83629d8a722f3135ed532b1";

    final EventEmitter firstEventEmitter =
        alice.execute(
            privateContractTransactions.createSmartContract(
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                alice.getEnclaveKey(),
                bob.getEnclaveKey(),
                charlie.getEnclaveKey()));

    privateContractVerifier
        .validPrivateContractDeployed(firstDeployedAddress, alice.getAddress().toString())
        .verify(firstEventEmitter);

    // charlie interacts with contract
    final String firstTransactionHash =
        charlie.execute(
            privateContractTransactions.callSmartContract(
                firstEventEmitter.getContractAddress(),
                firstEventEmitter.store(BigInteger.ONE).encodeFunctionCall(),
                charlie.getTransactionSigningKey(),
                bftPrivacyType.restriction,
                charlie.getEnclaveKey(),
                alice.getEnclaveKey(),
                bob.getEnclaveKey()));

    // alice gets receipt from charlie's interaction
    final PrivateTransactionReceipt aliceReceipt =
        alice.execute(privacyTransactions.getPrivateTransactionReceipt(firstTransactionHash));

    // verify bob and charlie have access to the same receipt
    bob.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            firstTransactionHash, aliceReceipt));
    charlie.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            firstTransactionHash, aliceReceipt));

    // alice deploys second contract
    final String secondDeployedAddress = "0xebf56429e6500e84442467292183d4d621359838";

    final EventEmitter secondEventEmitter =
        alice.execute(
            privateContractTransactions.createSmartContract(
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                alice.getEnclaveKey(),
                bob.getEnclaveKey()));

    privateContractVerifier
        .validPrivateContractDeployed(secondDeployedAddress, alice.getAddress().toString())
        .verify(secondEventEmitter);

    // bob interacts with contract
    final String secondTransactionHash =
        bob.execute(
            privateContractTransactions.callSmartContract(
                secondEventEmitter.getContractAddress(),
                secondEventEmitter.store(BigInteger.ONE).encodeFunctionCall(),
                bob.getTransactionSigningKey(),
                bftPrivacyType.restriction,
                bob.getEnclaveKey(),
                alice.getEnclaveKey()));

    // alice gets receipt from bob's interaction
    final PrivateTransactionReceipt secondExpectedReceipt =
        alice.execute(privacyTransactions.getPrivateTransactionReceipt(secondTransactionHash));

    bob.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            secondTransactionHash, secondExpectedReceipt));

    // charlie cannot see the receipt
    if (bftPrivacyType.restriction != Restriction.UNRESTRICTED) {
      charlie.verify(privateTransactionVerifier.noPrivateTransactionReceipt(secondTransactionHash));
    }
  }
}
