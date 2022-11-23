/*
 * Copyright Hyperledger Besu Contributors.
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
import static org.hyperledger.enclave.testutil.EnclaveEncryptorType.EC;
import static org.hyperledger.enclave.testutil.EnclaveType.TESSERA;
import static org.web3j.utils.Restriction.RESTRICTED;

import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.privacy.PrivacyNodeConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveKeyConfiguration;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Test;
import org.testcontainers.containers.Network;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.eea.crypto.PrivateTransactionEncoder;
import org.web3j.protocol.eea.crypto.RawPrivateTransaction;
import org.web3j.utils.Base64String;
import org.web3j.utils.Numeric;

public class PrivateTransactionValidationAcceptanceTests extends PrivacyAcceptanceTestBase {

  private final PrivacyNode miner;
  private final PrivacyNode bob;
  private final PrivacyNode charlie;
  private final Vertx vertx = Vertx.vertx();

  public PrivateTransactionValidationAcceptanceTests() throws IOException {
    final Network containerNetwork = Network.newNetwork();
    var aliceAccount = PrivacyAccountResolver.ALICE.resolve(EC);
    var bobAccount = PrivacyAccountResolver.BOB.resolve(EC);
    var charlieAccount = PrivacyAccountResolver.CHARLIE.resolve(EC);

    miner =
        privacyBesu.create(
            new PrivacyNodeConfiguration(
                false,
                false,
                false,
                new BesuNodeConfigurationBuilder()
                    .miningEnabled()
                    .name("miner")
                    .jsonRpcEnabled()
                    .build(),
                new EnclaveKeyConfiguration(
                    aliceAccount.getEnclaveKeyPaths(),
                    aliceAccount.getEnclavePrivateKeyPaths(),
                    aliceAccount.getEnclaveEncryptorType())),
            TESSERA,
            Optional.of(containerNetwork));

    bob =
        privacyBesu.create(
            new PrivacyNodeConfiguration(
                false,
                false,
                false,
                new BesuNodeConfigurationBuilder()
                    .name("bob")
                    .jsonRpcEnabled()
                    .keyFilePath(bobAccount.getPrivateKeyPath())
                    .enablePrivateTransactions()
                    .extraCLIOptions(List.of("--privacy-validate-private-transactions=false"))
                    .build(),
                new EnclaveKeyConfiguration(
                    bobAccount.getEnclaveKeyPaths(),
                    bobAccount.getEnclavePrivateKeyPaths(),
                    bobAccount.getEnclaveEncryptorType())),
            TESSERA,
            Optional.of(containerNetwork));
    charlie =
        privacyBesu.create(
            new PrivacyNodeConfiguration(
                false,
                false,
                false,
                new BesuNodeConfigurationBuilder()
                    .name("charlie")
                    .jsonRpcEnabled()
                    .keyFilePath(charlieAccount.getPrivateKeyPath())
                    .enablePrivateTransactions()
                    .extraCLIOptions(List.of("--privacy-validate-private-transactions=false"))
                    .build(),
                new EnclaveKeyConfiguration(
                    charlieAccount.getEnclaveKeyPaths(),
                    charlieAccount.getEnclavePrivateKeyPaths(),
                    charlieAccount.getEnclaveEncryptorType())),
            TESSERA,
            Optional.of(containerNetwork));

    privacyCluster.start(miner, bob, charlie);
  }

  @After
  public void cleanUp() {
    vertx.close();
  }

  @Test
  public void privateTransactionWithFuturePrivateNonce() {
    var nonceTooHigh = BigInteger.ONE;

    final RawPrivateTransaction rawPrivateTransaction =
        RawPrivateTransaction.createContractTransaction(
            nonceTooHigh,
            BigInteger.ZERO,
            BigInteger.ZERO,
            Numeric.prependHexPrefix(EventEmitter.BINARY),
            Base64String.wrap(bob.getEnclaveKey()),
            Collections.singletonList(Base64String.wrap(charlie.getEnclaveKey())),
            RESTRICTED);

    final String signedPrivateTransaction =
        Numeric.toHexString(
            PrivateTransactionEncoder.signMessage(
                rawPrivateTransaction, Credentials.create(bob.getTransactionSigningKey())));

    final String transactionKey =
        bob.execute(privacyTransactions.privDistributeTransaction(signedPrivateTransaction));

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
                pmt, Credentials.create(bob.getTransactionSigningKey())));

    final String transactionHash = bob.execute(ethTransactions.sendRawTransaction(signedPmt));

    eth.expectSuccessfulTransactionReceipt(transactionHash);

    var receipt = bob.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));

    assertThat(receipt.getStatus()).isEqualTo("0x2");
  }
}
