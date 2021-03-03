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
package org.hyperledger.besu.consensus.qbft;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public class QbftConsensusRoundIdentifier extends ConsensusRoundIdentifier {

  public QbftConsensusRoundIdentifier(final ConsensusRoundIdentifier consensusRoundIdentifier) {
    super(consensusRoundIdentifier.getSequenceNumber(), consensusRoundIdentifier.getRoundNumber());
  }

  public static QbftConsensusRoundIdentifier readFrom(final RLPInput in) {
    return new QbftConsensusRoundIdentifier(in.readLongScalar(), in.readIntScalar());
  }

  @Override
  public void writeTo(final RLPOutput out) {
    out.writeLongScalar(getSequenceNumber());
    out.writeIntScalar(getRoundNumber());
  }
}
