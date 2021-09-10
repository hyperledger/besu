/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.tests.acceptance.bft.pki;

import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import org.junit.Test;

public class PkiQbftAcceptanceTest extends ParameterizedPkiQbftTestBase {

  public PkiQbftAcceptanceTest(
      final String testName, final PkiQbftAcceptanceTestParameterization input) {
    super(testName, input);
  }

  @Test
  public void shouldMineOnSingleNode() throws Exception {
    final BesuNode minerNode = nodeFactory.createNode(besu, "miner1");
    cluster.start(minerNode);

    cluster.verify(blockchain.reachesHeight(minerNode, 1));

    final Account sender = accounts.createAccount("account1");
    final Account receiver = accounts.createAccount("account2");

    minerNode.execute(accountTransactions.createTransfer(sender, 50));
    cluster.verify(sender.balanceEquals(50));

    minerNode.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 1));
    cluster.verify(receiver.balanceEquals(1));

    minerNode.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 2));
    cluster.verify(receiver.balanceEquals(3));
  }

  @Test
  public void shouldMineOnMultipleNodes() throws Exception {
    final BesuNode minerNode1 = nodeFactory.createNode(besu, "miner1");
    final BesuNode minerNode2 = nodeFactory.createNode(besu, "miner2");
    final BesuNode minerNode3 = nodeFactory.createNode(besu, "miner3");
    final BesuNode minerNode4 = nodeFactory.createNode(besu, "miner4");
    cluster.start(minerNode1, minerNode2, minerNode3, minerNode4);

    cluster.verify(blockchain.reachesHeight(minerNode1, 1, 85));

    final Account sender = accounts.createAccount("account1");
    final Account receiver = accounts.createAccount("account2");

    minerNode1.execute(accountTransactions.createTransfer(sender, 50));
    cluster.verify(sender.balanceEquals(50));

    minerNode2.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 1));
    cluster.verify(receiver.balanceEquals(1));

    minerNode3.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 2));
    cluster.verify(receiver.balanceEquals(3));

    minerNode4.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 3));
    cluster.verify(receiver.balanceEquals(6));
  }

  @Test
  public void shouldMineWithIgnoringANodeInCRL() throws Exception {
    final BesuNode minerNode1 = nodeFactory.createNode(besu, "miner1");
    final BesuNode minerNode2 = nodeFactory.createNode(besu, "miner2");
    final BesuNode minerNode3 = nodeFactory.createNode(besu, "miner3");
    final BesuNode minerNode4 = nodeFactory.createNode(besu, "miner4");
    final BesuNode minerNode5 = nodeFactory.createNode(besu, "miner5");
    final BesuNode minerNode6 = nodeFactory.createNode(besu, "miner6");
    try {
      cluster.start(minerNode1, minerNode2, minerNode3, minerNode4);

      cluster.startNode(minerNode5);
      cluster.startNode(minerNode6);

      cluster.verify(blockchain.reachesHeight(minerNode1, 1, 85));

      final Account sender = accounts.createAccount("account1");
      final Account receiver = accounts.createAccount("account2");

      minerNode1.execute(accountTransactions.createTransfer(sender, 50));
      cluster.verify(sender.balanceEquals(50));

      minerNode2.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 1));
      cluster.verify(receiver.balanceEquals(1));

      minerNode3.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 2));
      cluster.verify(receiver.balanceEquals(3));

      minerNode4.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 3));
      cluster.verify(receiver.balanceEquals(6));

      if (minerNode1.getTLSConfiguration().isEmpty()) {
        minerNode1.verify(net.awaitPeerCount(5));
        minerNode5.verify(net.awaitPeerCount(5));
        minerNode6.verify(net.awaitPeerCount(5));
      } else {
        minerNode1.verify(net.awaitPeerCount(3));
        minerNode5.verify(net.awaitPeerCount(0));
        minerNode6.verify(net.awaitPeerCount(0));
      }
    } finally {
      cluster.stopNode(minerNode5);
      cluster.stopNode(minerNode6);
      minerNode5.close();
      minerNode6.close();
    }
  }
}
