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
package tech.pegasys.pantheon.consensus.common;

import tech.pegasys.pantheon.ethereum.core.Address;

import java.util.Objects;

import com.google.common.base.Preconditions;

public class CastVote {

  private final ValidatorVotePolarity votePolarity;
  private final Address proposer;
  private final Address recipient;

  public CastVote(
      final ValidatorVotePolarity votePolarity, final Address proposer, final Address recipient) {
    Preconditions.checkNotNull(votePolarity);
    Preconditions.checkNotNull(proposer);
    Preconditions.checkNotNull(recipient);
    this.votePolarity = votePolarity;
    this.proposer = proposer;
    this.recipient = recipient;
  }

  public ValidatorVotePolarity getVotePolarity() {
    return votePolarity;
  }

  public Address getProposer() {
    return proposer;
  }

  public Address getRecipient() {
    return recipient;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CastVote castVote = (CastVote) o;
    return votePolarity == castVote.votePolarity
        && Objects.equals(proposer, castVote.proposer)
        && Objects.equals(recipient, castVote.recipient);
  }

  @Override
  public int hashCode() {
    return Objects.hash(votePolarity, proposer, recipient);
  }
}
