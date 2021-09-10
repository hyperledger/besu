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
package org.hyperledger.besu.consensus.qbt.support;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.messagewrappers.BftMessage;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.payload.ProposalPayload;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.consensus.qbt.support.RoundChangeMessage.SignedRoundChange;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.tuweni.bytes.Bytes;

public class ProposalMessage implements RlpTestCaseMessage {
  private static final BftExtraDataCodec bftExtraDataCodec = new QbftExtraDataCodec();

  private final SignedProposal signedProposal;
  private final List<SignedRoundChange> roundChanges;

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, defaultImpl = PrepareMessage.class)
  private final List<PrepareMessage> prepares;

  public ProposalMessage(
      @JsonProperty("signedProposal") final SignedProposal signedProposal,
      @JsonProperty("roundChanges") final List<SignedRoundChange> roundChanges,
      @JsonProperty("prepares") final List<PrepareMessage> prepares) {
    this.signedProposal = signedProposal;
    this.roundChanges = roundChanges;
    this.prepares = prepares;
  }

  @Override
  public BftMessage<ProposalPayload> fromRlp(final Bytes rlp) {
    return Proposal.decode(rlp, bftExtraDataCodec);
  }

  @Override
  public BftMessage<ProposalPayload> toBftMessage() {
    final List<SignedData<RoundChangePayload>> signedRoundChanges =
        roundChanges.stream()
            .map(SignedRoundChange::toSignedRoundChangePayload)
            .collect(Collectors.toList());
    final List<SignedData<PreparePayload>> signedPrepares =
        prepares.stream().map(PrepareMessage::toSignedPreparePayload).collect(Collectors.toList());
    final Block block =
        Block.readFrom(
            RLP.input(Bytes.fromHexString(signedProposal.unsignedProposal.block)),
            BftBlockHeaderFunctions.forCommittedSeal(new QbftExtraDataCodec()));
    final ProposalPayload proposalPayload =
        new ProposalPayload(
            new ConsensusRoundIdentifier(
                signedProposal.unsignedProposal.sequence, signedProposal.unsignedProposal.round),
            block);
    final SignedData<ProposalPayload> signedProposalPayload =
        SignedData.create(
            proposalPayload,
            SignatureAlgorithmFactory.getInstance()
                .decodeSignature(Bytes.fromHexString(signedProposal.signature)));
    return new Proposal(signedProposalPayload, signedRoundChanges, signedPrepares);
  }

  public static class SignedProposal {
    private final UnsignedProposal unsignedProposal;
    private final String signature;

    public SignedProposal(
        @JsonProperty("unsignedProposal") final UnsignedProposal unsignedProposal,
        @JsonProperty("signature") final String signature) {
      this.unsignedProposal = unsignedProposal;
      this.signature = signature;
    }
  }

  public static class UnsignedProposal {
    private final long sequence;
    private final int round;
    private final String block;

    @JsonCreator
    public UnsignedProposal(
        @JsonProperty("sequence") final long sequence,
        @JsonProperty("round") final int round,
        @JsonProperty("block") final String block) {
      this.sequence = sequence;
      this.round = round;
      this.block = block;
    }
  }
}
