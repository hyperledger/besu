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
package org.hyperledger.besu.tests.acceptance;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CreateAccountAcceptanceTest extends AcceptanceTestBase {

  private Node minerNode;

  @BeforeEach
  public void setUp() throws Exception {
    minerNode = besu.createMinerNode("minerNode");
    cluster.start(minerNode, besu.createArchiveNode("archiveNode"));
  }

  @Test
  public void shouldCreateAnAccount() {
    final Account account = accounts.createAccount("account-one");
    final Amount balance = Amount.ether(20);

    minerNode.execute(accountTransactions.createTransfer(account, balance));

    cluster.verify(account.balanceEquals(balance));
  }
}
