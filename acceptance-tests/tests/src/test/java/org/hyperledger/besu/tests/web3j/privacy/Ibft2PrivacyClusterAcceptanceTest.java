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
package org.hyperledger.besu.tests.web3j.privacy;

import org.hyperledger.besu.tests.acceptance.dsl.privacy.ParameterizedEnclaveTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.testcontainers.containers.Network;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;

public class Ibft2PrivacyClusterAcceptanceTest extends ParameterizedEnclaveTestBase {
  public Ibft2PrivacyClusterAcceptanceTest(final EnclaveType enclaveType) {
    super(enclaveType);
  }

  private static final long IBFT2_CHAIN_ID = 4;

  private PrivacyNode alice;
  private PrivacyNode bob;
  private PrivacyNode charlie;

  @Before
  public void setUp() throws Exception {
    final Network containerNetwork = Network.newNetwork();

    alice =
        privacyBesu.createIbft2NodePrivacyEnabled(
            "node1", privacyAccountResolver.resolve(0), enclaveType, Optional.of(containerNetwork));
    bob =
        privacyBesu.createIbft2NodePrivacyEnabled(
            "node2", privacyAccountResolver.resolve(1), enclaveType, Optional.of(containerNetwork));
    charlie =
        privacyBesu.createIbft2NodePrivacyEnabled(
            "node3", privacyAccountResolver.resolve(2), enclaveType, Optional.of(containerNetwork));
    privacyCluster.start(alice, bob, charlie);
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
                IBFT2_CHAIN_ID,
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
                IBFT2_CHAIN_ID,
                alice.getEnclaveKey(),
                bob.getEnclaveKey()));

    final PrivateTransactionReceipt expectedReceipt =
        alice.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));

    bob.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            transactionHash, expectedReceipt));
    charlie.verify(privateTransactionVerifier.noPrivateTransactionReceipt(transactionHash));
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
                IBFT2_CHAIN_ID,
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
                IBFT2_CHAIN_ID,
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
                IBFT2_CHAIN_ID,
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
                IBFT2_CHAIN_ID,
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
                IBFT2_CHAIN_ID,
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
                IBFT2_CHAIN_ID,
                bob.getEnclaveKey(),
                alice.getEnclaveKey()));

    // alice gets receipt from bob's interaction
    final PrivateTransactionReceipt secondExpectedReceipt =
        alice.execute(privacyTransactions.getPrivateTransactionReceipt(secondTransactionHash));

    bob.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            secondTransactionHash, secondExpectedReceipt));

    // charlie cannot see the receipt
    charlie.verify(privateTransactionVerifier.noPrivateTransactionReceipt(secondTransactionHash));
  }
}
