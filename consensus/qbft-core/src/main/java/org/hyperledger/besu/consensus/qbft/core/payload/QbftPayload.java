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

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.Payload;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

/** The Qbft payload. */
public abstract class QbftPayload implements Payload {
  /** Default constructor */
  protected QbftPayload() {}

  /**
   * Write consensus round.
   *
   * @param out the out
   */
  protected void writeConsensusRound(final RLPOutput out) {
    out.writeLongScalar(getRoundIdentifier().getSequenceNumber());
    out.writeIntScalar(getRoundIdentifier().getRoundNumber());
  }

  /**
   * Read consensus round.
   *
   * @param in the rlp input
   * @return the consensus round identifier
   */
  protected static ConsensusRoundIdentifier readConsensusRound(final RLPInput in) {
    return new ConsensusRoundIdentifier(in.readLongScalar(), in.readIntScalar());
  }

  @Override
  public Hash hashForSignature() {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeIntScalar(getMessageType());
    out.writeRaw(this.encoded());
    out.endList();
    return Hash.hash(out.encoded());
  }
}
