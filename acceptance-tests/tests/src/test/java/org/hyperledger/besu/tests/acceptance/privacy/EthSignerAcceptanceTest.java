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

import static org.web3j.utils.Restriction.UNRESTRICTED;

import org.hyperledger.besu.tests.acceptance.dsl.ethsigner.EthSignerClient;
import org.hyperledger.besu.tests.acceptance.dsl.ethsigner.testutil.EthSignerTestHarness;
import org.hyperledger.besu.tests.acceptance.dsl.ethsigner.testutil.EthSignerTestHarnessFactory;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.ParameterizedEnclaveTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;

import org.junit.Ignore;
import org.junit.Test;
import org.web3j.crypto.CipherException;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.utils.Restriction;

public class EthSignerAcceptanceTest extends ParameterizedEnclaveTestBase {
  private final PrivacyNode minerNode;
  private final EthSignerTestHarness ethSigner;

  private final EthSignerClient ethSignerClient;

  public EthSignerAcceptanceTest(final Restriction restriction, final EnclaveType enclaveType)
      throws IOException, CipherException {
    super(restriction, enclaveType);

    minerNode =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            "miner-node",
            privacyAccountResolver.resolve(0),
            enclaveType,
            Optional.empty(),
            false,
            false,
            restriction == UNRESTRICTED);
    privacyCluster.start(minerNode);

    ethSigner =
        EthSignerTestHarnessFactory.create(
            privacy.newFolder().toPath(),
            "ethSignerKey--fe3b557e8fb62b89f4916b721be55ceb828dbd73.json",
            minerNode.getBesu().getJsonRpcSocketPort().orElseThrow(),
            1337);

    ethSignerClient = new EthSignerClient(ethSigner.getHttpListeningUrl());
  }

  @Test
  public void privateSmartContractMustDeploy() throws IOException {
    final String transactionHash =
        ethSignerClient.eeaSendTransaction(
            null,
            BigInteger.valueOf(3000000L),
            BigInteger.valueOf(1000),
            EventEmitter.BINARY,
            BigInteger.valueOf(0),
            minerNode.getEnclaveKey(),
            Collections.emptyList(),
            restriction.toString().toLowerCase());

    final PrivateTransactionReceipt receipt =
        minerNode.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));

    minerNode.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(transactionHash, receipt));
  }

  // requires ethsigner jar > 0.3.0
  // https://cloudsmith.io/~consensys/repos/ethsigner/packages/
  @Test
  @Ignore
  public void privateSmartContractMustDeployNoNonce() throws IOException {
    final String transactionHash =
        ethSignerClient.eeaSendTransaction(
            null,
            BigInteger.valueOf(3000000L),
            BigInteger.valueOf(1000),
            EventEmitter.BINARY,
            minerNode.getEnclaveKey(),
            Collections.emptyList(),
            restriction.toString().toLowerCase());

    final PrivateTransactionReceipt receipt =
        minerNode.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));

    minerNode.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(transactionHash, receipt));
  }

  @Test
  public void privateSmartContractMustDeployWithPrivacyGroup() throws IOException {
    final String privacyGroupId = minerNode.execute(createPrivacyGroup(null, null, minerNode));

    final String transactionHash =
        ethSignerClient.eeaSendTransaction(
            null,
            BigInteger.valueOf(3000000L),
            BigInteger.valueOf(1000),
            EventEmitter.BINARY,
            BigInteger.valueOf(0),
            minerNode.getEnclaveKey(),
            privacyGroupId,
            restriction.toString().toLowerCase());

    final PrivateTransactionReceipt receipt =
        minerNode.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));

    minerNode.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(transactionHash, receipt));
  }

  @Test
  public void privateSmartContractMustDeployWithPrivacyGroupNoNonce() throws IOException {
    final String privacyGroupId = minerNode.execute(createPrivacyGroup(null, null, minerNode));

    final String transactionHash =
        ethSignerClient.eeaSendTransaction(
            null,
            BigInteger.valueOf(3000000L),
            BigInteger.valueOf(1000),
            EventEmitter.BINARY,
            minerNode.getEnclaveKey(),
            privacyGroupId,
            restriction.toString().toLowerCase());

    final PrivateTransactionReceipt receipt =
        minerNode.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));

    minerNode.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(transactionHash, receipt));
  }
}
