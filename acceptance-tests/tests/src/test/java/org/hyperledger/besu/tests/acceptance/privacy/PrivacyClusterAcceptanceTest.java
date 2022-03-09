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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.DEFAULT_PRIVACY;
import static org.web3j.utils.Restriction.RESTRICTED;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.testcontainers.containers.Network;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.protocol.eea.crypto.PrivateTransactionEncoder;
import org.web3j.protocol.eea.crypto.RawPrivateTransaction;
import org.web3j.utils.Base64String;
import org.web3j.utils.Numeric;

@RunWith(Parameterized.class)
public class PrivacyClusterAcceptanceTest extends PrivacyAcceptanceTestBase {

  private final PrivacyNode alice;
  private final PrivacyNode bob;
  private final PrivacyNode charlie;
  private final Vertx vertx = Vertx.vertx();
  private final EnclaveFactory enclaveFactory = new EnclaveFactory(vertx);

  @Parameters(name = "{0}")
  public static Collection<EnclaveType> enclaveTypes() {
    return EnclaveType.valuesForTests();
  }

  public PrivacyClusterAcceptanceTest(final EnclaveType enclaveType) throws IOException {
    final Network containerNetwork = Network.newNetwork();
    alice =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            "node1",
            privacyAccountResolver.resolve(0),
            enclaveType,
            Optional.of(containerNetwork),
            false,
            false,
            false);
    bob =
        privacyBesu.createPrivateTransactionEnabledNode(
            "node2",
            privacyAccountResolver.resolve(1),
            enclaveType,
            Optional.of(containerNetwork),
            false,
            false,
            false);
    charlie =
        privacyBesu.createPrivateTransactionEnabledNode(
            "node3",
            privacyAccountResolver.resolve(2),
            enclaveType,
            Optional.of(containerNetwork),
            false,
            false,
            false);
    privacyCluster.start(alice, bob, charlie);
  }

  @After
  public void cleanUp() {
    vertx.close();
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
                RESTRICTED,
                alice.getEnclaveKey(),
                bob.getEnclaveKey()));

    final PrivateTransactionReceipt expectedReceipt =
        alice.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));

    bob.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            transactionHash, expectedReceipt));

    charlie.verify(privateTransactionVerifier.noPrivateTransactionReceipt(transactionHash));

    // When Alice executes a contract call in the wrong privacy group the transaction should pass
    // but it should NOT return any output
    final String transactionHash2 =
        alice.execute(
            privateContractTransactions.callSmartContract(
                eventEmitter.getContractAddress(),
                eventEmitter.value().encodeFunctionCall(),
                alice.getTransactionSigningKey(),
                RESTRICTED,
                alice.getEnclaveKey(),
                charlie.getEnclaveKey()));

    final PrivateTransactionReceipt expectedReceipt2 =
        alice.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash2));

    assertThat(expectedReceipt2.getOutput()).isEqualTo("0x");

    charlie.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            transactionHash2, expectedReceipt2));
  }

  @Test
  public void aliceCanUsePrivDistributeTransaction() {
    // Contract address is generated from sender address and transaction nonce
    final String contractAddress = "0xebf56429e6500e84442467292183d4d621359838";

    final RawPrivateTransaction rawPrivateTransaction =
        RawPrivateTransaction.createContractTransaction(
            BigInteger.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO,
            Numeric.prependHexPrefix(EventEmitter.BINARY),
            Base64String.wrap(alice.getEnclaveKey()),
            Collections.singletonList(Base64String.wrap(bob.getEnclaveKey())),
            RESTRICTED);

    final String signedPrivateTransaction =
        Numeric.toHexString(
            PrivateTransactionEncoder.signMessage(
                rawPrivateTransaction, Credentials.create(alice.getTransactionSigningKey())));
    final String transactionKey =
        alice.execute(privacyTransactions.privDistributeTransaction(signedPrivateTransaction));

    final Enclave aliceEnclave = enclaveFactory.createVertxEnclave(alice.getEnclave().clientUrl());
    final ReceiveResponse aliceRR =
        aliceEnclave.receive(
            Bytes.fromHexString(transactionKey).toBase64String(), alice.getEnclaveKey());

    final Enclave bobEnclave = enclaveFactory.createVertxEnclave(bob.getEnclave().clientUrl());
    final ReceiveResponse bobRR =
        bobEnclave.receive(
            Bytes.fromHexString(transactionKey).toBase64String(), bob.getEnclaveKey());

    assertThat(bobRR).usingRecursiveComparison().isEqualTo(aliceRR);

    final RawTransaction pmt =
        RawTransaction.createTransaction(
            BigInteger.ZERO,
            BigInteger.valueOf(1000),
            BigInteger.valueOf(65000),
            DEFAULT_PRIVACY.toString(),
            transactionKey);

    final String signedPmt =
        Numeric.toHexString(
            TransactionEncoder.signMessage(
                pmt, Credentials.create(alice.getTransactionSigningKey())));

    final String transactionHash = alice.execute(ethTransactions.sendRawTransaction(signedPmt));

    final PrivateTransactionReceipt expectedReceipt =
        new PrivateTransactionReceipt(
            contractAddress,
            "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
            null,
            null, // ignored in the following call, checked separately below
            Collections.emptyList(),
            "0x023955c49d6265c579561940287449242704d5fd239ff07ea36a3fc7aface61c",
            "0x82e521ee16ff13104c5f81e8354ecaaafd5450b710b07f620204032bfe76041a",
            "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=",
            new ArrayList<>(
                Collections.singletonList("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=")),
            "DyAOiF/ynpc+JXa2YAGB0bCitSlOMNm+ShmB/7M6C4w=",
            "0x1",
            null);

    alice.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            transactionHash, expectedReceipt, true));

    final PrivateTransactionReceipt alicePrivateTransactionReceipt =
        alice.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));
    assertThat(EventEmitter.BINARY)
        .contains(alicePrivateTransactionReceipt.getOutput().substring(2));

    bob.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            transactionHash, expectedReceipt, true));

    final PrivateTransactionReceipt bobPrivateTransactionReceipt =
        bob.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));
    assertThat(EventEmitter.BINARY).contains(bobPrivateTransactionReceipt.getOutput().substring(2));
  }

  @Test
  public void aliceCanDeployMultipleTimesInSingleGroup() {
    final String firstDeployedAddress = "0xebf56429e6500e84442467292183d4d621359838";

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
                RESTRICTED,
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
                RESTRICTED,
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
