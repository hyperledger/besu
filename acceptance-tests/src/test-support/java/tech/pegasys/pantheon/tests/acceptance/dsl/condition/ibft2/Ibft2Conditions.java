/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.tests.acceptance.dsl.condition.ibft2;

import static tech.pegasys.pantheon.tests.acceptance.dsl.transaction.clique.CliqueTransactions.LATEST;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ibft2.Ibft2Transactions;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;

public class Ibft2Conditions {

  private final Ibft2Transactions ibftTwo;

  public Ibft2Conditions(final Ibft2Transactions ibftTwo) {
    this.ibftTwo = ibftTwo;
  }

  public List<PantheonNode> validators(final PantheonNode[] nodes) {
    final Comparator<PantheonNode> compareByAddress =
        Comparator.comparing(PantheonNode::getAddress);
    List<PantheonNode> pantheonNodes = Arrays.asList(nodes);
    pantheonNodes.sort(compareByAddress);
    return pantheonNodes;
  }

  public ExpectValidators validatorsEqual(final PantheonNode... validators) {
    return new ExpectValidators(ibftTwo, validatorAddresses(validators));
  }

  private Address[] validatorAddresses(final PantheonNode[] validators) {
    return Arrays.stream(validators).map(PantheonNode::getAddress).sorted().toArray(Address[]::new);
  }

  public Condition awaitValidatorSetChange(final Node node) {
    return new AwaitValidatorSetChange(node.execute(ibftTwo.createGetValidators(LATEST)), ibftTwo);
  }

  public Condition noProposals() {
    return new ExpectProposals(ibftTwo, ImmutableMap.of());
  }

  public PendingVotesConfig pendingVotesEqual() {
    return new PendingVotesConfig(ibftTwo);
  }

  public static class PendingVotesConfig {
    private final Map<PantheonNode, Boolean> proposals = new HashMap<>();
    private final Ibft2Transactions ibft;

    private PendingVotesConfig(final Ibft2Transactions ibft) {
      this.ibft = ibft;
    }

    public PendingVotesConfig addProposal(final PantheonNode node) {
      proposals.put(node, true);
      return this;
    }

    public PendingVotesConfig removeProposal(final PantheonNode node) {
      proposals.put(node, false);
      return this;
    }

    public Condition build() {
      final Map<Address, Boolean> proposalsAsAddress =
          this.proposals.entrySet().stream()
              .collect(Collectors.toMap(p -> p.getKey().getAddress(), Entry::getValue));
      return new tech.pegasys.pantheon.tests.acceptance.dsl.condition.ibft2.ExpectProposals(
          ibft, proposalsAsAddress);
    }
  }
}
