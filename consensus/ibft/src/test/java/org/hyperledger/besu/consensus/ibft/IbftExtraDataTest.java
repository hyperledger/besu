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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.consensus.common.VoteType;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import com.google.common.collect.Lists;
import org.junit.Test;

public class IbftExtraDataTest {

  private final String RAW_HEX_ENCODING_STRING =
      "f8f1a00102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20ea9400000000000000000000000000000000000"
          + "00001940000000000000000000000000000000000000002d794000000000000000000000000000000000000000181ff8400fedc"
          + "baf886b841000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000"
          + "0000000000000000000000000000000000a00b84100000000000000000000000000000000000000000000000000000000000000"
          + "0a000000000000000000000000000000000000000000000000000000000000000100";

  private final IbftExtraData DECODED_EXTRA_DATA_FOR_RAW_HEX_ENCODING_STRING =
      getDecodedExtraDataForRawHexEncodingString();

  private static IbftExtraData getDecodedExtraDataForRawHexEncodingString() {
    final List<Address> validators =
        Arrays.asList(Address.fromHexString("1"), Address.fromHexString("2"));
    final Optional<Vote> vote = Optional.of(Vote.authVote(Address.fromHexString("1")));
    final int round = 0x00FEDCBA;
    final List<Signature> committerSeals =
        Arrays.asList(
            Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 0),
            Signature.create(BigInteger.TEN, BigInteger.ONE, (byte) 0));

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = createNonEmptyVanityData();
    final BytesValue vanity_data = BytesValue.wrap(vanity_bytes);

    return new IbftExtraData(vanity_data, committerSeals, vote, round, validators);
  }

  @Test
  public void correctlyCodedRoundConstitutesValidContent() {
    final List<Address> validators = Lists.newArrayList();
    final Optional<Vote> vote = Optional.of(Vote.authVote(Address.fromHexString("1")));
    final int round = 0x00FEDCBA;
    final byte[] roundAsByteArray = new byte[] {(byte) 0x00, (byte) 0xFE, (byte) 0xDC, (byte) 0xBA};
    final List<Signature> committerSeals = Lists.newArrayList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final BytesValue vanity_data = BytesValue.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytesValue(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytesValue(validator));

    // encoded vote
    vote.get().writeTo(encoder);

    // This is to verify that the decoding works correctly when the round is encoded as 4 bytes
    encoder.writeBytesValue(BytesValue.wrap(roundAsByteArray));
    encoder.writeList(
        committerSeals, (committer, rlp) -> rlp.writeBytesValue(committer.encodedBytes()));
    encoder.endList();

    final BytesValue bufferToInject = encoder.encoded();

    final IbftExtraData extraData = IbftExtraData.decodeRaw(bufferToInject);

    assertThat(extraData.getVanityData()).isEqualTo(vanity_data);
    assertThat(extraData.getRound()).isEqualTo(round);
    assertThat(extraData.getSeals()).isEqualTo(committerSeals);
    assertThat(extraData.getValidators()).isEqualTo(validators);
  }

  /**
   * This test specifically verifies that {@link IbftExtraData#decode(BlockHeader)} uses {@link
   * RLPInput#readInt()} rather than {@link RLPInput#readIntScalar()} to decode the round number
   */
  @Test
  public void incorrectlyEncodedRoundThrowsRlpException() {
    final List<Address> validators = Lists.newArrayList();
    final Optional<Vote> vote = Optional.of(Vote.authVote(Address.fromHexString("1")));
    final byte[] roundAsByteArray = new byte[] {(byte) 0xFE, (byte) 0xDC, (byte) 0xBA};
    final List<Signature> committerSeals = Lists.newArrayList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final BytesValue vanity_data = BytesValue.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytesValue(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytesValue(validator));

    // encoded vote
    vote.get().writeTo(encoder);

    // This is to verify that the decoding throws an exception when the round number is not encoded
    // in 4 byte format
    encoder.writeBytesValue(BytesValue.wrap(roundAsByteArray));
    encoder.writeList(
        committerSeals, (committer, rlp) -> rlp.writeBytesValue(committer.encodedBytes()));
    encoder.endList();

    final BytesValue bufferToInject = encoder.encoded();

    assertThatThrownBy(() -> IbftExtraData.decodeRaw(bufferToInject))
        .isInstanceOf(RLPException.class);
  }

  @Test
  public void nullVoteAndListConstituteValidContent() {
    final List<Address> validators = Lists.newArrayList();
    final int round = 0x00FEDCBA;
    final List<Signature> committerSeals = Lists.newArrayList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final BytesValue vanity_data = BytesValue.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytesValue(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytesValue(validator));

    // encode an empty vote
    encoder.writeNull();

    encoder.writeInt(round);
    encoder.writeList(
        committerSeals, (committer, rlp) -> rlp.writeBytesValue(committer.encodedBytes()));
    encoder.endList();

    final BytesValue bufferToInject = encoder.encoded();

    final IbftExtraData extraData = IbftExtraData.decodeRaw(bufferToInject);

    assertThat(extraData.getVanityData()).isEqualTo(vanity_data);
    assertThat(extraData.getVote().isPresent()).isEqualTo(false);
    assertThat(extraData.getRound()).isEqualTo(round);
    assertThat(extraData.getSeals()).isEqualTo(committerSeals);
    assertThat(extraData.getValidators()).isEqualTo(validators);
  }

  @Test
  public void emptyVoteAndListIsEncodedCorrectly() {
    final List<Address> validators = Lists.newArrayList();
    final Optional<Vote> vote = Optional.empty();
    final int round = 0x00FEDCBA;
    final List<Signature> committerSeals = Lists.newArrayList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final BytesValue vanity_data = BytesValue.wrap(vanity_bytes);

    IbftExtraData expectedExtraData =
        new IbftExtraData(vanity_data, committerSeals, vote, round, validators);

    IbftExtraData actualExtraData = IbftExtraData.decodeRaw(expectedExtraData.encode());

    assertThat(actualExtraData).isEqualToComparingFieldByField(expectedExtraData);
  }

  @Test
  public void emptyListConstituteValidContent() {
    final List<Address> validators = Lists.newArrayList();
    final Optional<Vote> vote = Optional.of(Vote.dropVote(Address.fromHexString("1")));
    final int round = 0x00FEDCBA;
    final List<Signature> committerSeals = Lists.newArrayList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final BytesValue vanity_data = BytesValue.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytesValue(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytesValue(validator));

    // encoded vote
    vote.get().writeTo(encoder);

    encoder.writeInt(round);
    encoder.writeList(
        committerSeals, (committer, rlp) -> rlp.writeBytesValue(committer.encodedBytes()));
    encoder.endList();

    final BytesValue bufferToInject = encoder.encoded();

    final IbftExtraData extraData = IbftExtraData.decodeRaw(bufferToInject);

    assertThat(extraData.getVanityData()).isEqualTo(vanity_data);
    assertThat(extraData.getRound()).isEqualTo(round);
    assertThat(extraData.getSeals()).isEqualTo(committerSeals);
    assertThat(extraData.getValidators()).isEqualTo(validators);
  }

  @Test
  public void emptyListsAreEncodedAndDecodedCorrectly() {
    final List<Address> validators = Lists.newArrayList();
    final Optional<Vote> vote = Optional.of(Vote.authVote(Address.fromHexString("1")));
    final int round = 0x00FEDCBA;
    final List<Signature> committerSeals = Lists.newArrayList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final BytesValue vanity_data = BytesValue.wrap(vanity_bytes);

    IbftExtraData expectedExtraData =
        new IbftExtraData(vanity_data, committerSeals, vote, round, validators);

    IbftExtraData actualExtraData = IbftExtraData.decodeRaw(expectedExtraData.encode());

    assertThat(actualExtraData).isEqualToComparingFieldByField(expectedExtraData);
  }

  @Test
  public void fullyPopulatedDataProducesCorrectlyFormedExtraDataObject() {
    final List<Address> validators =
        Arrays.asList(Address.fromHexString("1"), Address.fromHexString("2"));
    final int round = 0x00FEDCBA;
    final List<Signature> committerSeals =
        Arrays.asList(
            Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 0),
            Signature.create(BigInteger.TEN, BigInteger.ONE, (byte) 0));

    // Create randomised vanity data.
    final byte[] vanity_bytes = createNonEmptyVanityData();
    new Random().nextBytes(vanity_bytes);
    final BytesValue vanity_data = BytesValue.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList(); // This is required to create a "root node" for all RLP'd data
    encoder.writeBytesValue(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytesValue(validator));

    // encoded vote
    encoder.startList();
    encoder.writeBytesValue(Address.fromHexString("1"));
    encoder.writeByte(Vote.ADD_BYTE_VALUE);
    encoder.endList();

    encoder.writeInt(round);
    encoder.writeList(
        committerSeals, (committer, rlp) -> rlp.writeBytesValue(committer.encodedBytes()));
    encoder.endList();

    final BytesValue bufferToInject = encoder.encoded();

    final IbftExtraData extraData = IbftExtraData.decodeRaw(bufferToInject);

    assertThat(extraData.getVanityData()).isEqualTo(vanity_data);
    assertThat(extraData.getVote())
        .isEqualTo(Optional.of(new Vote(Address.fromHexString("1"), VoteType.ADD)));
    assertThat(extraData.getRound()).isEqualTo(round);
    assertThat(extraData.getSeals()).isEqualTo(committerSeals);
    assertThat(extraData.getValidators()).isEqualTo(validators);
  }

  @Test
  public void fullyPopulatedDataIsEncodedAndDecodedCorrectly() {
    final List<Address> validators =
        Arrays.asList(Address.fromHexString("1"), Address.fromHexString("2"));
    final Optional<Vote> vote = Optional.of(Vote.authVote(Address.fromHexString("1")));
    final int round = 0x00FEDCBA;
    final List<Signature> committerSeals =
        Arrays.asList(
            Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 0),
            Signature.create(BigInteger.TEN, BigInteger.ONE, (byte) 0));

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = createNonEmptyVanityData();
    final BytesValue vanity_data = BytesValue.wrap(vanity_bytes);

    IbftExtraData expectedExtraData =
        new IbftExtraData(vanity_data, committerSeals, vote, round, validators);

    IbftExtraData actualExtraData = IbftExtraData.decodeRaw(expectedExtraData.encode());

    assertThat(actualExtraData).isEqualToComparingFieldByField(expectedExtraData);
  }

  @Test
  public void encodingMatchesKnownRawHexString() {
    final BytesValue expectedRawDecoding = BytesValue.fromHexString(RAW_HEX_ENCODING_STRING);
    assertThat(DECODED_EXTRA_DATA_FOR_RAW_HEX_ENCODING_STRING.encode())
        .isEqualTo(expectedRawDecoding);
  }

  @Test
  public void decodingOfKnownRawHexStringMatchesKnowExtraDataObject() {

    final IbftExtraData expectedExtraData = DECODED_EXTRA_DATA_FOR_RAW_HEX_ENCODING_STRING;

    BytesValue rawDecoding = BytesValue.fromHexString(RAW_HEX_ENCODING_STRING);
    IbftExtraData actualExtraData = IbftExtraData.decodeRaw(rawDecoding);

    assertThat(actualExtraData).isEqualToComparingFieldByField(expectedExtraData);
  }

  @Test
  public void extraDataCanBeEncodedWithoutCommitSeals() {
    final List<Address> validators =
        Arrays.asList(Address.fromHexString("1"), Address.fromHexString("2"));
    final Optional<Vote> vote = Optional.of(Vote.authVote(Address.fromHexString("1")));
    final int round = 0x00FEDCBA;
    final List<Signature> committerSeals =
        Arrays.asList(
            Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 0),
            Signature.create(BigInteger.TEN, BigInteger.ONE, (byte) 0));

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = createNonEmptyVanityData();
    final BytesValue vanity_data = BytesValue.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytesValue(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytesValue(validator));

    // encoded vote
    vote.get().writeTo(encoder);

    encoder.writeInt(round);
    encoder.endList();

    BytesValue expectedEncoding = encoder.encoded();

    BytesValue actualEncoding =
        new IbftExtraData(vanity_data, committerSeals, vote, round, validators)
            .encodeWithoutCommitSeals();

    assertThat(actualEncoding).isEqualTo(expectedEncoding);
  }

  @Test
  public void extraDataCanBeEncodedwithoutCommitSealsOrRoundNumber() {
    final List<Address> validators =
        Arrays.asList(Address.fromHexString("1"), Address.fromHexString("2"));
    final Optional<Vote> vote = Optional.of(Vote.authVote(Address.fromHexString("1")));
    final int round = 0x00FEDCBA;
    final List<Signature> committerSeals =
        Arrays.asList(
            Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 0),
            Signature.create(BigInteger.TEN, BigInteger.ONE, (byte) 0));

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = createNonEmptyVanityData();
    final BytesValue vanity_data = BytesValue.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytesValue(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytesValue(validator));

    // encoded vote
    vote.get().writeTo(encoder);

    encoder.endList();

    BytesValue expectedEncoding = encoder.encoded();

    BytesValue actualEncoding =
        new IbftExtraData(vanity_data, committerSeals, vote, round, validators)
            .encodeWithoutCommitSealsAndRoundNumber();

    assertThat(actualEncoding).isEqualTo(expectedEncoding);
  }

  @Test
  public void incorrectlyStructuredRlpThrowsException() {
    final List<Address> validators = Lists.newArrayList();
    final Optional<Vote> vote = Optional.of(Vote.authVote(Address.fromHexString("1")));
    final int round = 0x00FEDCBA;
    final List<Signature> committerSeals = Lists.newArrayList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final BytesValue vanity_data = BytesValue.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytesValue(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytesValue(validator));

    // encoded vote
    vote.get().writeTo(encoder);

    encoder.writeInt(round);
    encoder.writeList(
        committerSeals, (committer, rlp) -> rlp.writeBytesValue(committer.encodedBytes()));
    encoder.writeLong(1);
    encoder.endList();

    final BytesValue bufferToInject = encoder.encoded();

    assertThatThrownBy(() -> IbftExtraData.decodeRaw(bufferToInject))
        .isInstanceOf(RLPException.class);
  }

  @Test
  public void incorrectVoteTypeThrowsException() {
    final List<Address> validators =
        Arrays.asList(Address.fromHexString("1"), Address.fromHexString("2"));
    final Address voteRecipient = Address.fromHexString("1");
    final byte voteType = (byte) 0xAA;
    final int round = 0x00FEDCBA;
    final List<Signature> committerSeals =
        Arrays.asList(
            Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 0),
            Signature.create(BigInteger.TEN, BigInteger.ONE, (byte) 0));

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final BytesValue vanity_data = BytesValue.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytesValue(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytesValue(validator));

    // encode vote
    encoder.startList();
    encoder.writeBytesValue(voteRecipient);
    encoder.writeByte(voteType);
    encoder.endList();

    encoder.writeInt(round);
    encoder.writeList(
        committerSeals, (committer, rlp) -> rlp.writeBytesValue(committer.encodedBytes()));
    encoder.endList();

    final BytesValue bufferToInject = encoder.encoded();

    assertThatThrownBy(() -> IbftExtraData.decodeRaw(bufferToInject))
        .isInstanceOf(RLPException.class);
  }

  private static byte[] createNonEmptyVanityData() {
    final byte[] vanity_bytes = new byte[32];
    for (int i = 0; i < vanity_bytes.length; i++) {
      vanity_bytes[i] = (byte) (i + 1);
    }
    return vanity_bytes;
  }
}
