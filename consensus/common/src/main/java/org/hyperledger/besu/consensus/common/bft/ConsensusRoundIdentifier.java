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

import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Objects;

import com.google.common.base.MoreObjects;

/**
 * Represents the chain index (i.e. height) and number of attempted consensuses conducted at this
 * height.
 */
public class ConsensusRoundIdentifier implements Comparable<ConsensusRoundIdentifier> {

  private final long sequence;
  private final int round;

  /**
   * Constructor for a round identifier
   *
   * @param sequence Sequence number for this round, synonymous with block height
   * @param round round number for the current attempt at achieving consensus
   */
  public ConsensusRoundIdentifier(final long sequence, final int round) {
    this.sequence = sequence;
    this.round = round;
  }

  /**
   * Constructor that derives the sequence and round information from an RLP encoded message
   *
   * @param in The RLP body of the message to check
   * @return A derived sequence and round number
   */
  public static ConsensusRoundIdentifier readFrom(final RLPInput in) {
    return new ConsensusRoundIdentifier(in.readLong(), in.readInt());
  }

  /**
   * Adds this rounds information to a given RLP buffer
   *
   * @param out The RLP buffer to add to
   */
  public void writeTo(final RLPOutput out) {
    out.writeLong(sequence);
    out.writeInt(round);
  }

  /**
   * Gets round number.
   *
   * @return the round number
   */
  public int getRoundNumber() {
    return this.round;
  }

  /**
   * Gets sequence number.
   *
   * @return the sequence number
   */
  public long getSequenceNumber() {
    return this.sequence;
  }

  /**
   * Comparator for round identifiers to achieve ordering
   *
   * @param v The round to compare this one to
   * @return a negative integer, zero, or a positive integer as this object is less than, equal to,
   *     or greater than the specified object.
   */
  @Override
  public int compareTo(final ConsensusRoundIdentifier v) {
    final int sequenceComparison = Long.compareUnsigned(sequence, v.sequence);
    if (sequenceComparison != 0) {
      return sequenceComparison;
    } else {
      return Integer.compare(round, v.round);
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("Sequence", sequence)
        .add("Round", round)
        .toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ConsensusRoundIdentifier that = (ConsensusRoundIdentifier) o;
    return sequence == that.sequence && round == that.round;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sequence, round);
  }
}
