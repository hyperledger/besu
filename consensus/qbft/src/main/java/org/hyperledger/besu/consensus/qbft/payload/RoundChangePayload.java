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
package org.hyperledger.besu.consensus.qbft.payload;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.qbft.messagedata.QbftV1;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.MoreObjects;

public class RoundChangePayload extends QbftPayload {
  private static final int TYPE = QbftV1.ROUND_CHANGE;
  private final ConsensusRoundIdentifier roundChangeIdentifier;
  private final Optional<PreparedRoundMetadata> preparedRoundMetadata;

  public RoundChangePayload(
      final ConsensusRoundIdentifier roundChangeIdentifier,
      final Optional<PreparedRoundMetadata> preparedRoundMetadata) {
    this.roundChangeIdentifier = roundChangeIdentifier;
    this.preparedRoundMetadata = preparedRoundMetadata;
  }

  @Override
  public ConsensusRoundIdentifier getRoundIdentifier() {
    return roundChangeIdentifier;
  }

  public Optional<PreparedRoundMetadata> getPreparedRoundMetadata() {
    return preparedRoundMetadata;
  }

  @Override
  public void writeTo(final RLPOutput rlpOutput) {
    // RLP encode of the message data content (round identifier and prepared certificate)
    rlpOutput.startList();
    writeConsensusRound(rlpOutput);

    rlpOutput.startList();
    preparedRoundMetadata.ifPresent(prm -> prm.writeTo(rlpOutput));
    rlpOutput.endList();

    rlpOutput.endList();
  }

  public static RoundChangePayload readFrom(final RLPInput rlpInput) {
    rlpInput.enterList();
    final ConsensusRoundIdentifier roundIdentifier = readConsensusRound(rlpInput);
    final Optional<PreparedRoundMetadata> preparedRoundMetadata;

    rlpInput.enterList();
    if (rlpInput.isEndOfCurrentList()) {
      preparedRoundMetadata = Optional.empty();
    } else {
      preparedRoundMetadata = Optional.of(PreparedRoundMetadata.readFrom(rlpInput));
    }
    rlpInput.leaveList();

    rlpInput.leaveList();
    return new RoundChangePayload(roundIdentifier, preparedRoundMetadata);
  }

  @Override
  public int getMessageType() {
    return TYPE;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RoundChangePayload that = (RoundChangePayload) o;
    return Objects.equals(roundChangeIdentifier, that.roundChangeIdentifier)
        && Objects.equals(preparedRoundMetadata, that.preparedRoundMetadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(roundChangeIdentifier, preparedRoundMetadata);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("roundChangeIdentifier", roundChangeIdentifier)
        .add("preparedRoundMetadata", preparedRoundMetadata)
        .toString();
  }
}
