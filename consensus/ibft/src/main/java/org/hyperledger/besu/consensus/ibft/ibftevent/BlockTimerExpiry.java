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
package org.hyperledger.besu.consensus.ibft.ibftevent;

import org.hyperledger.besu.consensus.ibft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.ibft.ibftevent.IbftEvents.Type;

import java.util.Objects;

import com.google.common.base.MoreObjects;

/** Event indicating a block timer has expired */
public final class BlockTimerExpiry implements IbftEvent {
  private final ConsensusRoundIdentifier roundIdentifier;

  /**
   * Constructor for a BlockTimerExpiry event
   *
   * @param roundIdentifier The roundIdentifier that the expired timer belonged to
   */
  public BlockTimerExpiry(final ConsensusRoundIdentifier roundIdentifier) {
    this.roundIdentifier = roundIdentifier;
  }

  @Override
  public Type getType() {
    return Type.BLOCK_TIMER_EXPIRY;
  }

  public ConsensusRoundIdentifier getRoundIndentifier() {
    return roundIdentifier;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("Round Identifier", roundIdentifier).toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BlockTimerExpiry that = (BlockTimerExpiry) o;
    return Objects.equals(roundIdentifier, that.roundIdentifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(roundIdentifier);
  }
}
