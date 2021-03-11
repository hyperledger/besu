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
package org.hyperledger.besu.consensus.qbft.payload;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.junit.Test;

public class QbftPayloadTest {

  private final QbftPayload payload =
      new QbftPayload() {
        @Override
        public void writeTo(final RLPOutput rlpOutput) {
          writeConsensusRound(rlpOutput);
        }

        @Override
        public int getMessageType() {
          return 0;
        }

        @Override
        public ConsensusRoundIdentifier getRoundIdentifier() {
          return new ConsensusRoundIdentifier(5, 10);
        }
      };

  @Test
  public void roundAndSequenceAreEncodedAsScalars() {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    payload.writeTo(out);
    out.endList();
    final RLPInput rlpIn = RLP.input(out.encoded());

    rlpIn.enterList();
    assertThat(rlpIn.readLongScalar()).isEqualTo(payload.getRoundIdentifier().getSequenceNumber());
    assertThat(rlpIn.readIntScalar()).isEqualTo(payload.getRoundIdentifier().getRoundNumber());
    rlpIn.leaveList();
  }

  @Test
  public void readingConsensusRoundExpectsScalarValues() {
    final long sequence = 100;
    final int round = 8;
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeLongScalar(sequence);
    out.writeIntScalar(round);
    out.endList();

    final RLPInput rlpIn = RLP.input(out.encoded());
    rlpIn.enterList();
    final ConsensusRoundIdentifier roundIdentifier = QbftPayload.readConsensusRound(rlpIn);
    rlpIn.leaveList();

    assertThat(roundIdentifier.getSequenceNumber()).isEqualTo(sequence);
    assertThat(roundIdentifier.getRoundNumber()).isEqualTo(round);
  }
}
