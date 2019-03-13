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

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;

import java.io.IOException;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class DeployPrivateSmartContractAcceptanceTest extends PrivateAcceptanceTestBase {

  // Contract address is generated from sender address and transaction nonce
  protected static final Address CONTRACT_ADDRESS =
      Address.fromHexString("0x0bac79b78b9866ef11c989ad21a7fcf15f7a18d7");
  protected static final String PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

  private PantheonNode minerNode;
  private static OrionTestHarness enclave;
  private static PrivacyParameters privacyParameters;

  @BeforeClass
  public static void setUpOnce() throws Exception {
    enclave = createEnclave("orion_key_0.pub", "orion_key_0.key");
    privacyParameters = getPrivacyParams(enclave);
  }

  @AfterClass
  public static void tearDownOnce() {
    enclave.getOrion().stop();
  }

  @Before
  public void setUp() throws Exception {
    minerNode = pantheon.createPrivateTransactionEnabledMinerNode("miner-node", privacyParameters);
    cluster.start(minerNode);
  }

  @Test
  public void deployingMustGiveValidReceipt() throws IOException {
    final String transactionHash =
        minerNode.execute(transactions.deployPrivateSmartContract(getDeploySimpleStorage()));

    privateContractVerifier
        .validPrivateTransactionReceipt(CONTRACT_ADDRESS.toString())
        .verify(minerNode, transactionHash, PUBLIC_KEY);
  }

  @Test
  public void privateSmartContractMustEmitEvents() throws IOException {
    minerNode.execute(transactions.deployPrivateSmartContract(getDeploySimpleStorage()));

    final String transactionHash =
        minerNode.execute(transactions.createPrivateRawTransaction(getExecuteStoreFunc()));

    privateContractVerifier
        .validPrivateTransactionReceiptReturnsEvents("1000")
        .verify(minerNode, transactionHash, PUBLIC_KEY);
  }

  @Test
  public void privateSmartContractMustReturnValues() throws IOException {

    minerNode.execute(transactions.deployPrivateSmartContract(getDeploySimpleStorage()));

    minerNode.execute(transactions.createPrivateRawTransaction(getExecuteStoreFunc()));

    final String transactionHash =
        minerNode.execute(transactions.createPrivateRawTransaction(getExecuteGetFunc()));

    privateContractVerifier
        .validPrivateTransactionReceiptReturnsValues("1000")
        .verify(minerNode, transactionHash, PUBLIC_KEY);
  }
}
