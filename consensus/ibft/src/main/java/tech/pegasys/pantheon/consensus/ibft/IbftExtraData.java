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
package tech.pegasys.pantheon.consensus.ibft;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;
import java.util.Optional;

/**
 * Represents the data structure stored in the extraData field of the BlockHeader used when
 * operating under an IBFT 2.0 consensus mechanism.
 */
public class IbftExtraData {

  public static final int EXTRA_VANITY_LENGTH = 32;

  private final BytesValue vanityData;
  private final List<Signature> seals;
  private final Optional<Vote> vote;
  private final int round;
  private final List<Address> validators;

  public IbftExtraData(
      final BytesValue vanityData,
      final List<Signature> seals,
      final Optional<Vote> vote,
      final int round,
      final List<Address> validators) {

    checkNotNull(vanityData);
    checkNotNull(seals);
    checkNotNull(validators);

    this.vanityData = vanityData;
    this.seals = seals;
    this.round = round;
    this.validators = validators;
    this.vote = vote;
  }

  public static IbftExtraData decode(final BytesValue input) {
    checkArgument(
        input.size() > EXTRA_VANITY_LENGTH,
        "Invalid BytesValue supplied - too short to produce a valid IBFT Extra Data object.");

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

  // Accessors
  public BytesValue getVanityData() {
    return vanityData;
  }

  public List<Signature> getSeals() {
    return seals;
  }

  public List<Address> getValidators() {
    return validators;
  }

  public Optional<Vote> getVote() {
    return vote;
  }

  public int getRound() {
    return round;
  }
}
