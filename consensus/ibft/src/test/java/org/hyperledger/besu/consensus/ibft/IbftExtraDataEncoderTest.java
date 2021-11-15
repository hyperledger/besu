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
package org.hyperledger.besu.consensus.ibft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.Vote;
import org.hyperledger.besu.consensus.common.validator.VoteType;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class IbftExtraDataEncoderTest {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  private final String RAW_HEX_ENCODING_STRING =
      "f8f1a00102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20ea9400000000000000000000000000000000000"
          + "00001940000000000000000000000000000000000000002d794000000000000000000000000000000000000000181ff8400fedc"
          + "baf886b841000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000"
          + "0000000000000000000000000000000000a00b84100000000000000000000000000000000000000000000000000000000000000"
          + "0a000000000000000000000000000000000000000000000000000000000000000100";

  private final BftExtraData DECODED_EXTRA_DATA_FOR_RAW_HEX_ENCODING_STRING =
      getDecodedExtraDataForRawHexEncodingString();
  private final IbftExtraDataCodec bftExtraDataEncoder = new IbftExtraDataCodec();

  private static BftExtraData getDecodedExtraDataForRawHexEncodingString() {
    final List<Address> validators =
        Arrays.asList(Address.fromHexString("1"), Address.fromHexString("2"));
    final Optional<Vote> vote = Optional.of(Vote.authVote(Address.fromHexString("1")));
    final int round = 0x00FEDCBA;
    final List<SECPSignature> committerSeals =
        Arrays.asList(
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.ONE, BigInteger.TEN, (byte) 0),
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.TEN, BigInteger.ONE, (byte) 0));

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = createNonEmptyVanityData();
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);

    return new BftExtraData(vanity_data, committerSeals, vote, round, validators);
  }

  @Test
  public void correctlyCodedRoundConstitutesValidContent() {
    final List<Address> validators = Lists.newArrayList();
    final int round = 0x00FEDCBA;
    final byte[] roundAsByteArray = new byte[] {(byte) 0x00, (byte) 0xFE, (byte) 0xDC, (byte) 0xBA};
    final List<SECPSignature> committerSeals = Lists.newArrayList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytes(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));

    // encoded vote
    encoder.startList();
    encoder.writeBytes(Address.fromHexString("1"));
    encoder.writeByte(Vote.ADD_BYTE_VALUE);
    encoder.endList();

    // This is to verify that the decoding works correctly when the round is encoded as 4 bytes
    encoder.writeBytes(Bytes.wrap(roundAsByteArray));
    encoder.writeList(committerSeals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    encoder.endList();

    final Bytes bufferToInject = encoder.encoded();

    final BftExtraData extraData = bftExtraDataEncoder.decodeRaw(bufferToInject);

    assertThat(extraData.getVanityData()).isEqualTo(vanity_data);
    assertThat(extraData.getRound()).isEqualTo(round);
    assertThat(extraData.getSeals()).isEqualTo(committerSeals);
    assertThat(extraData.getValidators()).isEqualTo(validators);
  }

  /**
   * This test specifically verifies that {@link IbftExtraDataCodec#decode(BlockHeader)} uses {@link
   * RLPInput#readInt()} rather than {@link RLPInput#readIntScalar()} to decode the round number
   */
  @Test
  public void incorrectlyEncodedRoundThrowsRlpException() {
    final List<Address> validators = Lists.newArrayList();
    final byte[] roundAsByteArray = new byte[] {(byte) 0xFE, (byte) 0xDC, (byte) 0xBA};
    final List<SECPSignature> committerSeals = Lists.newArrayList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytes(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));

    // encoded vote
    encoder.startList();
    encoder.writeBytes(Address.fromHexString("1"));
    encoder.writeByte(Vote.ADD_BYTE_VALUE);
    encoder.endList();

    // This is to verify that the decoding throws an exception when the round number is not encoded
    // in 4 byte format
    encoder.writeBytes(Bytes.wrap(roundAsByteArray));
    encoder.writeList(committerSeals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    encoder.endList();

    final Bytes bufferToInject = encoder.encoded();

    assertThatThrownBy(() -> bftExtraDataEncoder.decodeRaw(bufferToInject))
        .isInstanceOf(RLPException.class);
  }

  @Test
  public void nullVoteAndListConstituteValidContent() {
    final List<Address> validators = Lists.newArrayList();
    final int round = 0x00FEDCBA;
    final List<SECPSignature> committerSeals = Lists.newArrayList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytes(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));

    // encode an empty vote
    encoder.writeNull();

    encoder.writeInt(round);
    encoder.writeList(committerSeals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    encoder.endList();

    final Bytes bufferToInject = encoder.encoded();

    final BftExtraData extraData = bftExtraDataEncoder.decodeRaw(bufferToInject);

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
    final List<SECPSignature> committerSeals = Lists.newArrayList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);

    BftExtraData expectedExtraData =
        new BftExtraData(vanity_data, committerSeals, vote, round, validators);

    final IbftExtraDataCodec ibftExtraDataEncoder = bftExtraDataEncoder;
    BftExtraData actualExtraData =
        ibftExtraDataEncoder.decodeRaw(ibftExtraDataEncoder.encode(expectedExtraData));

    assertThat(actualExtraData).isEqualToComparingFieldByField(expectedExtraData);
  }

  @Test
  public void emptyListConstituteValidContent() {
    final List<Address> validators = Lists.newArrayList();
    final int round = 0x00FEDCBA;
    final List<SECPSignature> committerSeals = Lists.newArrayList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytes(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));

    // encoded vote
    encoder.startList();
    encoder.writeBytes(Address.fromHexString("1"));
    encoder.writeByte(Vote.DROP_BYTE_VALUE);
    encoder.endList();

    encoder.writeInt(round);
    encoder.writeList(committerSeals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    encoder.endList();

    final Bytes bufferToInject = encoder.encoded();

    final BftExtraData extraData = bftExtraDataEncoder.decodeRaw(bufferToInject);

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
    final List<SECPSignature> committerSeals = Lists.newArrayList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);

    BftExtraData expectedExtraData =
        new BftExtraData(vanity_data, committerSeals, vote, round, validators);

    final IbftExtraDataCodec ibftExtraDataEncoder = bftExtraDataEncoder;
    BftExtraData actualExtraData =
        ibftExtraDataEncoder.decodeRaw(ibftExtraDataEncoder.encode(expectedExtraData));

    assertThat(actualExtraData).isEqualToComparingFieldByField(expectedExtraData);
  }

  @Test
  public void fullyPopulatedDataProducesCorrectlyFormedExtraDataObject() {
    final List<Address> validators =
        Arrays.asList(Address.fromHexString("1"), Address.fromHexString("2"));
    final int round = 0x00FEDCBA;
    final List<SECPSignature> committerSeals =
        Arrays.asList(
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.ONE, BigInteger.TEN, (byte) 0),
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.TEN, BigInteger.ONE, (byte) 0));

    // Create randomised vanity data.
    final byte[] vanity_bytes = createNonEmptyVanityData();
    new Random().nextBytes(vanity_bytes);
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList(); // This is required to create a "root node" for all RLP'd data
    encoder.writeBytes(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));

    // encoded vote
    encoder.startList();
    encoder.writeBytes(Address.fromHexString("1"));
    encoder.writeByte(Vote.ADD_BYTE_VALUE);
    encoder.endList();

    encoder.writeInt(round);
    encoder.writeList(committerSeals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    encoder.endList();

    final Bytes bufferToInject = encoder.encoded();

    final BftExtraData extraData = bftExtraDataEncoder.decodeRaw(bufferToInject);

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
    final List<SECPSignature> committerSeals =
        Arrays.asList(
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.ONE, BigInteger.TEN, (byte) 0),
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.TEN, BigInteger.ONE, (byte) 0));

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = createNonEmptyVanityData();
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);

    BftExtraData expectedExtraData =
        new BftExtraData(vanity_data, committerSeals, vote, round, validators);
    final IbftExtraDataCodec ibftExtraDataEncoder = bftExtraDataEncoder;

    final BftExtraData actualExtraData =
        ibftExtraDataEncoder.decodeRaw(ibftExtraDataEncoder.encode(expectedExtraData));

    assertThat(actualExtraData).isEqualToComparingFieldByField(expectedExtraData);
  }

  @Test
  public void encodingMatchesKnownRawHexString() {
    final Bytes expectedRawDecoding = Bytes.fromHexString(RAW_HEX_ENCODING_STRING);
    assertThat(bftExtraDataEncoder.encode(DECODED_EXTRA_DATA_FOR_RAW_HEX_ENCODING_STRING))
        .isEqualTo(expectedRawDecoding);
  }

  @Test
  public void decodingOfKnownRawHexStringMatchesKnowExtraDataObject() {

    final BftExtraData expectedExtraData = DECODED_EXTRA_DATA_FOR_RAW_HEX_ENCODING_STRING;

    Bytes rawDecoding = Bytes.fromHexString(RAW_HEX_ENCODING_STRING);
    BftExtraData actualExtraData = bftExtraDataEncoder.decodeRaw(rawDecoding);

    assertThat(actualExtraData).isEqualToComparingFieldByField(expectedExtraData);
  }

  @Test
  public void extraDataCanBeEncodedWithoutCommitSeals() {
    final List<Address> validators =
        Arrays.asList(Address.fromHexString("1"), Address.fromHexString("2"));
    final Optional<Vote> vote = Optional.of(Vote.authVote(Address.fromHexString("1")));
    final int round = 0x00FEDCBA;
    final List<SECPSignature> committerSeals =
        Arrays.asList(
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.ONE, BigInteger.TEN, (byte) 0),
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.TEN, BigInteger.ONE, (byte) 0));

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = createNonEmptyVanityData();
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytes(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));

    // encoded vote
    encoder.startList();
    encoder.writeBytes(vote.get().getRecipient());
    encoder.writeByte(Vote.ADD_BYTE_VALUE);
    encoder.endList();

    encoder.writeInt(round);
    encoder.endList();

    Bytes expectedEncoding = encoder.encoded();

    Bytes actualEncoding =
        bftExtraDataEncoder.encodeWithoutCommitSeals(
            new BftExtraData(vanity_data, committerSeals, vote, round, validators));

    assertThat(actualEncoding).isEqualTo(expectedEncoding);
  }

  @Test
  public void extraDataCanBeEncodedwithoutCommitSealsOrRoundNumber() {
    final List<Address> validators =
        Arrays.asList(Address.fromHexString("1"), Address.fromHexString("2"));
    final Optional<Vote> vote = Optional.of(Vote.authVote(Address.fromHexString("1")));
    final int round = 0x00FEDCBA;
    final List<SECPSignature> committerSeals =
        Arrays.asList(
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.ONE, BigInteger.TEN, (byte) 0),
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.TEN, BigInteger.ONE, (byte) 0));

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = createNonEmptyVanityData();
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytes(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));

    // encoded vote
    encoder.startList();
    encoder.writeBytes(vote.get().getRecipient());
    encoder.writeByte(Vote.ADD_BYTE_VALUE);
    encoder.endList();

    encoder.endList();

    Bytes expectedEncoding = encoder.encoded();

    Bytes actualEncoding =
        bftExtraDataEncoder.encodeWithoutCommitSealsAndRoundNumber(
            new BftExtraData(vanity_data, committerSeals, vote, round, validators));

    assertThat(actualEncoding).isEqualTo(expectedEncoding);
  }

  @Test
  public void incorrectlyStructuredRlpThrowsException() {
    final List<Address> validators = Lists.newArrayList();
    final int round = 0x00FEDCBA;
    final List<SECPSignature> committerSeals = Lists.newArrayList();

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytes(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));

    // encoded vote
    encoder.startList();
    encoder.writeBytes(Address.fromHexString("1"));
    encoder.writeByte(Vote.ADD_BYTE_VALUE);
    encoder.endList();

    encoder.writeInt(round);
    encoder.writeList(committerSeals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    encoder.writeLong(1);
    encoder.endList();

    final Bytes bufferToInject = encoder.encoded();

    assertThatThrownBy(() -> bftExtraDataEncoder.decodeRaw(bufferToInject))
        .isInstanceOf(RLPException.class);
  }

  @Test
  public void incorrectVoteTypeThrowsException() {
    final List<Address> validators =
        Arrays.asList(Address.fromHexString("1"), Address.fromHexString("2"));
    final Address voteRecipient = Address.fromHexString("1");
    final byte voteType = (byte) 0xAA;
    final int round = 0x00FEDCBA;
    final List<SECPSignature> committerSeals =
        Arrays.asList(
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.ONE, BigInteger.TEN, (byte) 0),
            SIGNATURE_ALGORITHM.get().createSignature(BigInteger.TEN, BigInteger.ONE, (byte) 0));

    // Create a byte buffer with no data.
    final byte[] vanity_bytes = new byte[32];
    final Bytes vanity_data = Bytes.wrap(vanity_bytes);

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytes(vanity_data);
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));

    // encode vote
    encoder.startList();
    encoder.writeBytes(voteRecipient);
    encoder.writeByte(voteType);
    encoder.endList();

    encoder.writeInt(round);
    encoder.writeList(committerSeals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    encoder.endList();

    final Bytes bufferToInject = encoder.encoded();

    assertThatThrownBy(() -> bftExtraDataEncoder.decodeRaw(bufferToInject))
        .isInstanceOf(RLPException.class);
  }

  @Test
  public void emptyExtraDataThrowsException() {
    final Bytes bufferToInject = Bytes.EMPTY;

    assertThatThrownBy(() -> bftExtraDataEncoder.decodeRaw(bufferToInject))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid Bytes supplied - Bft Extra Data required.");
  }

  private static byte[] createNonEmptyVanityData() {
    final byte[] vanity_bytes = new byte[32];
    for (int i = 0; i < vanity_bytes.length; i++) {
      vanity_bytes[i] = (byte) (i + 1);
    }
    return vanity_bytes;
  }
}
