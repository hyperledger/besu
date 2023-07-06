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
package org.hyperledger.besu.consensus.common.validator;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.consensus.common.validator.VoteType.ADD;

import org.hyperledger.besu.datatypes.Address;

import java.util.Objects;

/** The Validator vote. */
public class ValidatorVote {

  private final VoteType votePolarity;
  private final Address proposer;
  private final Address recipient;

  /**
   * Instantiates a new Validator vote.
   *
   * @param votePolarity the vote polarity
   * @param proposer the proposer
   * @param recipient the recipient
   */
  public ValidatorVote(
      final VoteType votePolarity, final Address proposer, final Address recipient) {
    checkNotNull(votePolarity);
    checkNotNull(proposer);
    checkNotNull(recipient);
    this.votePolarity = votePolarity;
    this.proposer = proposer;
    this.recipient = recipient;
  }

  /**
   * Gets vote polarity.
   *
   * @return the vote polarity
   */
  public VoteType getVotePolarity() {
    return votePolarity;
  }

  /**
   * Gets proposer.
   *
   * @return the proposer
   */
  public Address getProposer() {
    return proposer;
  }

  /**
   * Gets recipient.
   *
   * @return the recipient
   */
  public Address getRecipient() {
    return recipient;
  }

  /**
   * Is auth vote.
   *
   * @return the boolean
   */
  public boolean isAuthVote() {
    return votePolarity.equals(ADD);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ValidatorVote validatorVote = (ValidatorVote) o;
    return votePolarity == validatorVote.votePolarity
        && Objects.equals(proposer, validatorVote.proposer)
        && Objects.equals(recipient, validatorVote.recipient);
  }

  @Override
  public int hashCode() {
    return Objects.hash(votePolarity, proposer, recipient);
  }
}
