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
package org.hyperledger.besu.tests.acceptance.bft;

import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.util.List;

import org.junit.Test;

public class BftMiningAcceptanceTest extends ParameterizedBftTestBase {

  public BftMiningAcceptanceTest(
      final String testName, final BftAcceptanceTestParameterization nodeFactory) {
    super(testName, nodeFactory);
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
  public void shouldMineOnMultipleNodesEvenWhenClusterContainsNonValidator() throws Exception {
    final String[] validators = {"validator1", "validator2", "validator3"};
    final BesuNode validator1 =
        nodeFactory.createNodeWithValidators(besu, "validator1", validators);
    final BesuNode validator2 =
        nodeFactory.createNodeWithValidators(besu, "validator2", validators);
    final BesuNode validator3 =
        nodeFactory.createNodeWithValidators(besu, "validator3", validators);
    final BesuNode nonValidatorNode =
        nodeFactory.createNodeWithValidators(besu, "non-validator", validators);
    cluster.start(validator1, validator2, validator3, nonValidatorNode);

    cluster.verify(blockchain.reachesHeight(validator1, 1, 85));

    final Account sender = accounts.createAccount("account1");
    final Account receiver = accounts.createAccount("account2");

    validator1.execute(accountTransactions.createTransfer(sender, 50));
    cluster.verify(sender.balanceEquals(50));

    validator2.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 1));
    cluster.verify(receiver.balanceEquals(1));

    nonValidatorNode.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 2));
    cluster.verify(receiver.balanceEquals(3));
  }

  @Test
  public void shouldStillMineWhenANonProposerNodeFailsAndHasSufficientValidators()
      throws Exception {
    final BesuNode minerNode1 = nodeFactory.createNode(besu, "miner1");
    final BesuNode minerNode2 = nodeFactory.createNode(besu, "miner2");
    final BesuNode minerNode3 = nodeFactory.createNode(besu, "miner3");
    final BesuNode minerNode4 = nodeFactory.createNode(besu, "miner4");
    final List<BesuNode> validators =
        bft.validators(new BesuNode[] {minerNode1, minerNode2, minerNode3, minerNode4});
    final BesuNode nonProposerNode = validators.get(validators.size() - 1);
    cluster.start(validators);

    cluster.verify(blockchain.reachesHeight(minerNode1, 1, 85));

    final Account receiver = accounts.createAccount("account2");

    cluster.stopNode(nonProposerNode);
    validators.get(0).execute(accountTransactions.createTransfer(receiver, 80));

    cluster.verifyOnActiveNodes(receiver.balanceEquals(80));
  }
}
