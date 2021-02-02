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
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.payload.PreparedRoundMetadata;
import org.hyperledger.besu.consensus.qbft.payload.ProposalPayload;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.consensus.qbt.support.PrepareMessage.UnsignedPrepare;
import org.hyperledger.besu.consensus.qbt.support.RoundChangeMessage.SignedRoundChange;
import org.hyperledger.besu.consensus.qbt.support.RoundChangeMessage.UnsignedRoundChange;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

public class ProposalMessage implements RlpTestInput {
  private final SignedProposal signedProposal;
  private final List<SignedRoundChange> roundChanges;
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
  public RlpTestInput fromRlp(final Bytes rlp) {
    final Proposal proposal = Proposal.decode(rlp);
    final SignedProposal signedProposal =
        new SignedProposal(
            new UnsignedProposal(
                proposal.getRoundIdentifier().getSequenceNumber(),
                proposal.getRoundIdentifier().getRoundNumber(),
                proposal.getBlock().toRlp().toHexString()),
            proposal.getSignedPayload().getSignature().encodedBytes().toHexString());
    final List<SignedRoundChange> signedRoundChanges =
        proposal.getRoundChanges().stream()
            .map(
                r ->
                    new SignedRoundChange(
                        new UnsignedRoundChange(
                            r.getPayload().getRoundIdentifier().getSequenceNumber(),
                            r.getPayload().getRoundIdentifier().getRoundNumber(),
                            r.getPayload()
                                .getPreparedRoundMetadata()
                                .map(rm -> rm.getPreparedBlockHash().toHexString()),
                            r.getPayload()
                                .getPreparedRoundMetadata()
                                .map(PreparedRoundMetadata::getPreparedRound)),
                        r.getSignature().encodedBytes().toHexString()))
            .collect(Collectors.toList());
    final List<PrepareMessage> signedPrepares =
        proposal.getPrepares().stream()
            .map(
                p ->
                    new PrepareMessage(
                        new UnsignedPrepare(
                            p.getPayload().getRoundIdentifier().getSequenceNumber(),
                            p.getPayload().getRoundIdentifier().getRoundNumber(),
                            p.getPayload().getDigest().toHexString()),
                        p.getSignature().encodedBytes().toHexString()))
            .collect(Collectors.toList());
    return new ProposalMessage(signedProposal, signedRoundChanges, signedPrepares);
  }

  @Override
  public Bytes toRlp() {
    final List<SignedData<RoundChangePayload>> signedRoundChanges =
        roundChanges.stream()
            .map(
                r ->
                    SignedData.create(
                        new RoundChangePayload(
                            new ConsensusRoundIdentifier(
                                r.getUnsignedRoundChange().getSequence(),
                                r.getUnsignedRoundChange().getRound()),
                            r.getUnsignedRoundChange().getPreparedRound().isPresent()
                                    && r.getUnsignedRoundChange().getPreparedValue().isPresent()
                                ? Optional.of(
                                    new PreparedRoundMetadata(
                                        Hash.fromHexString(
                                            r.getUnsignedRoundChange().getPreparedValue().get()),
                                        r.getUnsignedRoundChange().getPreparedRound().get()))
                                : Optional.empty()),
                        Signature.decode(Bytes.fromHexString(r.getSignature()))))
            .collect(Collectors.toList());

    final List<SignedData<PreparePayload>> signedPrepares =
        prepares.stream()
            .map(
                p ->
                    SignedData.create(
                        new PreparePayload(
                            new ConsensusRoundIdentifier(
                                p.getUnsignedPrepare().getSequence(),
                                p.getUnsignedPrepare().getRound()),
                            Hash.fromHexString(p.getUnsignedPrepare().getDigest())),
                        Signature.decode(Bytes.fromHexString(p.getSignature()))))
            .collect(Collectors.toList());

    final Block block =
        Block.readFrom(
            RLP.input(Bytes.fromHexString(signedProposal.unsignedProposal.block)),
            BftBlockHeaderFunctions.forOnChainBlock());
    final ProposalPayload proposalPayload =
        new ProposalPayload(
            new ConsensusRoundIdentifier(
                signedProposal.unsignedProposal.sequence, signedProposal.unsignedProposal.round),
            block);

    final SignedData<ProposalPayload> signedProposalPayload =
        SignedData.create(
            proposalPayload, Signature.decode(Bytes.fromHexString(signedProposal.signature)));
    return new Proposal(signedProposalPayload, signedRoundChanges, signedPrepares).encode();
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
