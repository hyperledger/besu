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
package org.hyperledger.besu.consensus.common.validator.blockbased;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.common.validator.ValidatorVote;
import org.hyperledger.besu.consensus.common.validator.VoteType;
import org.hyperledger.besu.datatypes.Address;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;

public class VoteProposerTest {
  private final Address localAddress = Address.fromHexString("0");

  @Test
  public void emptyProposerReturnsNoVotes() {
    final VoteProposer proposer = new VoteProposer();

    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.emptyList())))
        .isEqualTo(Optional.empty());
    assertThat(
            proposer.getVote(
                localAddress,
                new VoteTally(
                    Arrays.asList(
                        Address.fromHexString("0"),
                        Address.fromHexString("1"),
                        Address.fromHexString("2")))))
        .isEqualTo(Optional.empty());
  }

  @Test
  public void demoteVotes() {
    final VoteProposer proposer = new VoteProposer();
    final Address a1 = Address.fromHexString("1");
    final Address a2 = Address.fromHexString("2");
    final Address a3 = Address.fromHexString("3");

    proposer.drop(a1);

    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.emptyList()))).isEmpty();
    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.singletonList(a1))))
        .contains(new ValidatorVote(VoteType.DROP, localAddress, a1));
    assertThat(proposer.getVote(localAddress, new VoteTally(Arrays.asList(a1, a2, a3))))
        .contains(new ValidatorVote(VoteType.DROP, localAddress, a1));
    assertThat(proposer.getVote(localAddress, new VoteTally(Arrays.asList(a2, a3)))).isEmpty();
  }

  @Test
  public void promoteVotes() {
    final VoteProposer proposer = new VoteProposer();
    final Address a1 = Address.fromHexString("1");
    final Address a2 = Address.fromHexString("2");
    final Address a3 = Address.fromHexString("3");

    proposer.auth(a1);

    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.emptyList())))
        .contains(new ValidatorVote(VoteType.ADD, localAddress, a1));
    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.singletonList(a1))))
        .isEmpty();
    assertThat(proposer.getVote(localAddress, new VoteTally(Arrays.asList(a1, a2, a3)))).isEmpty();
    assertThat(proposer.getVote(localAddress, new VoteTally(Arrays.asList(a2, a3))))
        .contains(new ValidatorVote(VoteType.ADD, localAddress, a1));
  }

  @Test
  public void discardVotes() {
    final VoteProposer proposer = new VoteProposer();
    final Address a1 = Address.fromHexString("1");
    final Address a2 = Address.fromHexString("2");
    final Address a3 = Address.fromHexString("3");

    proposer.auth(a1);
    proposer.auth(a2);
    proposer.discard(a2);

    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.emptyList())))
        .contains(new ValidatorVote(VoteType.ADD, localAddress, a1));
    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.singletonList(a1))))
        .isEmpty();
    assertThat(proposer.getVote(localAddress, new VoteTally(Arrays.asList(a1, a2, a3)))).isEmpty();
    assertThat(proposer.getVote(localAddress, new VoteTally(Arrays.asList(a2, a3))))
        .contains(new ValidatorVote(VoteType.ADD, localAddress, a1));
  }

  @Test
  public void getVoteCyclesAllOptions() {
    final VoteProposer proposer = new VoteProposer();
    final Address a1 = Address.fromHexString("1");
    final Address a2 = Address.fromHexString("2");
    final Address a3 = Address.fromHexString("3");

    proposer.auth(a1);
    proposer.auth(a2);
    proposer.auth(a3);

    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.emptyList())))
        .contains(new ValidatorVote(VoteType.ADD, localAddress, a2));
    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.emptyList())))
        .contains(new ValidatorVote(VoteType.ADD, localAddress, a3));
    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.emptyList())))
        .contains(new ValidatorVote(VoteType.ADD, localAddress, a1));
    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.emptyList())))
        .contains(new ValidatorVote(VoteType.ADD, localAddress, a2));
  }

  @Test
  public void getVoteSkipsInvalidVotes() {
    final VoteProposer proposer = new VoteProposer();
    final Address a1 = Address.fromHexString("1");
    final Address a2 = Address.fromHexString("2");
    final Address a3 = Address.fromHexString("3");
    final Address a4 = Address.fromHexString("4");

    proposer.auth(a1);
    proposer.auth(a2);
    proposer.auth(a3);
    proposer.drop(a4);

    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.emptyList())))
        .contains(new ValidatorVote(VoteType.ADD, localAddress, a2));
    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.emptyList())))
        .contains(new ValidatorVote(VoteType.ADD, localAddress, a3));
    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.emptyList())))
        .contains(new ValidatorVote(VoteType.ADD, localAddress, a1));
    assertThat(proposer.getVote(localAddress, new VoteTally(Collections.emptyList())))
        .contains(new ValidatorVote(VoteType.ADD, localAddress, a2));
  }

  @Test
  public void revokesAuthVotesInTally() {
    final VoteProposer proposer = new VoteProposer();
    final Address a1 = Address.fromHexString("1");
    final Address a2 = Address.fromHexString("2");
    final Address a3 = Address.fromHexString("3");

    proposer.drop(a1);

    final VoteTally tally = new VoteTally(Arrays.asList(a2, a3));
    tally.addVote(new ValidatorVote(VoteType.ADD, localAddress, a1));

    assertThat(proposer.getVote(localAddress, tally))
        .contains(new ValidatorVote(VoteType.DROP, localAddress, a1));
  }

  @Test
  public void revokesDropVotesInTally() {
    final VoteProposer proposer = new VoteProposer();
    final Address a1 = Address.fromHexString("1");
    final Address a2 = Address.fromHexString("2");
    final Address a3 = Address.fromHexString("3");

    proposer.auth(a1);

    final VoteTally tally = new VoteTally(Arrays.asList(a2, a3));
    tally.addVote(new ValidatorVote(VoteType.DROP, localAddress, a1));

    assertThat(proposer.getVote(localAddress, tally))
        .contains(new ValidatorVote(VoteType.ADD, localAddress, a1));
  }

  @Test
  public void revokesAuthVotesInTallyWhenValidatorIsInValidatorList() {
    final VoteProposer proposer = new VoteProposer();
    final Address a1 = Address.fromHexString("1");
    final Address a2 = Address.fromHexString("2");
    final Address a3 = Address.fromHexString("3");

    final VoteTally tally = new VoteTally(Arrays.asList(a1, a2, a3));
    tally.addVote(new ValidatorVote(VoteType.ADD, localAddress, a1));

    proposer.drop(a1);

    assertThat(proposer.getVote(localAddress, tally))
        .contains(new ValidatorVote(VoteType.DROP, localAddress, a1));
  }

  @Test
  public void revokesDropVotesInTallyWhenValidatorIsInValidatorList() {
    final VoteProposer proposer = new VoteProposer();
    final Address a1 = Address.fromHexString("1");
    final Address a2 = Address.fromHexString("2");
    final Address a3 = Address.fromHexString("3");

    final VoteTally tally = new VoteTally(Arrays.asList(a1, a2, a3));
    tally.addVote(new ValidatorVote(VoteType.DROP, localAddress, a1));

    proposer.auth(a1);

    assertThat(proposer.getVote(localAddress, tally))
        .contains(new ValidatorVote(VoteType.ADD, localAddress, a1));
  }
}
