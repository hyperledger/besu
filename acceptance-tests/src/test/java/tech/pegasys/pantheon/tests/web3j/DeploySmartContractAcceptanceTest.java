/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.tests.web3j;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.web3j.generated.SimpleStorage;

import org.junit.Before;
import org.junit.Test;

public class DeploySmartContractAcceptanceTest extends AcceptanceTestBase {

  private PantheonNode minerNode;

  @Before
  public void setUp() throws Exception {
    minerNode = pantheon.createMinerNode("miner-node");
    cluster.start(minerNode);
  }

  @Test
  public void deployingMustGiveValidReceipt() {
    // Contract address is generated from sender address and transaction nonce
    final String contractAddress = "0x42699a7612a82f1d9c36148af9c77354759b210b";

    final SimpleStorage simpleStorageContract =
        minerNode.execute(transactions.createSmartContract(SimpleStorage.class));

    contractVerifier.validTransactionReceipt(contractAddress).verify(simpleStorageContract);
  }
}
