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

import static org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions.forCommittedSeal;

import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.messagewrappers.BftMessage;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.payload.PreparedRoundMetadata;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.tuweni.bytes.Bytes;

public class RoundChangeMessage implements RlpTestCaseMessage {
  private static final BftExtraDataCodec bftExtraDataCodec = new QbftExtraDataCodec();

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
  public BftMessage<RoundChangePayload> fromRlp(final Bytes rlp) {
    return RoundChange.decode(rlp, bftExtraDataCodec);
  }

  @Override
  public BftMessage<RoundChangePayload> toBftMessage() {
    final Optional<RLPInput> blockRlp = this.block.map(s -> RLP.input(Bytes.fromHexString(s)));
    final Optional<Block> block =
        blockRlp.map(r -> Block.readFrom(r, forCommittedSeal(new QbftExtraDataCodec())));
    final List<SignedData<PreparePayload>> signedPrepares =
        prepares.stream().map(PrepareMessage::toSignedPreparePayload).collect(Collectors.toList());
    return new RoundChange(
        SignedRoundChange.toSignedRoundChangePayload(signedRoundChange), block, signedPrepares);
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
          roundChangePayload,
          SignatureAlgorithmFactory.getInstance()
              .decodeSignature(Bytes.fromHexString(signedRoundChange.signature)));
    }
  }
}
