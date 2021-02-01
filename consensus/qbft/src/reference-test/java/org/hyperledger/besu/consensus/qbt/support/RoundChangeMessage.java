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
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.payload.PreparedRoundMetadata;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.consensus.qbt.support.PrepareMessage.SignedPrepare;
import org.hyperledger.besu.consensus.qbt.support.PrepareMessage.UnsignedPrepare;
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

public class RoundChangeMessage implements RlpTestInput {
  private final SignedRoundChange signedRoundChange;
  private final Optional<String> block;
  private final List<SignedPrepare> prepares;

  public RoundChangeMessage(
      @JsonProperty("signedRoundChange") final SignedRoundChange signedRoundChange,
      @JsonProperty("block") final Optional<String> block,
      @JsonProperty("prepares") final List<SignedPrepare> prepares) {
    this.signedRoundChange = signedRoundChange;
    this.block = block;
    this.prepares = prepares;
  }

  @Override
  public RlpTestInput fromRlp(final Bytes rlp) {
    final RoundChange roundChange = RoundChange.decode(rlp);
    final UnsignedRoundChange unsignedRoundChange =
        new UnsignedRoundChange(
            roundChange.getRoundIdentifier().getSequenceNumber(),
            roundChange.getRoundIdentifier().getRoundNumber(),
            roundChange
                .getPreparedRoundMetadata()
                .map(rm -> rm.getPreparedBlockHash().toHexString()),
            roundChange.getPreparedRoundMetadata().map(PreparedRoundMetadata::getPreparedRound));
    final List<SignedPrepare> prepares =
        roundChange.getPrepares().stream()
            .map(
                p ->
                    new SignedPrepare(
                        new UnsignedPrepare(
                            p.getPayload().getRoundIdentifier().getSequenceNumber(),
                            p.getPayload().getRoundIdentifier().getRoundNumber(),
                            p.getPayload().getDigest().toHexString()),
                        p.getSignature().encodedBytes().toHexString()))
            .collect(Collectors.toList());
    return new RoundChangeMessage(
        new SignedRoundChange(
            unsignedRoundChange,
            roundChange.getSignedPayload().getSignature().encodedBytes().toHexString()),
        roundChange.getProposedBlock().map(b -> b.toRlp().toHexString()),
        prepares);
  }

  @Override
  public Bytes toRlp() {
    final UnsignedRoundChange unsignedRoundChange = signedRoundChange.unsignedRoundChange;
    final Optional<PreparedRoundMetadata> preparedRoundMetadata =
        unsignedRoundChange.preparedRound.isPresent()
                && unsignedRoundChange.preparedValue.isPresent()
            ? Optional.of(
                new PreparedRoundMetadata(
                    Hash.fromHexString(unsignedRoundChange.preparedValue.get()),
                    unsignedRoundChange.preparedRound.get()))
            : Optional.empty();
    final RoundChangePayload roundChangePayload =
        new RoundChangePayload(
            new ConsensusRoundIdentifier(unsignedRoundChange.sequence, unsignedRoundChange.round),
            preparedRoundMetadata);
    final SignedData<RoundChangePayload> signedRoundChangePayload =
        SignedData.create(
            roundChangePayload, Signature.decode(Bytes.fromHexString(signedRoundChange.signature)));
    final Optional<Block> block =
        this.block.map(
            b ->
                Block.readFrom(
                    RLP.input(Bytes.fromHexString(b)), BftBlockHeaderFunctions.forOnChainBlock()));
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
    return new RoundChange(signedRoundChangePayload, block, signedPrepares).encode();
  }

  public static class UnsignedRoundChange {
    private final long sequence;
    private final int round;
    private final Optional<String> preparedValue;
    private final Optional<Integer> preparedRound;

    @JsonCreator
    public UnsignedRoundChange(
        @JsonProperty("sequence") final long sequence,
        @JsonProperty("round") final int round,
        @JsonProperty("preparedValue") final Optional<String> preparedValue,
        @JsonProperty("preparedRound") final Optional<Integer> preparedRound) {
      this.sequence = sequence;
      this.round = round;
      this.preparedValue = preparedValue;
      this.preparedRound = preparedRound;
    }

    public long getSequence() {
      return sequence;
    }

    public int getRound() {
      return round;
    }

    public Optional<String> getPreparedValue() {
      return preparedValue;
    }

    public Optional<Integer> getPreparedRound() {
      return preparedRound;
    }
  }

  public static class SignedRoundChange {
    private final UnsignedRoundChange unsignedRoundChange;
    private final String signature;

    public SignedRoundChange(
        @JsonProperty("unsignedRoundChange") final UnsignedRoundChange unsignedRoundChange,
        @JsonProperty("signature") final String signature) {
      this.unsignedRoundChange = unsignedRoundChange;
      this.signature = signature;
    }

    public UnsignedRoundChange getUnsignedRoundChange() {
      return unsignedRoundChange;
    }

    public String getSignature() {
      return signature;
    }
  }
}
