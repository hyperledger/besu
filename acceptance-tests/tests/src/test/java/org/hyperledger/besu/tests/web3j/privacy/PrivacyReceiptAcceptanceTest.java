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

import static java.util.Optional.empty;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.transaction.CreatePrivacyGroupTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.miner.MinerTransactions;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class PrivacyReceiptAcceptanceTest extends PrivacyAcceptanceTestBase {
  private PrivacyNode alice;
  final MinerTransactions minerTransactions = new MinerTransactions();

  @Before
  public void setUp() throws Exception {
    alice =
        privacyBesu.createIbft2NodePrivacyMiningEnabled("node1", privacyAccountResolver.resolve(0));
    privacyCluster.start(alice);
  }

  @Test
  public void createPrivateTransactionReceiptSuccessfulTransaction() {
    final CreatePrivacyGroupTransaction onlyAlice =
        privacyTransactions.createPrivacyGroup("Only Alice", "", alice);

    final String privacyGroupId = alice.execute(onlyAlice);

    final PrivateTransaction validTransaction =
        createSignedTransaction(alice, privacyGroupId, empty());
    final BytesValueRLPOutput rlpOutput = getRLPOutput(validTransaction);

    final Hash transactionHash =
        alice.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));

    // Successful PMT
    alice.getBesu().verify(eth.expectSuccessfulTransactionReceipt(transactionHash.toString()));
    // Successful private transaction
    alice.getBesu().verify(priv.getSuccessfulTransactionReceipt(transactionHash));
  }

  @Test
  public void createPrivateTransactionReceiptFailedTransaction() {
    final CreatePrivacyGroupTransaction onlyAlice =
        privacyTransactions.createPrivacyGroup("Only Alice", "", alice);

    final String privacyGroupId = alice.execute(onlyAlice);

    final PrivateTransaction invalidPayloadTransaction =
        createSignedTransaction(
            alice, privacyGroupId, Optional.of(Bytes.fromBase64String("invalidPayload")));
    final BytesValueRLPOutput rlpOutput = getRLPOutput(invalidPayloadTransaction);

    final Hash transactionHash =
        alice.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));

    // Successful PMT
    alice.getBesu().verify(eth.expectSuccessfulTransactionReceipt(transactionHash.toString()));
    // Failed private transaction
    alice.getBesu().verify(priv.getFailedTransactionReceipt(transactionHash));
  }

  @Test
  public void createPrivateTransactionReceiptInvalidTransaction() {
    final CreatePrivacyGroupTransaction onlyAlice =
        privacyTransactions.createPrivacyGroup("Only Alice", "", alice);

    final String privacyGroupId = alice.execute(onlyAlice);

    final PrivateTransaction validTransaction =
        createSignedTransaction(alice, privacyGroupId, empty());
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

  private static PrivateTransaction createSignedTransaction(
      final PrivacyNode node, final String privacyGoupId, final Optional<Bytes> payload) {
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
        .restriction(Restriction.RESTRICTED)
        .privacyGroupId(Bytes.fromBase64String(privacyGoupId))
        .signAndBuild(node.getBesu().getPrivacyParameters().getSigningKeyPair().get());
  }
}
