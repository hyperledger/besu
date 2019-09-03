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

import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyNode;
import tech.pegasys.pantheon.tests.web3j.generated.EventEmitter;

import java.math.BigInteger;

import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.eea.response.PrivateTransactionReceipt;

public class PrivacyClusterAcceptanceTest extends PrivacyAcceptanceTestBase {

  private static final long POW_CHAIN_ID = 2018;

  private PrivacyNode alice;
  private PrivacyNode bob;
  private PrivacyNode charlie;

  @Before
  public void setUp() throws Exception {
    alice =
        privacyPantheon.createPrivateTransactionEnabledMinerNode(
            "node1", privacyAccountResolver.resolve(0));
    bob =
        privacyPantheon.createPrivateTransactionEnabledNode(
            "node2", privacyAccountResolver.resolve(1));
    charlie =
        privacyPantheon.createPrivateTransactionEnabledNode(
            "node3", privacyAccountResolver.resolve(2));
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
                POW_CHAIN_ID,
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
                POW_CHAIN_ID,
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

    final EventEmitter firstEventEmitter =
        alice.execute(
            privateContractTransactions.createSmartContract(
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                POW_CHAIN_ID,
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
                POW_CHAIN_ID,
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
                POW_CHAIN_ID,
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
                POW_CHAIN_ID,
                charlie.getEnclaveKey(),
                alice.getEnclaveKey(),
                bob.getEnclaveKey()));

    // alice gets receipt from charlie's interaction
    final PrivateTransactionReceipt firstExpectedReceipt =
        alice.execute(privacyTransactions.getPrivateTransactionReceipt(firstTransactionHash));

    // verify bob and charlie have access to the same receipt
    bob.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            firstTransactionHash, firstExpectedReceipt));
    charlie.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            firstTransactionHash, firstExpectedReceipt));

    // alice deploys second contract
    final String secondDeployedAddress = "0xebf56429e6500e84442467292183d4d621359838";

    final EventEmitter secondEventEmitter =
        alice.execute(
            privateContractTransactions.createSmartContract(
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                POW_CHAIN_ID,
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
                POW_CHAIN_ID,
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
