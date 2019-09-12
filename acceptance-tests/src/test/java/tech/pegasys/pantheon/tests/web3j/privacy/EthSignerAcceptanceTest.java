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

import tech.pegasys.pantheon.tests.acceptance.dsl.ethsigner.EthSignerClient;
import tech.pegasys.pantheon.tests.acceptance.dsl.ethsigner.testutil.EthSignerTestHarness;
import tech.pegasys.pantheon.tests.acceptance.dsl.ethsigner.testutil.EthSignerTestHarnessFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyNode;
import tech.pegasys.pantheon.tests.web3j.generated.EventEmitter;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.web3j.protocol.eea.response.PrivateTransactionReceipt;
import org.web3j.protocol.pantheon.response.privacy.PrivacyGroup;
import org.web3j.utils.Base64String;

public class EthSignerAcceptanceTest extends PrivacyAcceptanceTestBase {

  private PrivacyNode minerNode;
  private EthSignerTestHarness ethSigner;

  private EthSignerClient ethSignerClient;

  @Before
  public void setUp() throws Exception {
    minerNode =
        privacyPantheon.createPrivateTransactionEnabledMinerNode(
            "miner-node", privacyAccountResolver.resolve(0));
    privacyCluster.start(minerNode);

    ethSigner =
        EthSignerTestHarnessFactory.create(
            privacy.newFolder().toPath(),
            "ethSignerKey--fe3b557e8fb62b89f4916b721be55ceb828dbd73.json",
            minerNode.getPantheon().getJsonRpcSocketPort().orElseThrow(),
            2018);

    ethSignerClient = new EthSignerClient(ethSigner.getHttpListeningUrl());
  }

  @Test
  @Ignore
  public void privateSmartContractMustDeploy() throws IOException {
    final String transactionHash =
        ethSignerClient.eeaSendTransaction(
            null,
            BigInteger.valueOf(63992),
            BigInteger.valueOf(1000),
            EventEmitter.BINARY,
            BigInteger.valueOf(0),
            minerNode.getEnclaveKey(),
            Collections.emptyList(),
            "restricted");

    final PrivateTransactionReceipt receipt =
        minerNode.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));

    minerNode.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(transactionHash, receipt));
  }

  @Test
  @Ignore
  public void privateSmartContractMustDeployNoNonce() throws IOException {
    final String transactionHash =
        ethSignerClient.eeaSendTransaction(
            null,
            BigInteger.valueOf(63992),
            BigInteger.valueOf(1000),
            EventEmitter.BINARY,
            minerNode.getEnclaveKey(),
            Collections.emptyList(),
            "restricted");

    final PrivateTransactionReceipt receipt =
        minerNode.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));

    minerNode.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(transactionHash, receipt));
  }

  @Test
  public void privateSmartContractMustDeployWithPrivacyGroup() throws IOException {
    final String privacyGroupId =
        minerNode.execute(privacyTransactions.createPrivacyGroup(null, null, minerNode));

    minerNode.verify(
        privateTransactionVerifier.validPrivacyGroupCreated(
            new PrivacyGroup(
                privacyGroupId,
                PrivacyGroup.Type.PANTHEON,
                "Default Name",
                "Default Description",
                Base64String.wrapList(minerNode.getEnclaveKey()))));

    final String transactionHash =
        ethSignerClient.eeaSendTransaction(
            null,
            BigInteger.valueOf(63992),
            BigInteger.valueOf(1000),
            EventEmitter.BINARY,
            BigInteger.valueOf(0),
            minerNode.getEnclaveKey(),
            privacyGroupId,
            "restricted");

    final PrivateTransactionReceipt receipt =
        minerNode.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));

    minerNode.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(transactionHash, receipt));
  }

  @Test
  @Ignore
  public void privateSmartContractMustDeployWithPrivacyGroupNoNonce() throws IOException {
    final String privacyGroupId =
        minerNode.execute(privacyTransactions.createPrivacyGroup(null, null, minerNode));

    minerNode.verify(
        privateTransactionVerifier.validPrivacyGroupCreated(
            new PrivacyGroup(
                privacyGroupId,
                PrivacyGroup.Type.PANTHEON,
                "Default Name",
                "Default Description",
                Base64String.wrapList(minerNode.getEnclaveKey()))));

    final String transactionHash =
        ethSignerClient.eeaSendTransaction(
            null,
            BigInteger.valueOf(63992),
            BigInteger.valueOf(1000),
            EventEmitter.BINARY,
            minerNode.getEnclaveKey(),
            privacyGroupId,
            "restricted");

    final PrivateTransactionReceipt receipt =
        minerNode.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));

    minerNode.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(transactionHash, receipt));
  }
}
