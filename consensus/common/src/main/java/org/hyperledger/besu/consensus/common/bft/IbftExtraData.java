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
package org.hyperledger.besu.consensus.common.bft;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

/**
 * Represents the data structure stored in the extraData field of the BlockHeader used when
 * operating under an BFT consensus mechanism.
 */
public class IbftExtraData implements BftExtraData {
  private static final Logger LOG = LogManager.getLogger();

  private final Bytes vanityData;
  private final Collection<SECPSignature> seals;
  private final Optional<Vote> vote;
  private final int round;
  private final Collection<Address> validators;

  public IbftExtraData(
      final Bytes vanityData,
      final Collection<SECPSignature> seals,
      final Optional<Vote> vote,
      final int round,
      final Collection<Address> validators) {

    checkNotNull(vanityData);
    checkNotNull(seals);
    checkNotNull(validators);

    this.vanityData = vanityData;
    this.seals = seals;
    this.round = round;
    this.validators = validators;
    this.vote = vote;
  }

  @Override
  public Bytes encode() {
    return encode(EncodingType.ALL);
  }

  @Override
  public Bytes encodeWithoutCommitSeals() {
    return encode(EncodingType.EXCLUDE_COMMIT_SEALS);
  }

  @Override
  public Bytes encodeWithoutCommitSealsAndRoundNumber() {
    return encode(EncodingType.EXCLUDE_COMMIT_SEALS_AND_ROUND_NUMBER);
  }

  static BftExtraData fromAddresses(Collection<Address> addresses) {
    return new IbftExtraData(
        Bytes.wrap(new byte[32]), Collections.emptyList(), Optional.empty(), 0, addresses);
  }

  static BftExtraData decode(BlockHeader blockHeader) {
    final Object inputExtraData = blockHeader.getParsedExtraData();
    if (inputExtraData instanceof BftExtraData) {
      return (BftExtraData) inputExtraData;
    }
    IbftExtraData.LOG.warn(
        "Expected a BftExtraData instance but got {}. Reparsing required.",
        inputExtraData != null ? inputExtraData.getClass().getName() : "null");
    return decodeRaw(blockHeader.getExtraData());
  }

  static BftExtraData decodeRaw(Bytes input) {
    if (input.isEmpty()) {
      throw new IllegalArgumentException("Invalid Bytes supplied - Bft Extra Data required.");
    }

    final RLPInput rlpInput = new BytesValueRLPInput(input, false);

    rlpInput.enterList(); // This accounts for the "root node" which contains BFT data items.
    final Bytes vanityData = rlpInput.readBytes();
    final List<Address> validators = rlpInput.readList(Address::readFrom);
    final Optional<Vote> vote;
    if (rlpInput.nextIsNull()) {
      vote = Optional.empty();
      rlpInput.skipNext();
    } else {
      vote = Optional.of(Vote.readFrom(rlpInput));
    }
    final int round = rlpInput.readInt();
    final List<SECPSignature> seals =
        rlpInput.readList(
            rlp -> SignatureAlgorithmFactory.getInstance().decodeSignature(rlp.readBytes()));
    rlpInput.leaveList();

    return new IbftExtraData(vanityData, seals, vote, round, validators);
  }

  static String createGenesisExtraDataString(List<Address> validators) {
    final BftExtraData extraData =
        new IbftExtraData(
            Bytes.wrap(new byte[32]), Collections.emptyList(), Optional.empty(), 0, validators);
    return extraData.encode().toString();
  }

  private enum EncodingType {
    ALL,
    EXCLUDE_COMMIT_SEALS,
    EXCLUDE_COMMIT_SEALS_AND_ROUND_NUMBER
  }

  private Bytes encode(final EncodingType encodingType) {

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytes(vanityData);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));
    if (vote.isPresent()) {
      vote.get().writeTo(encoder);
    } else {
      encoder.writeNull();
    }

    if (encodingType != EncodingType.EXCLUDE_COMMIT_SEALS_AND_ROUND_NUMBER) {
      encoder.writeInt(round);
      if (encodingType != EncodingType.EXCLUDE_COMMIT_SEALS) {
        encoder.writeList(seals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
      }
    }
    encoder.endList();

    return encoder.encoded();
  }

  // Accessors
  @Override
  public Bytes getVanityData() {
    return vanityData;
  }

  @Override
  public Collection<SECPSignature> getSeals() {
    return seals;
  }

  @Override
  public Collection<Address> getValidators() {
    return validators;
  }

  @Override
  public Optional<Vote> getVote() {
    return vote;
  }

  @Override
  public int getRound() {
    return round;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", IbftExtraData.class.getSimpleName() + "[", "]")
        .add("vanityData=" + vanityData)
        .add("seals=" + seals)
        .add("vote=" + vote)
        .add("round=" + round)
        .add("validators=" + validators)
        .toString();
  }
}
