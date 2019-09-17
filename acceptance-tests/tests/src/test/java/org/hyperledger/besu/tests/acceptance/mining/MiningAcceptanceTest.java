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
package org.hyperledger.besu.tests.acceptance.mining;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;

import org.junit.Before;
import org.junit.Test;

public class MiningAcceptanceTest extends AcceptanceTestBase {

  private Node minerNode;

  @Before
  public void setUp() throws Exception {
    minerNode = besu.createMinerNode("miner1");
    cluster.start(minerNode);
  }

  @Test
  public void shouldMineTransactions() {
    final Account sender = accounts.createAccount("account1");
    final Account receiver = accounts.createAccount("account2");
    minerNode.execute(accountTransactions.createTransfer(sender, 50));
    cluster.verify(sender.balanceEquals(50));

    minerNode.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 1));
    cluster.verify(receiver.balanceEquals(1));

    minerNode.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 2));
    cluster.verify(receiver.balanceEquals(3));

    minerNode.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 3));
    cluster.verify(receiver.balanceEquals(6));

    minerNode.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 4));
    cluster.verify(receiver.balanceEquals(10));

    minerNode.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 5));
    cluster.verify(receiver.balanceEquals(15));
  }
}
