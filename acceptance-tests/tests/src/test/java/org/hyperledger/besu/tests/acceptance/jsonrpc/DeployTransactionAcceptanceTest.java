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
package org.hyperledger.besu.tests.acceptance.jsonrpc;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import org.junit.Before;
import org.junit.Test;

public class DeployTransactionAcceptanceTest extends AcceptanceTestBase {

  private BesuNode minerNode;
  private Account recipient;

  @Before
  public void setUp() throws Exception {
    recipient = accounts.createAccount("recipient");
    minerNode = besu.createMinerNode("node");
    cluster.start(minerNode);
  }

  @Test
  public void transactionMustHaveReceipt() {
    final Hash transactionHash =
        minerNode.execute(accountTransactions.createTransfer(recipient, 5));
    cluster.verify(recipient.balanceEquals(5));
    minerNode.verify(eth.expectSuccessfulTransactionReceipt(transactionHash.toString()));
  }

  @Test
  public void imaginaryTransactionMustHaveNoReceipt() {
    minerNode.verify(
        eth.expectNoTransactionReceipt(
            "0x0000000000000000000000000000000000000000000000000000000000000000"));
  }
}
