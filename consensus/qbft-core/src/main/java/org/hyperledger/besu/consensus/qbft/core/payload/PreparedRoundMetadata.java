/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.consensus.qbft.core.payload;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Objects;

import com.google.common.base.MoreObjects;

/** The Prepared round metadata. */
public class PreparedRoundMetadata {
  private final Hash preparedBlockHash;
  private final int preparedRound;

  /**
   * Instantiates a new Prepared round metadata.
   *
   * @param preparedBlockHash the prepared block hash
   * @param preparedRound the prepared round
   */
  public PreparedRoundMetadata(final Hash preparedBlockHash, final int preparedRound) {
    this.preparedBlockHash = preparedBlockHash;
    this.preparedRound = preparedRound;
  }

  /**
   * Gets prepared block hash.
   *
   * @return the prepared block hash
   */
  public Hash getPreparedBlockHash() {
    return preparedBlockHash;
  }

  /**
   * Gets prepared round.
   *
   * @return the prepared round
   */
  public int getPreparedRound() {
    return preparedRound;
  }

  /**
   * Constructor that derives the sequence and round information from an RLP encoded message
   *
   * @param in The RLP body of the message to check
   * @return A PreparedRoundMetadata as extracted from the supplied RLP input
   */
  public static PreparedRoundMetadata readFrom(final RLPInput in) {
    final int preparedRound = in.readIntScalar();
    final Hash preparedBlockHash = Hash.wrap(in.readBytes32());
    return new PreparedRoundMetadata(preparedBlockHash, preparedRound);
  }

  /**
   * Adds this rounds information to a given RLP buffer
   *
   * @param out The RLP buffer to add to
   */
  public void writeTo(final RLPOutput out) {
    out.writeIntScalar(preparedRound);
    out.writeBytes(preparedBlockHash);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PreparedRoundMetadata that = (PreparedRoundMetadata) o;
    return preparedRound == that.preparedRound
        && Objects.equals(preparedBlockHash, that.preparedBlockHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(preparedBlockHash, preparedRound);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("preparedBlockHash", preparedBlockHash)
        .add("preparedRound", preparedRound)
        .toString();
  }
}
