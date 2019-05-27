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
package tech.pegasys.pantheon.tests.acceptance.ibft2;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.account.Account;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

public class Ibft2MiningAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldMineOnSingleNode() throws IOException {
    final PantheonNode minerNode = pantheon.createIbft2Node("miner1");
    cluster.start(minerNode);

    cluster.waitUntil(wait.chainHeadHasProgressedByAtLeast(minerNode, 1));

    final Account sender = accounts.createAccount("account1");
    final Account receiver = accounts.createAccount("account2");

    minerNode.execute(transactions.createTransfer(sender, 50));
    cluster.verify(sender.balanceEquals(50));

    minerNode.execute(transactions.createIncrementalTransfers(sender, receiver, 1));
    cluster.verify(receiver.balanceEquals(1));

    minerNode.execute(transactions.createIncrementalTransfers(sender, receiver, 2));
    cluster.verify(receiver.balanceEquals(3));
  }

  @Test
  public void shouldMineOnMultipleNodes() throws IOException {
    final PantheonNode minerNode1 = pantheon.createIbft2Node("miner1");
    final PantheonNode minerNode2 = pantheon.createIbft2Node("miner2");
    final PantheonNode minerNode3 = pantheon.createIbft2Node("miner3");
    final PantheonNode minerNode4 = pantheon.createIbft2Node("miner4");
    cluster.start(minerNode1, minerNode2, minerNode3, minerNode4);

    cluster.waitUntil(wait.chainHeadHasProgressedByAtLeast(minerNode1, 1, 85));

    final Account sender = accounts.createAccount("account1");
    final Account receiver = accounts.createAccount("account2");

    minerNode1.execute(transactions.createTransfer(sender, 50));
    cluster.verify(sender.balanceEquals(50));

    minerNode2.execute(transactions.createIncrementalTransfers(sender, receiver, 1));
    cluster.verify(receiver.balanceEquals(1));

    minerNode3.execute(transactions.createIncrementalTransfers(sender, receiver, 2));
    cluster.verify(receiver.balanceEquals(3));

    minerNode4.execute(transactions.createIncrementalTransfers(sender, receiver, 3));
    cluster.verify(receiver.balanceEquals(6));
  }

  @Test
  public void shouldMineOnMultipleNodesEvenWhenClusterContainsNonValidator() throws IOException {
    final String[] validators = {"validator1", "validator2", "validator3"};
    final PantheonNode validator1 =
        pantheon.createIbft2NodeWithValidators("validator1", validators);
    final PantheonNode validator2 =
        pantheon.createIbft2NodeWithValidators("validator2", validators);
    final PantheonNode validator3 =
        pantheon.createIbft2NodeWithValidators("validator3", validators);
    final PantheonNode nonValidatorNode =
        pantheon.createIbft2NodeWithValidators("non-validator", validators);
    cluster.start(validator1, validator2, validator3, nonValidatorNode);

    cluster.waitUntil(wait.chainHeadHasProgressedByAtLeast(validator1, 1, 85));

    final Account sender = accounts.createAccount("account1");
    final Account receiver = accounts.createAccount("account2");

    validator1.execute(transactions.createTransfer(sender, 50));
    cluster.verify(sender.balanceEquals(50));

    validator2.execute(transactions.createIncrementalTransfers(sender, receiver, 1));
    cluster.verify(receiver.balanceEquals(1));

    nonValidatorNode.execute(transactions.createIncrementalTransfers(sender, receiver, 2));
    cluster.verify(receiver.balanceEquals(3));
  }

  @Test
  public void shouldStillMineWhenANonProposerNodeFailsAndHasSufficientValidators()
      throws IOException {
    final PantheonNode minerNode1 = pantheon.createIbft2Node("miner1");
    final PantheonNode minerNode2 = pantheon.createIbft2Node("miner2");
    final PantheonNode minerNode3 = pantheon.createIbft2Node("miner3");
    final PantheonNode minerNode4 = pantheon.createIbft2Node("miner4");
    final List<PantheonNode> validators =
        ibftTwo.validators(new PantheonNode[] {minerNode1, minerNode2, minerNode3, minerNode4});
    final PantheonNode nonProposerNode = validators.get(validators.size() - 1);
    cluster.start(validators);

    cluster.waitUntil(wait.chainHeadHasProgressedByAtLeast(minerNode1, 1, 85));

    final Account receiver = accounts.createAccount("account2");

    cluster.stopNode(nonProposerNode);
    validators.get(0).execute(transactions.createTransfer(receiver, 80));

    cluster.verifyOnActiveNodes(receiver.balanceEquals(80));
  }
}
