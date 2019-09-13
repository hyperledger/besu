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
package tech.pegasys.pantheon.tests.acceptance.dsl.condition.clique;

import static java.util.Collections.emptyList;
import static tech.pegasys.pantheon.ethereum.core.Hash.fromHexString;
import static tech.pegasys.pantheon.tests.acceptance.dsl.transaction.clique.CliqueTransactions.LATEST;

import tech.pegasys.pantheon.config.CliqueConfigOptions;
import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.blockchain.ExpectBlockNotCreated;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.clique.ExpectNonceVote.CLIQUE_NONCE_VOTE;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.clique.CliqueTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eth.EthTransactions;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import org.web3j.protocol.core.DefaultBlockParameter;

public class CliqueConditions {

  private final EthTransactions eth;
  private final CliqueTransactions clique;

  public CliqueConditions(final EthTransactions eth, final CliqueTransactions clique) {
    this.eth = eth;
    this.clique = clique;
  }

  public Condition validatorsEqual(final PantheonNode... validators) {
    return new ExpectValidators(clique, validatorAddresses(validators));
  }

  public Condition validatorsAtBlockEqual(
      final String blockNumber, final PantheonNode... validators) {
    return new ExpectValidatorsAtBlock(clique, blockNumber, validatorAddresses(validators));
  }

  public Condition validatorsAtBlockHashFromBlockNumberEqual(
      final Node node, final long blockNumber, final PantheonNode... validators) {
    final DefaultBlockParameter blockParameter =
        DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber));
    final String blockHash = node.execute(eth.block(blockParameter)).getHash();
    return new ExpectValidatorsAtBlockHash(
        clique, fromHexString(blockHash), validatorAddresses(validators));
  }

  public ProposalsConfig proposalsEqual() {
    return new ProposalsConfig(clique);
  }

  public Condition noProposals() {
    return new ExpectProposals(clique, ImmutableMap.of());
  }

  public Condition nonceVoteEquals(final CLIQUE_NONCE_VOTE clique_nonce_vote) {
    return new ExpectNonceVote(eth, clique_nonce_vote);
  }

  public Condition noNewBlockCreated(final PantheonNode node) {
    final int blockPeriodSeconds = cliqueBlockPeriod(node);
    final int blockPeriodWait = blockPeriodSeconds * 1000;
    return new ExpectBlockNotCreated(eth, blockPeriodWait, blockPeriodWait);
  }

  public Condition awaitSignerSetChange(final Node node) {
    return new AwaitSignerSetChange(node.execute(clique.createGetSigners(LATEST)), clique);
  }

  private int cliqueBlockPeriod(final PantheonNode node) {
    final String config = node.getGenesisConfigProvider().create(emptyList()).get();
    final GenesisConfigFile genesisConfigFile = GenesisConfigFile.fromConfig(config);
    final CliqueConfigOptions cliqueConfigOptions =
        genesisConfigFile.getConfigOptions().getCliqueConfigOptions();
    return cliqueConfigOptions.getBlockPeriodSeconds();
  }

  private Address[] validatorAddresses(final PantheonNode[] validators) {
    return Arrays.stream(validators).map(PantheonNode::getAddress).sorted().toArray(Address[]::new);
  }

  public Condition blockIsCreatedByProposer(final PantheonNode proposer) {
    return new ExpectedBlockHasProposer(eth, proposer.getAddress());
  }

  public static class ProposalsConfig {

    private final Map<PantheonNode, Boolean> proposals = new HashMap<>();
    private final CliqueTransactions clique;

    public ProposalsConfig(final CliqueTransactions clique) {
      this.clique = clique;
    }

    public ProposalsConfig addProposal(final PantheonNode node) {
      proposals.put(node, true);
      return this;
    }

    public ProposalsConfig removeProposal(final PantheonNode node) {
      proposals.put(node, false);
      return this;
    }

    public Condition build() {
      final Map<Address, Boolean> proposalsAsAddress =
          this.proposals.entrySet().stream()
              .collect(Collectors.toMap(p -> p.getKey().getAddress(), Entry::getValue));
      return new ExpectProposals(clique, proposalsAsAddress);
    }
  }
}
