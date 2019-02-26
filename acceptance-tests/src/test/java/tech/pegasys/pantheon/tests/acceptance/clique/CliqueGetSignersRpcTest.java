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

  @Before
  public void setUp() throws Exception {
    final String[] validators = {"miner1"};
    minerNode1 = pantheon.createCliqueNodeWithValidators("miner1", validators);
    minerNode2 = pantheon.createCliqueNodeWithValidators("miner2", validators);
    cluster.start(minerNode1, minerNode2);
  }

  @Test
  public void shouldBeAbleToGetValidatorsForBlockNumber() {
    cluster.verify(clique.validatorsAtBlockEqual("0x0", minerNode1));
    minerNode1.waitUntil(wait.chainHeadIsAtLeast(1));

    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode2));
    cluster.waitUntil(wait.chainHeadHasProgressedByAtLeast(minerNode1, 1));
    cluster.verify(clique.validatorsAtBlockEqual("0x2", minerNode1, minerNode2));
    cluster.verify(clique.validatorsAtBlockEqual(LATEST, minerNode1, minerNode2));
  }

  @Test
  public void shouldBeAbleToGetValidatorsForBlockHash() {
    cluster.verify(clique.validatorsAtBlockHashFromBlockNumberEqual(minerNode1, 0, minerNode1));
    minerNode1.waitUntil(wait.chainHeadIsAtLeast(1));

    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode2));
    cluster.waitUntil(wait.chainHeadHasProgressedByAtLeast(minerNode1, 1));
    cluster.verify(
        clique.validatorsAtBlockHashFromBlockNumberEqual(minerNode1, 2, minerNode1, minerNode2));
  }
}
