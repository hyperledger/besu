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

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.pantheon.tests.acceptance.dsl.WaitUtils.waitFor;

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivateAcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.PrivateTransactionBuilder.TransactionType;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;

import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DeployPrivateSmartContractAcceptanceTest extends PrivateAcceptanceTestBase {

  // Contract address is generated from sender address and transaction nonce and privacy group id
  protected static final Address CONTRACT_ADDRESS =
      Address.fromHexString("0x06088ead8384df709132151403e08c2b978beb85");
  protected static final String PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private SECP256K1.KeyPair keypair =
      SECP256K1.KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

  private PantheonNode minerNode;
  private static OrionTestHarness enclave;
  private String deployContract;
  private String storeValue;
  private String getValue;

  @Before
  public void setUp() throws Exception {
    enclave = createEnclave("orion_key_0.pub", "orion_key_0.key");
    minerNode =
        pantheon.createPrivateTransactionEnabledMinerNode(
            "miner-node", getPrivacyParameters(enclave), "key");
    cluster.start(minerNode);

    deployContract =
        privateTransactionBuilder
            .nonce(0)
            .from(minerNode.getAddress())
            .to(null)
            .privateFrom(BytesValue.wrap(PUBLIC_KEY.getBytes(UTF_8)))
            .privateFor(Lists.newArrayList())
            .keyPair(keypair)
            .build(TransactionType.CREATE_CONTRACT);

    storeValue =
        privateTransactionBuilder
            .nonce(1)
            .from(minerNode.getAddress())
            .to(CONTRACT_ADDRESS)
            .privateFrom(BytesValue.wrap(PUBLIC_KEY.getBytes(UTF_8)))
            .privateFor(Lists.newArrayList())
            .keyPair(keypair)
            .build(TransactionType.STORE);

    getValue =
        privateTransactionBuilder
            .nonce(2)
            .from(minerNode.getAddress())
            .to(CONTRACT_ADDRESS)
            .privateFrom(BytesValue.wrap(PUBLIC_KEY.getBytes(UTF_8)))
            .privateFor(Lists.newArrayList())
            .keyPair(keypair)
            .build(TransactionType.GET);
  }

  @Test
  public void deployingMustGiveValidReceipt() {
    final String transactionHash =
        minerNode.execute(privateTransactions.deployPrivateSmartContract(deployContract));

    privateTransactionVerifier
        .validPrivateContractDeployed(CONTRACT_ADDRESS.toString())
        .verify(minerNode, transactionHash);
  }

  @Test
  public void privateSmartContractMustEmitEvents() {
    String transactionHash =
        minerNode.execute(privateTransactions.deployPrivateSmartContract(deployContract));

    waitForTransactionToBeMined(transactionHash);

    transactionHash =
        minerNode.execute(privateTransactions.createPrivateRawTransaction(storeValue));

    privateTransactionVerifier.validEventReturned("1000").verify(minerNode, transactionHash);
  }

  @Test
  public void privateSmartContractMustReturnValues() {

    String transactionHash =
        minerNode.execute(privateTransactions.deployPrivateSmartContract(deployContract));

    waitForTransactionToBeMined(transactionHash);

    transactionHash =
        minerNode.execute(privateTransactions.createPrivateRawTransaction(storeValue));

    waitForTransactionToBeMined(transactionHash);

    transactionHash = minerNode.execute(privateTransactions.createPrivateRawTransaction(getValue));

    privateTransactionVerifier.validOutputReturned("1000").verify(minerNode, transactionHash);
  }

  @After
  public void tearDown() {
    enclave.getOrion().stop();
  }

  public void waitForTransactionToBeMined(final String transactionHash) {
    waitFor(() -> minerNode.verify(eea.expectSuccessfulTransactionReceipt(transactionHash)));
  }
}
