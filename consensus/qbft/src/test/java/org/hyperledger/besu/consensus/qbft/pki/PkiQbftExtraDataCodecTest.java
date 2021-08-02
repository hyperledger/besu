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

package org.hyperledger.besu.consensus.qbft.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.qbft.QbftExtraDataCodecTestUtils.createNonEmptyVanityData;

import org.hyperledger.besu.consensus.common.VoteType;
import org.hyperledger.besu.consensus.common.bft.Vote;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Address;
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

  private final String EMPTY_CMS_RAW_HEX_ENCODING_STRING =
      "0xf8f3f8f0a00102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20ea94000000000000"
          + "0000000000000000000000000001940000000000000000000000000000000000000002d794000000000000"
          + "000000000000000000000000000181ff83fedcbaf886b84100000000000000000000000000000000000000"
          + "00000000000000000000000001000000000000000000000000000000000000000000000000000000000000"
          + "000a00b841000000000000000000000000000000000000000000000000000000000000000a000000000000"
          + "000000000000000000000000000000000000000000000000000100c0";

  // Arbitrary bytes representing a non-empty CMS
  private final Bytes cms = Bytes.fromHexString("0x01");

  // Raw hex-encoded extra data with arbitrary CMS data (0x01)
  private final String RAW_HEX_ENCODING_STRING =
      "0xf8f4f8f0a00102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20ea94000000000000"
          + "0000000000000000000000000001940000000000000000000000000000000000000002d794000000000000"
          + "000000000000000000000000000181ff83fedcbaf886b84100000000000000000000000000000000000000"
          + "00000000000000000000000001000000000000000000000000000000000000000000000000000000000000"
          + "000a00b841000000000000000000000000000000000000000000000000000000000000000a000000000000"
          + "000000000000000000000000000000000000000000000000000100c101";

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
    encoder.startList(); // start envelope list

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
    encoder.endList(); // end extra data list

    encoder.startList(); // start cms list
    encoder.writeBytes(cms);
    encoder.endList(); // end cms list

    encoder.endList(); // end envelope list

    final Bytes bufferToInject = encoder.encoded();

    final PkiQbftExtraData extraData =
        (PkiQbftExtraData) bftExtraDataCodec.decodeRaw(bufferToInject);

    assertThat(extraData.getVanityData()).isEqualTo(vanity_data);
    assertThat(extraData.getVote())
        .isEqualTo(Optional.of(new Vote(Address.fromHexString("1"), VoteType.ADD)));
    assertThat(extraData.getRound()).isEqualTo(round);
    assertThat(extraData.getSeals()).isEqualTo(committerSeals);
    assertThat(extraData.getValidators()).isEqualTo(validators);
    assertThat(extraData.getCms()).hasValue(cms);
  }

  @Test
  public void encodingExtraDataWithEmptyCmsMatchesKnownRawHexString() {
    final Bytes expectedRawDecoding = Bytes.fromHexString(EMPTY_CMS_RAW_HEX_ENCODING_STRING);
    final Bytes encoded = bftExtraDataCodec.encode(getDecodedExtraDataWithEmptyCms());

    assertThat(encoded).isEqualTo(expectedRawDecoding);
  }

  @Test
  public void encodingMatchesKnownRawHexString() {
    final Bytes expectedRawDecoding = Bytes.fromHexString(RAW_HEX_ENCODING_STRING);
    final Bytes encoded = bftExtraDataCodec.encode(getDecodedExtraData(Optional.of(cms)));

    assertThat(encoded).isEqualTo(expectedRawDecoding);
  }

  private static PkiQbftExtraData getDecodedExtraDataWithEmptyCms() {
    return getDecodedExtraData(Optional.empty());
  }

  private static PkiQbftExtraData getDecodedExtraData(final Optional<Bytes> cms) {
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
