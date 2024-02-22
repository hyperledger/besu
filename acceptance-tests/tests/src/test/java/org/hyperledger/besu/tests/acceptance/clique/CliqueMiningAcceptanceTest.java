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

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBaseJunit5;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory.CliqueOptions;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;

public class CliqueMiningAcceptanceTest extends AcceptanceTestBaseJunit5 {

  @Test
  public void shouldMineTransactionsOnSingleNode() throws IOException {
    final BesuNode minerNode = besu.createCliqueNode("miner1");
    cluster.start(minerNode);

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
  public void shouldNotMineBlocksIfNoTransactionsWhenCreateEmptyBlockIsFalse() throws IOException {
    final var cliqueOptionsNoEmptyBlocks =
        new CliqueOptions(
            CliqueOptions.DEFAULT.blockPeriodSeconds(), CliqueOptions.DEFAULT.epochLength(), false);
    final BesuNode minerNode = besu.createCliqueNode("miner1", cliqueOptionsNoEmptyBlocks);
    cluster.start(minerNode);

    cluster.verify(clique.noNewBlockCreated(minerNode));
  }

  @Test
  public void shouldMineBlocksOnlyWhenTransactionsArePresentWhenCreateEmptyBlockIsFalse()
      throws IOException {
    final var cliqueOptionsNoEmptyBlocks =
        new CliqueOptions(
            CliqueOptions.DEFAULT.blockPeriodSeconds(), CliqueOptions.DEFAULT.epochLength(), false);
    final BesuNode minerNode = besu.createCliqueNode("miner1", cliqueOptionsNoEmptyBlocks);
    cluster.start(minerNode);

    final Account sender = accounts.createAccount("account1");

    cluster.verify(clique.noNewBlockCreated(minerNode));

    minerNode.execute(accountTransactions.createTransfer(sender, 50));

    minerNode.verify(clique.blockIsCreatedByProposer(minerNode));
  }

  @Test
  public void shouldMineTransactionsOnMultipleNodes() throws IOException {
    final BesuNode minerNode1 = besu.createCliqueNode("miner1");
    final BesuNode minerNode2 = besu.createCliqueNode("miner2");
    final BesuNode minerNode3 = besu.createCliqueNode("miner3");
    cluster.start(minerNode1, minerNode2, minerNode3);

    final Account sender = accounts.createAccount("account1");
    final Account receiver = accounts.createAccount("account2");

    minerNode1.execute(accountTransactions.createTransfer(sender, 50));
    cluster.verify(sender.balanceEquals(50));

    minerNode2.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 1));
    cluster.verify(receiver.balanceEquals(1));

    minerNode3.execute(accountTransactions.createIncrementalTransfers(sender, receiver, 2));
    cluster.verify(receiver.balanceEquals(3));
  }

  @Test
  public void shouldStallMiningWhenInsufficientValidators() throws IOException {
    final BesuNode minerNode1 = besu.createCliqueNode("miner1");
    final BesuNode minerNode2 = besu.createCliqueNode("miner2");
    final BesuNode minerNode3 = besu.createCliqueNode("miner3");
    cluster.start(minerNode1, minerNode2, minerNode3);

    cluster.stopNode(minerNode2);
    cluster.stopNode(minerNode3);
    minerNode1.verify(net.awaitPeerCount(0));
    minerNode1.verify(clique.blockIsCreatedByProposer(minerNode1));

    minerNode1.verify(clique.noNewBlockCreated(minerNode1));
  }

  @Test
  public void shouldStillMineWhenANodeFailsAndHasSufficientValidators() throws IOException {
    final BesuNode minerNode1 = besu.createCliqueNode("miner1");
    final BesuNode minerNode2 = besu.createCliqueNode("miner2");
    final BesuNode minerNode3 = besu.createCliqueNode("miner3");
    cluster.start(minerNode1, minerNode2, minerNode3);

    cluster.verifyOnActiveNodes(blockchain.reachesHeight(minerNode1, 1, 85));

    cluster.stopNode(minerNode3);
    cluster.verifyOnActiveNodes(net.awaitPeerCount(1));

    cluster.verifyOnActiveNodes(blockchain.reachesHeight(minerNode1, 2));
    cluster.verifyOnActiveNodes(clique.blockIsCreatedByProposer(minerNode1));
    cluster.verifyOnActiveNodes(clique.blockIsCreatedByProposer(minerNode2));
  }

  @Test
  public void shouldMineBlocksWithBlockPeriodAccordingToTransitions() throws IOException {

    final var cliqueOptions = new CliqueOptions(3, CliqueOptions.DEFAULT.epochLength(), true);
    final BesuNode minerNode = besu.createCliqueNode("miner1", cliqueOptions);

    // setup transitions
    final Map<String, Object> decreasePeriodTransitionTo2 =
        Map.of("block", 2, "blockperiodseconds", 2);
    final Map<String, Object> decreasePeriodTransitionTo1 =
        Map.of("block", 3, "blockperiodseconds", 1);
    final Optional<String> initialGenesis =
        minerNode.getGenesisConfigProvider().create(List.of(minerNode));
    final String genesisWithTransitions =
        prependTransitionsToCliqueOptions(
            initialGenesis.orElseThrow(),
            List.of(decreasePeriodTransitionTo2, decreasePeriodTransitionTo1));
    minerNode.setGenesisConfig(genesisWithTransitions);

    // Mine 4 blocks
    cluster.start(minerNode);
    minerNode.verify(blockchain.reachesHeight(minerNode, 3));

    // Assert the block period decreased after each transition
    final EthBlock.Block block1 =
        minerNode.execute(ethTransactions.block(DefaultBlockParameter.valueOf(BigInteger.ONE)));
    final EthBlock.Block block2 =
        minerNode.execute(ethTransactions.block(DefaultBlockParameter.valueOf(BigInteger.TWO)));
    final EthBlock.Block block3 =
        minerNode.execute(
            ethTransactions.block(DefaultBlockParameter.valueOf(BigInteger.valueOf(3))));
    final EthBlock.Block block4 = minerNode.execute(ethTransactions.block());
    assertThat(block2.getTimestamp().subtract(block1.getTimestamp()).longValue()).isGreaterThan(2);
    assertThat(block3.getTimestamp().subtract(block2.getTimestamp()).longValue()).isLessThan(3);
    assertThat(block4.getTimestamp().subtract(block3.getTimestamp()).longValue()).isLessThan(2);
  }

  private String prependTransitionsToCliqueOptions(
      final String originalOptions, final List<Map<String, Object>> transitions) {
    final StringBuilder stringBuilder =
        new StringBuilder()
            .append(formatCliqueTransitionsOptions(transitions))
            .append(",\n")
            .append(quote("clique"))
            .append(": {");

    return originalOptions.replace(quote("clique") + ": {", stringBuilder.toString());
  }

  private String formatCliqueTransitionsOptions(final List<Map<String, Object>> transitions) {
    final StringBuilder stringBuilder = new StringBuilder();

    stringBuilder.append(quote("transitions"));
    stringBuilder.append(": {\n");
    stringBuilder.append(quote("clique"));
    stringBuilder.append(": [");
    final String formattedTransitions =
        transitions.stream().map(this::formatTransition).collect(joining(",\n"));
    stringBuilder.append(formattedTransitions);
    stringBuilder.append("\n]");
    stringBuilder.append("}\n");

    return stringBuilder.toString();
  }

  private String quote(final Object value) {
    return '"' + value.toString() + '"';
  }

  private String formatTransition(final Map<String, Object> transition) {
    final StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("{");
    String formattedTransition =
        transition.keySet().stream()
            .map(key -> formatKeyValues(key, transition.get(key)))
            .collect(joining(","));
    stringBuilder.append(formattedTransition);
    stringBuilder.append("}");
    return stringBuilder.toString();
  }

  private String formatKeyValues(final Object... keyOrValue) {
    if (keyOrValue.length % 2 == 1) {
      // An odd number of strings cannot form a set of key-value pairs
      throw new IllegalArgumentException("Must supply key-value pairs");
    }
    final StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0; i < keyOrValue.length; i += 2) {
      if (i > 0) {
        stringBuilder.append(", ");
      }
      final String key = keyOrValue[i].toString();
      final Object value = keyOrValue[i + 1];
      final String valueStr = value instanceof String ? quote(value) : value.toString();
      stringBuilder.append(String.format("\n%s: %s", quote(key), valueStr));
    }
    return stringBuilder.toString();
  }
}
