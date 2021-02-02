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
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.tuweni.bytes.Bytes;

public class RoundChangeMessage implements RlpTestInput {
  private final SignedRoundChange signedRoundChange;
  private final Optional<String> block;

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, defaultImpl = PrepareMessage.class)
  private final List<PrepareMessage> prepares;

  public RoundChangeMessage(
      @JsonProperty("signedRoundChange") final SignedRoundChange signedRoundChange,
      @JsonProperty("block") final Optional<String> block,
      @JsonProperty("prepares") final List<PrepareMessage> prepares) {
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
    final List<PrepareMessage> prepares =
        roundChange.getPrepares().stream()
            .map(PrepareMessage::fromSignedPreparePayload)
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
    final Optional<Block> block =
        this.block.map(
            b ->
                Block.readFrom(
                    RLP.input(Bytes.fromHexString(b)), BftBlockHeaderFunctions.forOnChainBlock()));
    final List<SignedData<PreparePayload>> signedPrepares =
        prepares.stream().map(PrepareMessage::toSignedPreparePayload).collect(Collectors.toList());
    return new RoundChange(
            SignedRoundChange.toSignedRoundChangePayload(signedRoundChange), block, signedPrepares)
        .encode();
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

    public static SignedData<RoundChangePayload> toSignedRoundChangePayload(
        final SignedRoundChange signedRoundChange) {
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
      return SignedData.create(
          roundChangePayload, Signature.decode(Bytes.fromHexString(signedRoundChange.signature)));
    }

    public static SignedRoundChange fromSignedRoundChangePayload(
        final SignedData<RoundChangePayload> signedRoundChangePayload) {
      return new SignedRoundChange(
          new UnsignedRoundChange(
              signedRoundChangePayload.getPayload().getRoundIdentifier().getSequenceNumber(),
              signedRoundChangePayload.getPayload().getRoundIdentifier().getRoundNumber(),
              signedRoundChangePayload
                  .getPayload()
                  .getPreparedRoundMetadata()
                  .map(rm -> rm.getPreparedBlockHash().toHexString()),
              signedRoundChangePayload
                  .getPayload()
                  .getPreparedRoundMetadata()
                  .map(PreparedRoundMetadata::getPreparedRound)),
          signedRoundChangePayload.getSignature().encodedBytes().toHexString());
    }
  }
}
