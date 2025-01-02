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
package org.hyperledger.besu.tests.acceptance.clique;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.condition.clique.ExpectNonceVote.CLIQUE_NONCE_VOTE;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class CliqueProposeRpcAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldAddValidators() throws IOException {
    final String[] initialValidators = {"miner1", "miner2"};
    final BesuNode minerNode1 = besu.createCliqueNodeWithValidators("miner1", initialValidators);
    final BesuNode minerNode2 = besu.createCliqueNodeWithValidators("miner2", initialValidators);
    final BesuNode minerNode3 = besu.createCliqueNodeWithValidators("miner3", initialValidators);
    cluster.start(minerNode1, minerNode2, minerNode3);

    waitForNodesConnectedAndInSync(minerNode1, minerNode2, minerNode3);

    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2));
    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode3));
    minerNode2.execute(cliqueTransactions.createAddProposal(minerNode3));
    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2, minerNode3));
  }

  @Test
  public void shouldRemoveValidators() throws IOException {
    final String[] initialValidators = {"miner1", "miner2", "miner3"};
    final BesuNode minerNode1 = besu.createCliqueNodeWithValidators("miner1", initialValidators);
    final BesuNode minerNode2 = besu.createCliqueNodeWithValidators("miner2", initialValidators);
    final BesuNode minerNode3 = besu.createCliqueNodeWithValidators("miner3", initialValidators);
    cluster.start(minerNode1, minerNode2, minerNode3);

    waitForNodesConnectedAndInSync(minerNode1, minerNode2, minerNode3);

    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2, minerNode3));
    final Condition cliqueValidatorsChanged = clique.awaitSignerSetChange(minerNode1);
    minerNode1.execute(cliqueTransactions.createRemoveProposal(minerNode3));
    minerNode2.execute(cliqueTransactions.createRemoveProposal(minerNode3));
    cluster.verify(cliqueValidatorsChanged);
    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2));
  }

  @Disabled
  @Test
  public void shouldNotAddValidatorWhenInsufficientVotes() throws IOException {
    final String[] initialValidators = {"miner1", "miner2"};
    final BesuNode minerNode1 = besu.createCliqueNodeWithValidators("miner1", initialValidators);
    final BesuNode minerNode2 = besu.createCliqueNodeWithValidators("miner2", initialValidators);
    final BesuNode minerNode3 = besu.createCliqueNodeWithValidators("miner3", initialValidators);
    cluster.start(minerNode1, minerNode2, minerNode3);

    waitForNodesConnectedAndInSync(minerNode1, minerNode2, minerNode3);

    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2));
    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode3));
    minerNode1.verify(blockchain.reachesHeight(minerNode1, 1));
    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2));
  }

  @Test
  public void shouldNotRemoveValidatorWhenInsufficientVotes() throws IOException {
    final BesuNode minerNode1 = besu.createCliqueNode("miner1");
    final BesuNode minerNode2 = besu.createCliqueNode("miner2");
    final BesuNode minerNode3 = besu.createCliqueNode("miner3");
    cluster.start(minerNode1, minerNode2, minerNode3);

    waitForNodesConnectedAndInSync(minerNode1, minerNode2, minerNode3);

    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2, minerNode3));
    minerNode1.execute(cliqueTransactions.createRemoveProposal(minerNode3));
    minerNode1.verify(blockchain.reachesHeight(minerNode1, 1));
    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2, minerNode3));
  }

  @Disabled
  @Test
  public void shouldIncludeVoteInBlockHeader() throws IOException {
    final String[] initialValidators = {"miner1", "miner2"};
    final BesuNode minerNode1 = besu.createCliqueNodeWithValidators("miner1", initialValidators);
    final BesuNode minerNode2 = besu.createCliqueNodeWithValidators("miner2", initialValidators);
    final BesuNode minerNode3 = besu.createCliqueNodeWithValidators("miner3", initialValidators);
    cluster.start(minerNode1, minerNode2, minerNode3);

    waitForNodesConnectedAndInSync(minerNode1, minerNode2, minerNode3);

    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode3));
    minerNode1.verify(blockchain.reachesHeight(minerNode1, 1));
    minerNode1.verify(blockchain.beneficiaryEquals(minerNode3));
    minerNode1.verify(clique.nonceVoteEquals(CLIQUE_NONCE_VOTE.AUTH));

    minerNode1.execute(cliqueTransactions.createRemoveProposal(minerNode2));
    minerNode1.verify(blockchain.reachesHeight(minerNode1, 1));
    minerNode1.verify(blockchain.beneficiaryEquals(minerNode2));
    minerNode1.verify(clique.nonceVoteEquals(CLIQUE_NONCE_VOTE.DROP));
  }

  private void waitForNodesConnectedAndInSync(
      final BesuNode minerNode1, final BesuNode minerNode2, final BesuNode minerNode3) {
    // verify nodes are fully connected otherwise blocks could not be propagated
    minerNode1.verify(net.awaitPeerCount(2));
    minerNode2.verify(net.awaitPeerCount(2));
    minerNode3.verify(net.awaitPeerCount(2));

    // verify that the miner started producing blocks and all other nodes are syncing from it
    waitForBlockHeight(minerNode1, 1);
    final var minerChainHead = minerNode1.execute(ethTransactions.block());
    minerNode2.verify(blockchain.minimumHeight(minerChainHead.getNumber().longValue()));
    minerNode3.verify(blockchain.minimumHeight(minerChainHead.getNumber().longValue()));
  }
}
