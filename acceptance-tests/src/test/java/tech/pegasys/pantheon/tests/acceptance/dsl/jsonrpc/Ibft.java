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
package tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.ibft.ExpectProposals;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.ibft.ExpectValidators;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ibft.IbftTransactions;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;

public class Ibft {

  private final IbftTransactions ibft;

  public Ibft(final IbftTransactions ibft) {
    this.ibft = ibft;
  }

  public List<PantheonNode> validators(final PantheonNode[] nodes) {
    final Comparator<PantheonNode> compareByAddress =
        Comparator.comparing(PantheonNode::getAddress);
    List<PantheonNode> pantheonNodes = Arrays.asList(nodes);
    pantheonNodes.sort(compareByAddress);
    return pantheonNodes;
  }

  public ExpectValidators validatorsEqual(final PantheonNode... validators) {
    return new ExpectValidators(ibft, validatorAddresses(validators));
  }

  private Address[] validatorAddresses(final PantheonNode[] validators) {
    return Arrays.stream(validators).map(PantheonNode::getAddress).sorted().toArray(Address[]::new);
  }

  public Condition noProposals() {
    return new ExpectProposals(ibft, ImmutableMap.of());
  }

  public PendingVotesConfig pendingVotesEqual() {
    return new PendingVotesConfig(ibft);
  }

  public static class PendingVotesConfig {
    private final Map<PantheonNode, Boolean> proposals = new HashMap<>();
    private final IbftTransactions ibft;

    public PendingVotesConfig(final IbftTransactions ibft) {
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
          this.proposals
              .entrySet()
              .stream()
              .collect(Collectors.toMap(p -> p.getKey().getAddress(), Entry::getValue));
      return new tech.pegasys.pantheon.tests.acceptance.dsl.condition.ibft.ExpectProposals(
          ibft, proposalsAsAddress);
    }
  }
}
