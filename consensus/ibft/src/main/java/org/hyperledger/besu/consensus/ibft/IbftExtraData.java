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
package org.hyperledger.besu.consensus.ibft;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents the data structure stored in the extraData field of the BlockHeader used when
 * operating under an IBFT 2.0 consensus mechanism.
 */
public class IbftExtraData implements ParsedExtraData {
  private static final Logger LOG = LogManager.getLogger();

  public static final int EXTRA_VANITY_LENGTH = 32;

  private final BytesValue vanityData;
  private final Collection<Signature> seals;
  private final Optional<Vote> vote;
  private final int round;
  private final Collection<Address> validators;

  public IbftExtraData(
      final BytesValue vanityData,
      final Collection<Signature> seals,
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

  public static IbftExtraData fromAddresses(final Collection<Address> addresses) {
    return new IbftExtraData(
        BytesValue.wrap(new byte[32]), Collections.emptyList(), Optional.empty(), 0, addresses);
  }

  public static IbftExtraData decode(final BlockHeader blockHeader) {
    final Object inputExtraData = blockHeader.getParsedExtraData();
    if (inputExtraData instanceof IbftExtraData) {
      return (IbftExtraData) inputExtraData;
    }
    LOG.warn(
        "Expected a IbftExtraData instance but got {}. Reparsing required.",
        inputExtraData != null ? inputExtraData.getClass().getName() : "null");
    return decodeRaw(blockHeader.getExtraData());
  }

  static IbftExtraData decodeRaw(final BytesValue input) {
    final RLPInput rlpInput = new BytesValueRLPInput(input, false);

    rlpInput.enterList(); // This accounts for the "root node" which contains IBFT data items.
    final BytesValue vanityData = rlpInput.readBytesValue();
    final List<Address> validators = rlpInput.readList(Address::readFrom);
    final Optional<Vote> vote;
    if (rlpInput.nextIsNull()) {
      vote = Optional.empty();
      rlpInput.skipNext();
    } else {
      vote = Optional.of(Vote.readFrom(rlpInput));
    }
    final int round = rlpInput.readInt();
    final List<Signature> seals = rlpInput.readList(rlp -> Signature.decode(rlp.readBytesValue()));
    rlpInput.leaveList();

    return new IbftExtraData(vanityData, seals, vote, round, validators);
  }

  public BytesValue encode() {
    return encode(EncodingType.ALL);
  }

  public BytesValue encodeWithoutCommitSeals() {
    return encode(EncodingType.EXCLUDE_COMMIT_SEALS);
  }

  public BytesValue encodeWithoutCommitSealsAndRoundNumber() {
    return encode(EncodingType.EXCLUDE_COMMIT_SEALS_AND_ROUND_NUMBER);
  }

  private enum EncodingType {
    ALL,
    EXCLUDE_COMMIT_SEALS,
    EXCLUDE_COMMIT_SEALS_AND_ROUND_NUMBER
  }

  private BytesValue encode(final EncodingType encodingType) {

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytesValue(vanityData);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytesValue(validator));
    if (vote.isPresent()) {
      vote.get().writeTo(encoder);
    } else {
      encoder.writeNull();
    }

    if (encodingType != EncodingType.EXCLUDE_COMMIT_SEALS_AND_ROUND_NUMBER) {
      encoder.writeInt(round);
      if (encodingType != EncodingType.EXCLUDE_COMMIT_SEALS) {
        encoder.writeList(seals, (committer, rlp) -> rlp.writeBytesValue(committer.encodedBytes()));
      }
    }
    encoder.endList();

    return encoder.encoded();
  }

  public static String createGenesisExtraDataString(final List<Address> validators) {
    final IbftExtraData extraData =
        new IbftExtraData(
            BytesValue.wrap(new byte[32]),
            Collections.emptyList(),
            Optional.empty(),
            0,
            validators);
    return extraData.encode().toString();
  }

  // Accessors
  public BytesValue getVanityData() {
    return vanityData;
  }

  public Collection<Signature> getSeals() {
    return seals;
  }

  public Collection<Address> getValidators() {
    return validators;
  }

  public Optional<Vote> getVote() {
    return vote;
  }

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
