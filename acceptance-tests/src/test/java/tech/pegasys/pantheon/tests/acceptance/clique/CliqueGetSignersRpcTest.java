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
package tech.pegasys.pantheon.tests.acceptance.clique;

import static tech.pegasys.pantheon.tests.acceptance.dsl.transaction.clique.CliqueTransactions.LATEST;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;

import org.junit.Before;
import org.junit.Test;

public class CliqueGetSignersRpcTest extends AcceptanceTestBase {
  private PantheonNode minerNode1;
  private PantheonNode minerNode2;
  private PantheonNode minerNode3;
  private PantheonNode[] allNodes;
  private PantheonNode[] initialNodes;

  @Before
  public void setUp() throws Exception {
    final String[] validators = {"miner1", "miner2"};
    minerNode1 = pantheon.createCliqueNodeWithValidators("miner1", validators);
    minerNode2 = pantheon.createCliqueNodeWithValidators("miner2", validators);
    minerNode3 = pantheon.createCliqueNodeWithValidators("miner3", validators);
    initialNodes = new PantheonNode[] {minerNode1, minerNode2};
    allNodes = new PantheonNode[] {minerNode1, minerNode2, minerNode3};
    cluster.start(allNodes);
  }

  @Test
  public void shouldBeAbleToGetValidatorsForBlockNumber() {
    cluster.verify(clique.validatorsAtBlockEqual("0x0", initialNodes));
    cluster.waitUntil(wait.chainHeadIsAt(1));
    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode3));
    minerNode2.execute(cliqueTransactions.createAddProposal(minerNode3));

    cluster.waitUntil(wait.chainHeadHasProgressed(minerNode1, 1));
    cluster.verify(clique.validatorsAtBlockEqual("0x2", initialNodes));

    minerNode1.waitUntil(wait.chainHeadHasProgressed(minerNode1, 1));
    cluster.verify(clique.validatorsAtBlockEqual("0x3", allNodes));
    cluster.verify(clique.validatorsAtBlockEqual(LATEST, allNodes));
  }

  @Test
  public void shouldBeAbleToGetValidatorsForBlockHash() {
    cluster.verify(clique.validatorsAtBlockHashFromBlockNumberEqual(minerNode1, 0, initialNodes));
    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode3));
    minerNode2.execute(cliqueTransactions.createAddProposal(minerNode3));

    minerNode1.waitUntil(wait.chainHeadHasProgressed(minerNode1, 1));
    cluster.verify(clique.validatorsAtBlockHashFromBlockNumberEqual(minerNode1, 1, initialNodes));

    minerNode1.waitUntil(wait.chainHeadHasProgressed(minerNode1, 1));
    cluster.verify(clique.validatorsAtBlockHashFromBlockNumberEqual(minerNode1, 3, allNodes));
  }
}
