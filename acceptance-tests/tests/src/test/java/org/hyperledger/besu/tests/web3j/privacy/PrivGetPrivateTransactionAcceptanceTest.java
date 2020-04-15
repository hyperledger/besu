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

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.transaction.CreatePrivacyGroupTransaction;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class PrivGetPrivateTransactionAcceptanceTest extends PrivacyAcceptanceTestBase {

  private PrivacyNode alice;
  private PrivacyNode bob;

  @Before
  public void setUp() throws Exception {
    alice = privacyBesu.createIbft2NodePrivacyEnabled("node1", privacyAccountResolver.resolve(0));
    bob = privacyBesu.createIbft2NodePrivacyEnabled("node2", privacyAccountResolver.resolve(1));
    privacyCluster.start(alice, bob);
  }

  @Test
  public void returnsTransaction() {
    final CreatePrivacyGroupTransaction onlyAlice =
        privacyTransactions.createPrivacyGroup("Only Alice", "", alice);

    final String privacyGroupId = alice.execute(onlyAlice);

    final PrivateTransaction validSignedPrivateTransaction =
        getValidSignedPrivateTransaction(alice, privacyGroupId);
    final BytesValueRLPOutput rlpOutput = getRLPOutput(validSignedPrivateTransaction);

    final Hash transactionHash =
        alice.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));

    alice.getBesu().verify(eth.expectSuccessfulTransactionReceipt(transactionHash.toString()));

    alice
        .getBesu()
        .verify(priv.getPrivateTransaction(transactionHash, validSignedPrivateTransaction));
  }

  @Test
  public void nonExistentHashReturnsNull() {
    alice.getBesu().verify(priv.getPrivateTransactionReturnsNull(Hash.ZERO));
  }

  @Test
  public void returnsNullTransactionNotInNodesPrivacyGroup() {
    final CreatePrivacyGroupTransaction onlyAlice =
        privacyTransactions.createPrivacyGroup("Only Alice", "", alice);

    final String privacyGroupId = alice.execute(onlyAlice);

    final PrivateTransaction validSignedPrivateTransaction =
        getValidSignedPrivateTransaction(alice, privacyGroupId);
    final BytesValueRLPOutput rlpOutput = getRLPOutput(validSignedPrivateTransaction);

    final Hash transactionHash =
        alice.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));

    alice.getBesu().verify(eth.expectSuccessfulTransactionReceipt(transactionHash.toString()));

    bob.getBesu().verify(priv.getPrivateTransactionReturnsNull(transactionHash));
  }

  private BytesValueRLPOutput getRLPOutput(final PrivateTransaction privateTransaction) {
    final BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlpo);
    return bvrlpo;
  }

  private static PrivateTransaction getValidSignedPrivateTransaction(
      final PrivacyNode node, final String privacyGoupId) {
    return PrivateTransaction.builder()
        .nonce(0)
        .gasPrice(Wei.of(999999))
        .gasLimit(3000000)
        .to(null)
        .value(Wei.ZERO)
        .payload(Bytes.wrap(new byte[] {}))
        .sender(node.getAddress())
        .privateFrom(Bytes.fromBase64String(node.getEnclaveKey()))
        .restriction(Restriction.RESTRICTED)
        .privacyGroupId(Bytes.fromBase64String(privacyGoupId))
        .signAndBuild(node.getBesu().getPrivacyParameters().getSigningKeyPair().get());
  }
}
