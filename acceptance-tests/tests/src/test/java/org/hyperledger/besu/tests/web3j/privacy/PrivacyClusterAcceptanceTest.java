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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.math.BigInteger;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.eea.crypto.PrivateTransactionEncoder;
import org.web3j.protocol.eea.crypto.RawPrivateTransaction;
import org.web3j.protocol.pantheon.response.privacy.PrivateTransactionReceipt;
import org.web3j.utils.Base64String;
import org.web3j.utils.Numeric;
import org.web3j.utils.Restriction;

public class PrivacyClusterAcceptanceTest extends PrivacyAcceptanceTestBase {

  private static final long POW_CHAIN_ID = 2018;

  private static final String eventEmmitterDeployed =
      "0x6080604052600436106100565763ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416633fa4f245811461005b5780636057361d1461008257806367e404ce146100ae575b600080fd5b34801561006757600080fd5b506100706100ec565b60408051918252519081900360200190f35b34801561008e57600080fd5b506100ac600480360360208110156100a557600080fd5b50356100f2565b005b3480156100ba57600080fd5b506100c3610151565b6040805173ffffffffffffffffffffffffffffffffffffffff9092168252519081900360200190f35b60025490565b604080513381526020810183905281517fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f5929181900390910190a16002556001805473ffffffffffffffffffffffffffffffffffffffff191633179055565b60015473ffffffffffffffffffffffffffffffffffffffff169056fea165627a7a72305820c7f729cb24e05c221f5aa913700793994656f233fe2ce3b9fd9a505ea17e8d8a0029";

  private PrivacyNode alice;
  private PrivacyNode bob;
  private PrivacyNode charlie;

  @Before
  public void setUp() throws Exception {
    alice =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            "node1", privacyAccountResolver.resolve(0));
    bob =
        privacyBesu.createPrivateTransactionEnabledNode("node2", privacyAccountResolver.resolve(1));
    charlie =
        privacyBesu.createPrivateTransactionEnabledNode("node3", privacyAccountResolver.resolve(2));
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

    // When Alice executes a contract call in the wrong privacy group the transaction should pass
    // but it should return any output
    final String transactionHash2 =
        alice.execute(
            privateContractTransactions.callSmartContract(
                eventEmitter.getContractAddress(),
                eventEmitter.value().encodeFunctionCall(),
                alice.getTransactionSigningKey(),
                POW_CHAIN_ID,
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
            Restriction.RESTRICTED);

    final String signedPrivateTransaction =
        Numeric.toHexString(
            PrivateTransactionEncoder.signMessage(
                rawPrivateTransaction,
                POW_CHAIN_ID,
                Credentials.create(alice.getTransactionSigningKey())));
    final String transactionKey =
        alice.execute(privacyTransactions.privDistributeTransaction(signedPrivateTransaction));

    final Enclave aliceEnclave = new Enclave(alice.getOrion().clientUrl());
    final ReceiveResponse aliceRR =
        aliceEnclave.receive(
            new ReceiveRequest(
                BytesValues.asBase64String(BytesValue.fromHexString(transactionKey)),
                alice.getEnclaveKey()));

    final Enclave bobEnclave = new Enclave(bob.getOrion().clientUrl());
    final ReceiveResponse bobRR =
        bobEnclave.receive(
            new ReceiveRequest(
                BytesValues.asBase64String(BytesValue.fromHexString(transactionKey)),
                bob.getEnclaveKey()));

    assertThat(bobRR).isEqualToComparingFieldByField(aliceRR);

    final RawTransaction pmt =
        RawTransaction.createTransaction(
            BigInteger.ZERO,
            BigInteger.valueOf(1000),
            BigInteger.valueOf(65000),
            Address.DEFAULT_PRIVACY.toString(),
            transactionKey);

    final String signedPmt =
        Numeric.toHexString(
            TransactionEncoder.signMessage(
                pmt, POW_CHAIN_ID, Credentials.create(alice.getTransactionSigningKey())));

    final String transactionHash = alice.execute(ethTransactions.sendRawTransaction(signedPmt));

    final PrivateTransactionReceipt expectedReceipt =
        new PrivateTransactionReceipt(
            contractAddress,
            "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
            null,
            eventEmmitterDeployed,
            Collections.emptyList());

    alice.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            transactionHash, expectedReceipt));

    bob.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            transactionHash, expectedReceipt));
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
