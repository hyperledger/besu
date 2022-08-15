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

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.enclave.testutil.EnclaveEncryptorType.EC;
import static org.hyperledger.enclave.testutil.EnclaveEncryptorType.NACL;
import static org.hyperledger.enclave.testutil.EnclaveType.TESSERA;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveEncryptorType;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Optional;

import org.apache.tuweni.crypto.sodium.Box;
import org.assertj.core.api.Condition;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.testcontainers.containers.Network;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.utils.Restriction;

@RunWith(Parameterized.class)
public class EnclaveErrorAcceptanceTest extends PrivacyAcceptanceTestBase {

  private final PrivacyNode alice;
  private final PrivacyNode bob;
  private final String wrongPublicKey;

  @Parameters(name = "{0} enclave type with {1} encryptor")
  public static Collection<Object[]> enclaveParameters() {
    return Arrays.asList(
        new Object[][] {
          {TESSERA, NACL},
          {TESSERA, EC}
        });
  }

  public EnclaveErrorAcceptanceTest(
      final EnclaveType enclaveType, final EnclaveEncryptorType enclaveEncryptorType)
      throws IOException {

    final Network containerNetwork = Network.newNetwork();

    alice =
        privacyBesu.createIbft2NodePrivacyEnabled(
            "node1",
            PrivacyAccountResolver.ALICE.resolve(enclaveEncryptorType),
            false,
            enclaveType,
            Optional.of(containerNetwork),
            false,
            false,
            false,
            "0xAA");
    bob =
        privacyBesu.createIbft2NodePrivacyEnabled(
            "node2",
            PrivacyAccountResolver.BOB.resolve(enclaveEncryptorType),
            false,
            enclaveType,
            Optional.of(containerNetwork),
            false,
            false,
            false,
            "0xBB");
    privacyCluster.start(alice, bob);

    final byte[] wrongPublicKeyBytes =
        EnclaveEncryptorType.EC.equals(enclaveEncryptorType)
            ? getSECP256r1PublicKeyByteArray()
            : Box.KeyPair.random().publicKey().bytesArray();

    wrongPublicKey = Base64.getEncoder().encodeToString(wrongPublicKeyBytes);
  }

  @Test
  public void aliceCannotSendTransactionFromBobNode() {
    final Throwable throwable =
        catchThrowable(
            () ->
                alice.execute(
                    privateContractTransactions.createSmartContract(
                        EventEmitter.class,
                        alice.getTransactionSigningKey(),
                        wrongPublicKey,
                        bob.getEnclaveKey())));

    assertThat(throwable)
        .hasMessageContaining(
            JsonRpcError.PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY.getMessage());
  }

  @Test
  public void enclaveNoPeerUrlError() {
    final Throwable throwable =
        catchThrowable(
            () ->
                alice.execute(
                    privateContractTransactions.createSmartContract(
                        EventEmitter.class,
                        alice.getTransactionSigningKey(),
                        alice.getEnclaveKey(),
                        wrongPublicKey)));

    final String tesseraMessage = JsonRpcError.TESSERA_NODE_MISSING_PEER_URL.getMessage();

    assertThat(throwable.getMessage()).has(matchTesseraEnclaveMessage(tesseraMessage));
  }

  @Test
  public void whenEnclaveIsDisconnectedGetReceiptReturnsInternalError() {
    final EventEmitter eventEmitter =
        alice.execute(
            privateContractTransactions.createSmartContract(
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                alice.getEnclaveKey(),
                bob.getEnclaveKey()));

    privateContractVerifier
        .validPrivateContractDeployed(
            eventEmitter.getContractAddress(), alice.getAddress().toString())
        .verify(eventEmitter);

    final String transactionHash =
        alice.execute(
            privateContractTransactions.callSmartContract(
                eventEmitter.getContractAddress(),
                eventEmitter.store(BigInteger.ONE).encodeFunctionCall(),
                alice.getTransactionSigningKey(),
                Restriction.RESTRICTED,
                alice.getEnclaveKey(),
                bob.getEnclaveKey()));

    final PrivateTransactionReceipt receiptBeforeEnclaveLosesConnection =
        alice.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));

    alice.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            transactionHash, receiptBeforeEnclaveLosesConnection));

    alice.getEnclave().stop();

    alice.verify(
        privateTransactionVerifier.internalErrorPrivateTransactionReceipt(transactionHash));
  }

  @Test
  @Ignore("Web3J is broken by PR #1426")
  public void transactionFailsIfPartyIsOffline() {
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

    bob.getEnclave().stop();

    final Throwable throwable =
        catchThrowable(
            () ->
                alice.execute(
                    privateContractTransactions.callSmartContract(
                        eventEmitter.getContractAddress(),
                        eventEmitter.store(BigInteger.ONE).encodeFunctionCall(),
                        alice.getTransactionSigningKey(),
                        Restriction.RESTRICTED,
                        alice.getEnclaveKey(),
                        bob.getEnclaveKey())));

    assertThat(throwable).hasMessageContaining("NodePropagatingToAllPeers");
  }

  @Test
  public void createPrivacyGroupReturnsCorrectError() {
    final Throwable throwable =
        catchThrowable(() -> alice.execute(privacyTransactions.createPrivacyGroup(null, null)));
    final String tesseraMessage = JsonRpcError.TESSERA_CREATE_GROUP_INCLUDE_SELF.getMessage();

    assertThat(throwable.getMessage()).has(matchTesseraEnclaveMessage(tesseraMessage));
  }

  private Condition<String> matchTesseraEnclaveMessage(final String enclaveMessage) {
    return new Condition<>(
        message -> message.contains(enclaveMessage),
        "Message did not match Tessera expected output");
  }

  private byte[] getSECP256r1PublicKeyByteArray() {
    try {
      final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
      final ECGenParameterSpec spec = new ECGenParameterSpec("secp256r1");
      keyGen.initialize(spec);
      return keyGen.generateKeyPair().getPublic().getEncoded();
    } catch (Exception exception) {
      return new byte[0];
    }
  }
}
