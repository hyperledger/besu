/*
 * Copyright 2020 ConsenSys AG.
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

package org.hyperledger.besu.consensus.qbft.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.qbft.QbftExtraDataCodecTestUtils.createNonEmptyVanityData;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.Vote;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class PkiQbftExtraDataCodecTest {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  // Arbitrary bytes representing a non-empty CMS
  private final Bytes cms = Bytes.fromHexString("0x01");

  private final String RAW_EXCLUDE_COMMIT_SEALS_AND_ROUND_NUMBER_ENCODED_STRING =
      "0xf867a00102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20ea940000000000000000"
          + "000000000000000000000001940000000000000000000000000000000000000002d7940000000000000000"
          + "00000000000000000000000181ff80c001";

  private final String RAW_EXCLUDE_COMMIT_SEALS_ENCODED_STRING =
      "0xf86aa00102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20ea940000000000000000"
          + "000000000000000000000001940000000000000000000000000000000000000002d7940000000000000000"
          + "00000000000000000000000181ff83fedcbac001";

  private final String RAW_ALL_ENCODED_STRING =
      "0xf8f1a00102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20ea940000000000000000"
          + "000000000000000000000001940000000000000000000000000000000000000002d7940000000000000000"
          + "00000000000000000000000181ff83fedcbaf886b841000000000000000000000000000000000000000000"
          + "0000000000000000000001000000000000000000000000000000000000000000000000000000000000000a"
          + "00b841000000000000000000000000000000000000000000000000000000000000000a0000000000000000"
          + "0000000000000000000000000000000000000000000000010001";

  private final String RAW_QBFT_EXTRA_DATA =
      "0xf8f0a00102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20ea940000000000000000"
          + "000000000000000000000001940000000000000000000000000000000000000002d7940000000000000000"
          + "00000000000000000000000181ff83fedcbaf886b841000000000000000000000000000000000000000000"
          + "0000000000000000000001000000000000000000000000000000000000000000000000000000000000000a"
          + "00b841000000000000000000000000000000000000000000000000000000000000000a0000000000000000"
          + "00000000000000000000000000000000000000000000000100";

  private final PkiQbftExtraDataCodec bftExtraDataCodec = new PkiQbftExtraDataCodec();

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
    encoder.startList(); // start extra data list
    // vanity data
    encoder.writeBytes(vanity_data);
    // validators
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));
    // votes
    encoder.startList();
    encoder.writeBytes(Address.fromHexString("1"));
    encoder.writeByte(Vote.ADD_BYTE_VALUE);
    encoder.endList();
    // rounds
    encoder.writeIntScalar(round);
    // committer seals
    encoder.writeList(committerSeals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    // cms
    encoder.writeBytes(cms);
    encoder.endList(); // end extra data list

    final Bytes bufferToInject = encoder.encoded();

    final PkiQbftExtraData extraData =
        (PkiQbftExtraData) bftExtraDataCodec.decodeRaw(bufferToInject);

    assertThat(extraData.getVanityData()).isEqualTo(vanity_data);
    assertThat(extraData.getRound()).isEqualTo(round);
    assertThat(extraData.getSeals()).isEqualTo(committerSeals);
    assertThat(extraData.getValidators()).isEqualTo(validators);
    assertThat(extraData.getCms()).isEqualTo(cms);
  }

  @Test
  public void decodingQbftExtraDataDelegatesToQbftCodec() {
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
    encoder.startList(); // start extra data list
    // vanity data
    encoder.writeBytes(vanity_data);
    // validators
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));
    // votes
    encoder.startList();
    encoder.writeBytes(Address.fromHexString("1"));
    encoder.writeByte(Vote.ADD_BYTE_VALUE);
    encoder.endList();
    // rounds
    encoder.writeIntScalar(round);
    // committer seals
    encoder.writeList(committerSeals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    // Not including the CMS in the list (to generate a non-pki QBFT extra data)
    encoder.endList(); // end extra data list

    final Bytes bufferToInject = encoder.encoded();

    final PkiQbftExtraData extraData =
        (PkiQbftExtraData) bftExtraDataCodec.decodeRaw(bufferToInject);

    assertThat(extraData.getVanityData()).isEqualTo(vanity_data);
    assertThat(extraData.getRound()).isEqualTo(round);
    assertThat(extraData.getSeals()).isEqualTo(committerSeals);
    assertThat(extraData.getValidators()).isEqualTo(validators);
    assertThat(extraData.getCms()).isEqualTo(Bytes.EMPTY);
  }

  /*
   When encoding for blockchain, we ignore commit seals and round number, but we include the CMS
  */
  @Test
  public void encodingForBlockchainShouldIncludeCms() {
    final Bytes expectedRawDecoding =
        Bytes.fromHexString(RAW_EXCLUDE_COMMIT_SEALS_AND_ROUND_NUMBER_ENCODED_STRING);
    final Bytes encoded =
        bftExtraDataCodec.encodeWithoutCommitSealsAndRoundNumber(getDecodedExtraData(cms));

    assertThat(encoded).isEqualTo(expectedRawDecoding);
  }

  @Test
  public void encodingWithoutCommitSealsShouldIncludeCms() {
    final Bytes expectedRawDecoding = Bytes.fromHexString(RAW_EXCLUDE_COMMIT_SEALS_ENCODED_STRING);
    final Bytes encoded = bftExtraDataCodec.encodeWithoutCommitSeals(getDecodedExtraData(cms));

    assertThat(encoded).isEqualTo(expectedRawDecoding);
  }

  @Test
  public void encodingWithAllShouldIncludeCms() {
    final Bytes expectedRawDecoding = Bytes.fromHexString(RAW_ALL_ENCODED_STRING);
    final Bytes encoded = bftExtraDataCodec.encode(getDecodedExtraData(cms));

    assertThat(encoded).isEqualTo(expectedRawDecoding);
  }

  /*
   When encoding for proposal, we include commit seals and round number, but we ignore the CMS
  */
  @Test
  public void encodingForCreatingCmsProposal() {
    final Bytes expectedRawDecoding = Bytes.fromHexString(RAW_QBFT_EXTRA_DATA);
    final Bytes encoded = bftExtraDataCodec.encodeWithoutCms(getDecodedExtraData(cms));

    assertThat(encoded).isEqualTo(expectedRawDecoding);
  }

  /*
   When encoding non-pki extra data, we delegate to the regular QBFT encoder
  */
  @Test
  public void encodingQbftExtraData() {
    final Bytes expectedRawDecoding = Bytes.fromHexString(RAW_QBFT_EXTRA_DATA);
    final PkiQbftExtraData pkiBftExtraData = getDecodedExtraData(cms);
    final BftExtraData bftExtraData =
        new BftExtraData(
            pkiBftExtraData.getVanityData(),
            pkiBftExtraData.getSeals(),
            pkiBftExtraData.getVote(),
            pkiBftExtraData.getRound(),
            pkiBftExtraData.getValidators());

    final Bytes encoded = bftExtraDataCodec.encode(bftExtraData);

    assertThat(encoded).isEqualTo(expectedRawDecoding);
  }

  private static PkiQbftExtraData getDecodedExtraData(final Bytes cms) {
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

    return new PkiQbftExtraData(vanity_data, committerSeals, vote, round, validators, cms);
  }
}
