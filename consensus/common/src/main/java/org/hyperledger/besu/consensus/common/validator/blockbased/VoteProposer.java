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

import org.hyperledger.besu.consensus.common.validator.ValidatorVote;
import org.hyperledger.besu.consensus.common.validator.VoteType;
import org.hyperledger.besu.datatypes.Address;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Container for pending votes and selecting a vote for new blocks */
class VoteProposer {

  private final Map<Address, VoteType> proposals = new ConcurrentHashMap<>();
  private final AtomicInteger votePosition = new AtomicInteger(0);

  /**
   * Identifies an address that should be voted into the validator pool
   *
   * @param address The address to be voted in
   */
  public void auth(final Address address) {
    proposals.put(address, VoteType.ADD);
  }

  /**
   * Identifies an address that should be voted out of the validator pool
   *
   * @param address The address to be voted out
   */
  public void drop(final Address address) {
    proposals.put(address, VoteType.DROP);
  }

  /**
   * Discards a pending vote for an address if one exists
   *
   * @param address The address that should no longer be voted for
   */
  public void discard(final Address address) {
    proposals.remove(address);
  }

  public Map<Address, VoteType> getProposals() {
    return proposals;
  }

  private boolean voteNotYetCast(
      final Address localAddress,
      final Address voteAddress,
      final VoteType vote,
      final Collection<Address> validators,
      final VoteTally tally) {

    // Pre evaluate if we have a vote outstanding to auth or drop the target address
    final boolean votedAuth = tally.getOutstandingAddVotesFor(voteAddress).contains(localAddress);
    final boolean votedDrop =
        tally.getOutstandingRemoveVotesFor(voteAddress).contains(localAddress);

    // if they're a validator, we want to see them dropped, and we haven't voted to drop them yet
    if (validators.contains(voteAddress) && !votedDrop && vote == VoteType.DROP) {
      return true;
      // or if we've previously voted to auth them and we want to drop them
    } else if (votedAuth && vote == VoteType.DROP) {
      return true;
      // if they're not currently a validator and we want to see them authed and we haven't voted to
      // auth them yet
    } else if (!validators.contains(voteAddress) && !votedAuth && vote == VoteType.ADD) {
      return true;
      // or if we've previously voted to drop them and we want to see them authed
    } else if (votedDrop && vote == VoteType.ADD) {
      return true;
    }

    return false;
  }

  /**
   * Gets a valid vote from our list of pending votes
   *
   * @param localAddress The address of this validator node
   * @param tally the vote tally at the height of the chain we need a vote for
   * @return Either an address with the vote (auth or drop) or no vote if we have no valid pending
   *     votes
   */
  public Optional<ValidatorVote> getVote(final Address localAddress, final VoteTally tally) {
    final Collection<Address> validators = tally.getValidators();
    final List<Map.Entry<Address, VoteType>> validVotes = new ArrayList<>();

    proposals
        .entrySet()
        .forEach(
            proposal -> {
              if (voteNotYetCast(
                  localAddress, proposal.getKey(), proposal.getValue(), validators, tally)) {
                validVotes.add(proposal);
              }
            });

    if (validVotes.isEmpty()) {
      return Optional.empty();
    }

    // Get the next position in the voting queue we should propose
    final int currentVotePosition = votePosition.updateAndGet(i -> ++i % validVotes.size());

    final Map.Entry<Address, VoteType> voteToCast = validVotes.get(currentVotePosition);

    // Get a vote from the valid votes and return it
    return Optional.of(new ValidatorVote(voteToCast.getValue(), localAddress, voteToCast.getKey()));
  }
}
