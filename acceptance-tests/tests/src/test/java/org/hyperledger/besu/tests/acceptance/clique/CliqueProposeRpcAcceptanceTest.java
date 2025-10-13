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
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

public class CliqueProposeRpcAcceptanceTest extends AcceptanceTestBase {
  private static final GenesisConfigurationFactory.CliqueOptions CLIQUE_OPTIONS =
      new GenesisConfigurationFactory.CliqueOptions(5, 30000, true);

  @Test
  public void shouldAddAndRemoveValidators() throws IOException {
    final String[] initialValidators = {"miner1"};
    final BesuNode minerNode1 =
        besu.createCliqueNodeWithValidators("miner1", CLIQUE_OPTIONS, initialValidators);
    final BesuNode minerNode2 =
        besu.createCliqueNodeWithValidators("miner2", CLIQUE_OPTIONS, initialValidators);
    final BesuNode minerNode3 =
        besu.createCliqueNodeWithValidators("miner3", CLIQUE_OPTIONS, initialValidators);
    cluster.start(minerNode1, minerNode2, minerNode3);

    waitForNodesConnectedAndInSync(minerNode1, minerNode2, minerNode3);

    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode2));
    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2));

    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode3));
    minerNode2.execute(cliqueTransactions.createAddProposal(minerNode3));
    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2, minerNode3));

    final Condition cliqueValidatorsChanged = clique.awaitSignerSetChange(minerNode2);
    minerNode2.execute(cliqueTransactions.createRemoveProposal(minerNode1));
    minerNode3.execute(cliqueTransactions.createRemoveProposal(minerNode1));
    cluster.verify(clique.validatorsEqual(minerNode2, minerNode3));
    cluster.verify(cliqueValidatorsChanged);
  }

  @Test
  public void shouldNotAddValidatorWhenInsufficientVotes() throws IOException {
    final String[] initialValidators = {"miner1"};
    final BesuNode minerNode1 =
        besu.createCliqueNodeWithValidators("miner1", CLIQUE_OPTIONS, initialValidators);
    final BesuNode minerNode2 =
        besu.createCliqueNodeWithValidators("miner2", CLIQUE_OPTIONS, initialValidators);
    final BesuNode minerNode3 =
        besu.createCliqueNodeWithValidators("miner3", CLIQUE_OPTIONS, initialValidators);
    cluster.start(minerNode1, minerNode2, minerNode3);

    waitForNodesConnectedAndInSync(minerNode1, minerNode2, minerNode3);

    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode2));
    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2));

    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode3));
    minerNode1.verify(blockchain.reachesHeight(minerNode1, 1));
    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2));
  }

  @Test
  public void shouldNotRemoveValidatorWhenInsufficientVotes() throws IOException {
    final String[] initialValidators = {"miner1"};
    final BesuNode minerNode1 =
        besu.createCliqueNodeWithValidators("miner1", CLIQUE_OPTIONS, initialValidators);
    final BesuNode minerNode2 =
        besu.createCliqueNodeWithValidators("miner2", CLIQUE_OPTIONS, initialValidators);
    final BesuNode minerNode3 =
        besu.createCliqueNodeWithValidators("miner3", CLIQUE_OPTIONS, initialValidators);
    cluster.start(minerNode1, minerNode2, minerNode3);

    waitForNodesConnectedAndInSync(minerNode1, minerNode2, minerNode3);

    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode2));
    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2));

    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode3));
    minerNode2.execute(cliqueTransactions.createAddProposal(minerNode3));
    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2, minerNode3));

    minerNode1.execute(cliqueTransactions.createRemoveProposal(minerNode3));
    minerNode1.verify(blockchain.reachesHeight(minerNode1, 1));
    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2, minerNode3));
  }

  @Test
  public void shouldIncludeVoteInBlockHeader() throws IOException {
    final String[] initialValidators = {"miner1"};
    final BesuNode minerNode1 =
        besu.createCliqueNodeWithValidators("miner1", CLIQUE_OPTIONS, initialValidators);
    final BesuNode minerNode2 =
        besu.createCliqueNodeWithValidators("miner2", CLIQUE_OPTIONS, initialValidators);
    final BesuNode minerNode3 =
        besu.createCliqueNodeWithValidators("miner3", CLIQUE_OPTIONS, initialValidators);
    cluster.start(minerNode1, minerNode2, minerNode3);

    waitForNodesConnectedAndInSync(minerNode1, minerNode2, minerNode3);

    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode2));
    minerNode1.verify(blockchain.reachesHeight(minerNode1, 1));
    minerNode1.verify(blockchain.beneficiaryEquals(minerNode2));
    minerNode1.verify(clique.nonceVoteEquals(CLIQUE_NONCE_VOTE.AUTH));
    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2));

    minerNode1.execute(cliqueTransactions.createAddProposal(minerNode3));
    minerNode2.execute(cliqueTransactions.createAddProposal(minerNode3));
    minerNode1.verify(blockchain.reachesHeight(minerNode1, 1));
    minerNode2.verify(blockchain.reachesHeight(minerNode1, 1));
    minerNode1.verify(blockchain.beneficiaryEquals(minerNode3));
    minerNode2.verify(blockchain.beneficiaryEquals(minerNode3));
    minerNode1.verify(clique.nonceVoteEquals(CLIQUE_NONCE_VOTE.AUTH));
    minerNode2.verify(clique.nonceVoteEquals(CLIQUE_NONCE_VOTE.AUTH));
    cluster.verify(clique.validatorsEqual(minerNode1, minerNode2, minerNode3));

    minerNode1.execute(cliqueTransactions.createRemoveProposal(minerNode2));
    minerNode1.verify(blockchain.reachesHeight(minerNode1, 1));
    minerNode1.verify(blockchain.beneficiaryEquals(minerNode2));
    minerNode1.verify(clique.nonceVoteEquals(CLIQUE_NONCE_VOTE.DROP));
  }

  private void waitForNodesConnectedAndInSync(final BesuNode... nodes) {
    // verify nodes are fully connected otherwise blocks could not be propagated
    Arrays.stream(nodes).forEach(node -> node.verify(net.awaitPeerCount(nodes.length - 1)));

    // verify that the miner started producing blocks and all other nodes are syncing from it
    waitForBlockHeight(nodes[0], 1);
    final var firstNodeChainHead = nodes[0].execute(ethTransactions.block());
    Arrays.stream(nodes)
        .skip(1)
        .forEach(node -> waitForBlockHeight(node, firstNodeChainHead.getNumber().longValue()));
  }
}
