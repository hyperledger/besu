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
package tech.pegasys.pantheon.consensus.ibft.payload;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.messagedata.IbftV2;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.NewRound;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;

import java.util.Collections;
import java.util.Objects;
import java.util.StringJoiner;

public class NewRoundPayload implements Payload {
  private static final int TYPE = IbftV2.NEW_ROUND;
  private final ConsensusRoundIdentifier roundChangeIdentifier;
  private final RoundChangeCertificate roundChangeCertificate;
  private final SignedData<ProposalPayload> proposalPayload;

  public NewRoundPayload(
      final ConsensusRoundIdentifier roundIdentifier,
      final RoundChangeCertificate roundChangeCertificate,
      final SignedData<ProposalPayload> proposalPayload) {
    this.roundChangeIdentifier = roundIdentifier;
    this.roundChangeCertificate = roundChangeCertificate;
    this.proposalPayload = proposalPayload;
  }

  @Override
  public ConsensusRoundIdentifier getRoundIdentifier() {
    return roundChangeIdentifier;
  }

  public RoundChangeCertificate getRoundChangeCertificate() {
    return roundChangeCertificate;
  }

  public SignedData<ProposalPayload> getProposalPayload() {
    return proposalPayload;
  }

  @Override
  public void writeTo(final RLPOutput rlpOutput) {
    // RLP encode of the message data content (round identifier and prepared certificate)
    rlpOutput.startList();
    roundChangeIdentifier.writeTo(rlpOutput);
    roundChangeCertificate.writeTo(rlpOutput);
    proposalPayload.writeTo(rlpOutput);
    rlpOutput.endList();
  }

  public static NewRoundPayload readFrom(final RLPInput rlpInput) {

    rlpInput.enterList();
    final ConsensusRoundIdentifier roundIdentifier = ConsensusRoundIdentifier.readFrom(rlpInput);
    final RoundChangeCertificate roundChangeCertificate = RoundChangeCertificate.readFrom(rlpInput);
    final SignedData<ProposalPayload> proposalPayload =
        SignedData.readSignedProposalPayloadFrom(rlpInput);
    rlpInput.leaveList();

    return new NewRoundPayload(roundIdentifier, roundChangeCertificate, proposalPayload);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final NewRoundPayload that = (NewRoundPayload) o;
    return Objects.equals(roundChangeIdentifier, that.roundChangeIdentifier)
        && Objects.equals(roundChangeCertificate, that.roundChangeCertificate)
        && Objects.equals(proposalPayload, that.proposalPayload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(roundChangeIdentifier, roundChangeCertificate, proposalPayload);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", NewRoundPayload.class.getSimpleName() + "[", "]")
        .add("roundChangeIdentifier=" + roundChangeIdentifier)
        .add("roundChangeCertificate=" + roundChangeCertificate)
        .add("proposalPayload=" + proposalPayload)
        .toString();
  }

  @Override
  public int getMessageType() {
    return TYPE;
  }

  public static class Builder {

    private ConsensusRoundIdentifier roundChangeIdentifier = new ConsensusRoundIdentifier(1, 0);

    private RoundChangeCertificate roundChangeCertificate =
        new RoundChangeCertificate(Collections.emptyList());

    private SignedData<ProposalPayload> proposalPayload = null;

    public Builder(
        final ConsensusRoundIdentifier roundChangeIdentifier,
        final RoundChangeCertificate roundChangeCertificate,
        final SignedData<ProposalPayload> proposalPayload) {
      this.roundChangeIdentifier = roundChangeIdentifier;
      this.roundChangeCertificate = roundChangeCertificate;
      this.proposalPayload = proposalPayload;
    }

    public static Builder fromExisting(final NewRound payload) {
      return new Builder(
          payload.getRoundIdentifier(),
          payload.getRoundChangeCertificate(),
          payload.getProposalPayload());
    }

    public void setRoundChangeIdentifier(final ConsensusRoundIdentifier roundChangeIdentifier) {
      this.roundChangeIdentifier = roundChangeIdentifier;
    }

    public void setRoundChangeCertificate(final RoundChangeCertificate roundChangeCertificate) {
      this.roundChangeCertificate = roundChangeCertificate;
    }

    public NewRoundPayload build() {
      return new NewRoundPayload(roundChangeIdentifier, roundChangeCertificate, proposalPayload);
    }
  }
}
