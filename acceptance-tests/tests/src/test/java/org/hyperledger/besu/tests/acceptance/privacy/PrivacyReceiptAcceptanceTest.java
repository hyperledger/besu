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

import static java.util.Optional.empty;
import static org.web3j.utils.Restriction.RESTRICTED;
import static org.web3j.utils.Restriction.UNRESTRICTED;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.ParameterizedEnclaveTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.miner.MinerTransactions;
import org.hyperledger.enclave.testutil.EnclaveEncryptorType;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.utils.Restriction;

public class PrivacyReceiptAcceptanceTest extends ParameterizedEnclaveTestBase {
  final MinerTransactions minerTransactions = new MinerTransactions();

  private PrivacyNode alice;

  public void setUp(
      final Restriction restriction,
      final EnclaveType enclaveType,
      final EnclaveEncryptorType enclaveEncryptorType)
      throws IOException {

    alice =
        privacyBesu.createIbft2NodePrivacyEnabled(
            "node1",
            PrivacyAccountResolver.ALICE.resolve(enclaveEncryptorType),
            false,
            enclaveType,
            Optional.empty(),
            false,
            false,
            restriction == UNRESTRICTED,
            "0xAA");
    privacyCluster.start(alice);
  }

  @ParameterizedTest(name = "{0} tx with {1} enclave and {2} encryptor type")
  @MethodSource("params")
  public void createPrivateTransactionReceiptSuccessfulTransaction(
      final Restriction restriction,
      final EnclaveType enclaveType,
      final EnclaveEncryptorType enclaveEncryptorType)
      throws Exception {
    setUp(restriction, enclaveType, enclaveEncryptorType);
    final Transaction<String> onlyAlice = createPrivacyGroup(restriction, "Only Alice", "", alice);

    final String privacyGroupId = alice.execute(onlyAlice);

    final PrivateTransaction validTransaction =
        createSignedTransaction(restriction, alice, privacyGroupId, empty());
    final BytesValueRLPOutput rlpOutput = getRLPOutput(validTransaction);

    final Hash transactionHash =
        alice.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));

    // Successful PMT
    alice.getBesu().verify(eth.expectSuccessfulTransactionReceipt(transactionHash.toString()));
    // Successful private transaction
    alice.getBesu().verify(priv.getSuccessfulTransactionReceipt(transactionHash));
  }

  @ParameterizedTest(name = "{0} tx with {1} enclave and {2} encryptor type")
  @MethodSource("params")
  public void createPrivateTransactionReceiptFailedTransaction(
      final Restriction restriction,
      final EnclaveType enclaveType,
      final EnclaveEncryptorType enclaveEncryptorType)
      throws Exception {
    setUp(restriction, enclaveType, enclaveEncryptorType);
    final Transaction<String> onlyAlice = createPrivacyGroup(restriction, "Only Alice", "", alice);

    final String privacyGroupId = alice.execute(onlyAlice);

    final PrivateTransaction invalidPayloadTransaction =
        createSignedTransaction(
            restriction,
            alice,
            privacyGroupId,
            Optional.of(Bytes.fromBase64String("invalidPayload")));
    final BytesValueRLPOutput rlpOutput = getRLPOutput(invalidPayloadTransaction);

    final Hash transactionHash =
        alice.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));

    // Successful PMT
    alice.getBesu().verify(eth.expectSuccessfulTransactionReceipt(transactionHash.toString()));
    // Failed private transaction
    alice.getBesu().verify(priv.getFailedTransactionReceipt(transactionHash));
  }

  @ParameterizedTest(name = "{0} tx with {1} enclave and {2} encryptor type")
  @MethodSource("params")
  public void createPrivateTransactionReceiptInvalidTransaction(
      final Restriction restriction,
      final EnclaveType enclaveType,
      final EnclaveEncryptorType enclaveEncryptorType)
      throws Exception {
    setUp(restriction, enclaveType, enclaveEncryptorType);
    final Transaction<String> onlyAlice = createPrivacyGroup(restriction, "Only Alice", "", alice);

    final String privacyGroupId = alice.execute(onlyAlice);

    final PrivateTransaction validTransaction =
        createSignedTransaction(restriction, alice, privacyGroupId, empty());
    final BytesValueRLPOutput rlpOutput = getRLPOutput(validTransaction);

    // Stop mining, to allow adding duplicate nonce block
    alice.getBesu().execute(minerTransactions.minerStop());

    final Hash transactionHash1 =
        alice.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));
    final Hash transactionHash2 =
        alice.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));

    // Start mining again
    alice.getBesu().execute(minerTransactions.minerStart());

    // Successful PMTs
    alice.getBesu().verify(eth.expectSuccessfulTransactionReceipt(transactionHash1.toString()));
    alice.getBesu().verify(eth.expectSuccessfulTransactionReceipt(transactionHash2.toString()));
    // Successful first private transaction
    alice.getBesu().verify(priv.getSuccessfulTransactionReceipt(transactionHash1));
    // Invalid second private transaction
    alice.getBesu().verify(priv.getInvalidTransactionReceipt(transactionHash2));
  }

  private BytesValueRLPOutput getRLPOutput(final PrivateTransaction privateTransaction) {
    final BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlpo);
    return bvrlpo;
  }

  private PrivateTransaction createSignedTransaction(
      final Restriction restriction,
      final PrivacyNode node,
      final String privacyGoupId,
      final Optional<Bytes> payload) {

    org.hyperledger.besu.plugin.data.Restriction besuRestriction =
        restriction == RESTRICTED
            ? org.hyperledger.besu.plugin.data.Restriction.RESTRICTED
            : org.hyperledger.besu.plugin.data.Restriction.UNRESTRICTED;

    final Bytes defaultPayload = Bytes.wrap(new byte[] {});
    return PrivateTransaction.builder()
        .nonce(0)
        .gasPrice(Wei.of(999999))
        .gasLimit(3000000)
        .to(null)
        .value(Wei.ZERO)
        .payload(payload.orElse(defaultPayload))
        .sender(node.getAddress())
        .privateFrom(Bytes.fromBase64String(node.getEnclaveKey()))
        .restriction(besuRestriction)
        .privacyGroupId(Bytes.fromBase64String(privacyGoupId))
        .signAndBuild(node.getBesu().getPrivacyParameters().getSigningKeyPair().get());
  }
}
