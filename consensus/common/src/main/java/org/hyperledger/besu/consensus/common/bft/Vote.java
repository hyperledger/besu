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
package org.hyperledger.besu.consensus.common.bft;

import org.hyperledger.besu.consensus.common.validator.VoteType;
import org.hyperledger.besu.datatypes.Address;

import java.util.Objects;

/**
 * This class is only used to serialise/deserialise BlockHeaders and should not appear in business
 * logic.
 */
public class Vote {
  private final Address recipient;
  private final VoteType voteType;

  /** The constant ADD_BYTE_VALUE. */
  public static final byte ADD_BYTE_VALUE = (byte) 0xFF;

  /** The constant DROP_BYTE_VALUE. */
  public static final byte DROP_BYTE_VALUE = (byte) 0x0L;

  /**
   * Instantiates a new Vote.
   *
   * @param recipient the recipient
   * @param voteType the vote type
   */
  public Vote(final Address recipient, final VoteType voteType) {
    this.recipient = recipient;
    this.voteType = voteType;
  }

  /**
   * Auth vote.
   *
   * @param address the address
   * @return the vote
   */
  public static Vote authVote(final Address address) {
    return new Vote(address, VoteType.ADD);
  }

  /**
   * Drop vote.
   *
   * @param address the address
   * @return the vote
   */
  public static Vote dropVote(final Address address) {
    return new Vote(address, VoteType.DROP);
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
   * Is auth.
   *
   * @return the boolean
   */
  public boolean isAuth() {
    return voteType.equals(VoteType.ADD);
  }

  /**
   * Is drop.
   *
   * @return the boolean
   */
  public boolean isDrop() {
    return voteType.equals(VoteType.DROP);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Vote vote1 = (Vote) o;
    return recipient.equals(vote1.recipient) && voteType.equals(vote1.voteType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(recipient, voteType);
  }
}
