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
package org.hyperledger.besu.consensus.ibft.messagewrappers;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.messagewrappers.BftMessage;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.ibft.IbftExtraDataCodec;
import org.hyperledger.besu.consensus.ibft.payload.PayloadDeserializers;
import org.hyperledger.besu.consensus.ibft.payload.PreparedCertificate;
import org.hyperledger.besu.consensus.ibft.payload.RoundChangePayload;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/** The Round change. */
public class RoundChange extends BftMessage<RoundChangePayload> {

  private static final IbftExtraDataCodec BFT_EXTRA_DATA_ENCODER = new IbftExtraDataCodec();
  private final Optional<Block> proposedBlock;

  /**
   * Instantiates a new Round change.
   *
   * @param payload the payload
   * @param proposedBlock the proposed block
   */
  public RoundChange(
      final SignedData<RoundChangePayload> payload, final Optional<Block> proposedBlock) {
    super(payload);
    this.proposedBlock = proposedBlock;
  }

  /**
   * Gets proposed block.
   *
   * @return the proposed block
   */
  public Optional<Block> getProposedBlock() {
    return proposedBlock;
  }

  /**
   * Gets prepared certificate.
   *
   * @return the prepared certificate
   */
  public Optional<PreparedCertificate> getPreparedCertificate() {
    return getPayload().getPreparedCertificate();
  }

  /**
   * Gets prepared certificate round.
   *
   * @return the prepared certificate round
   */
  public Optional<ConsensusRoundIdentifier> getPreparedCertificateRound() {
    return getPreparedCertificate()
        .map(prepCert -> prepCert.getProposalPayload().getPayload().getRoundIdentifier());
  }

  @Override
  public Bytes encode() {
    final BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    rlpOut.startList();
    getSignedPayload().writeTo(rlpOut);
    if (proposedBlock.isPresent()) {
      proposedBlock.get().writeTo(rlpOut);
    } else {
      rlpOut.writeNull();
    }
    rlpOut.endList();
    return rlpOut.encoded();
  }

  /**
   * Decode data to round change.
   *
   * @param data the data
   * @return the round change
   */
  public static RoundChange decode(final Bytes data) {

    final RLPInput rlpIn = RLP.input(data);
    rlpIn.enterList();
    final SignedData<RoundChangePayload> payload =
        PayloadDeserializers.readSignedRoundChangePayloadFrom(rlpIn);
    Optional<Block> block = Optional.empty();
    if (!rlpIn.nextIsNull()) {
      block =
          Optional.of(
              Block.readFrom(
                  rlpIn, BftBlockHeaderFunctions.forCommittedSeal(BFT_EXTRA_DATA_ENCODER)));
    } else {
      rlpIn.skipNext();
    }
    rlpIn.leaveList();

    return new RoundChange(payload, block);
  }
}
