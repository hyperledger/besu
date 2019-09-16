/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.consensus.ibft.messagewrappers;

import org.hyperledger.besu.consensus.ibft.IbftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.ibft.payload.ProposalPayload;
import org.hyperledger.besu.consensus.ibft.payload.RoundChangeCertificate;
import org.hyperledger.besu.consensus.ibft.payload.SignedData;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Optional;

public class Proposal extends IbftMessage<ProposalPayload> {

  private final Block proposedBlock;

  private final Optional<RoundChangeCertificate> roundChangeCertificate;

  public Proposal(
      final SignedData<ProposalPayload> payload,
      final Block proposedBlock,
      final Optional<RoundChangeCertificate> certificate) {
    super(payload);
    this.proposedBlock = proposedBlock;
    this.roundChangeCertificate = certificate;
  }

  public Block getBlock() {
    return proposedBlock;
  }

  public Hash getDigest() {
    return getPayload().getDigest();
  }

  public Optional<RoundChangeCertificate> getRoundChangeCertificate() {
    return roundChangeCertificate;
  }

  @Override
  public BytesValue encode() {
    final BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    rlpOut.startList();
    getSignedPayload().writeTo(rlpOut);
    proposedBlock.writeTo(rlpOut);
    if (roundChangeCertificate.isPresent()) {
      roundChangeCertificate.get().writeTo(rlpOut);
    } else {
      rlpOut.writeNull();
    }
    rlpOut.endList();
    return rlpOut.encoded();
  }

  public static Proposal decode(final BytesValue data) {
    RLPInput rlpIn = RLP.input(data);
    rlpIn.enterList();
    final SignedData<ProposalPayload> payload = SignedData.readSignedProposalPayloadFrom(rlpIn);
    final Block proposedBlock = Block.readFrom(rlpIn, IbftBlockHeaderFunctions.forCommittedSeal());

    final Optional<RoundChangeCertificate> roundChangeCertificate =
        readRoundChangeCertificate(rlpIn);

    rlpIn.leaveList();
    return new Proposal(payload, proposedBlock, roundChangeCertificate);
  }

  private static Optional<RoundChangeCertificate> readRoundChangeCertificate(final RLPInput rlpIn) {
    RoundChangeCertificate roundChangeCertificate = null;
    if (!rlpIn.nextIsNull()) {
      roundChangeCertificate = RoundChangeCertificate.readFrom(rlpIn);
    } else {
      rlpIn.skipNext();
    }

    return Optional.ofNullable(roundChangeCertificate);
  }
}
