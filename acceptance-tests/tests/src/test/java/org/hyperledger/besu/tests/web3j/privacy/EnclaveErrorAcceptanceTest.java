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
package org.hyperledger.besu.tests.web3j.privacy;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;

import java.math.BigInteger;
import java.util.Base64;

import org.apache.tuweni.crypto.sodium.Box;
import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.eea.response.PrivateTransactionReceipt;

public class EnclaveErrorAcceptanceTest extends PrivacyAcceptanceTestBase {

  private static final long IBFT2_CHAIN_ID = 4;

  private PrivacyNode alice;
  private PrivacyNode bob;
  private String wrongPublicKey;

  @Before
  public void setUp() throws Exception {
    alice = privacyBesu.createIbft2NodePrivacyEnabled("node1", privacyAccountResolver.resolve(0));
    bob = privacyBesu.createIbft2NodePrivacyEnabled("node2", privacyAccountResolver.resolve(1));
    privacyCluster.start(alice, bob);

    wrongPublicKey =
        Base64.getEncoder().encodeToString(Box.KeyPair.random().publicKey().bytesArray());
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
                        IBFT2_CHAIN_ID,
                        wrongPublicKey,
                        bob.getEnclaveKey())));

    assertThat(throwable)
        .hasMessageContaining(JsonRpcError.ENCLAVE_NO_MATCHING_PRIVATE_KEY.getMessage());
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
                        IBFT2_CHAIN_ID,
                        alice.getEnclaveKey(),
                        wrongPublicKey)));

    assertThat(throwable).hasMessageContaining(JsonRpcError.NODE_MISSING_PEER_URL.getMessage());
  }

  @Test
  public void whenEnclaveIsDisconnectedGetReceiptReturnsInternalError() {
    final EventEmitter eventEmitter =
        alice.execute(
            privateContractTransactions.createSmartContract(
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                IBFT2_CHAIN_ID,
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
                IBFT2_CHAIN_ID,
                alice.getEnclaveKey(),
                bob.getEnclaveKey()));

    final PrivateTransactionReceipt receiptBeforeEnclaveLosesConnection =
        alice.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));

    alice.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            transactionHash, receiptBeforeEnclaveLosesConnection));

    alice.getOrion().stop();

    alice.verify(
        privateTransactionVerifier.internalErrorPrivateTransactionReceipt(transactionHash));
  }

  @Test
  public void transactionFailsIfPartyIsOffline() {
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

    bob.getOrion().stop();

    final Throwable throwable =
        catchThrowable(
            () ->
                alice.execute(
                    privateContractTransactions.callSmartContract(
                        eventEmitter.getContractAddress(),
                        eventEmitter.store(BigInteger.ONE).encodeFunctionCall(),
                        alice.getTransactionSigningKey(),
                        IBFT2_CHAIN_ID,
                        alice.getEnclaveKey(),
                        bob.getEnclaveKey())));

    assertThat(throwable).hasMessageContaining("NodePushingToPeer");
  }

  @Test
  public void createPrivacyGroupReturnsCorrectError() {
    final Throwable throwable =
        catchThrowable(() -> alice.execute(privacyTransactions.createPrivacyGroup(null, null)));

    assertThat(throwable).hasMessageContaining(JsonRpcError.CREATE_GROUP_INCLUDE_SELF.getMessage());
  }
}
