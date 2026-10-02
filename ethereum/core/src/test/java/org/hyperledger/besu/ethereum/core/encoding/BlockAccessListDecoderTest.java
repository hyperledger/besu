/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.core.encoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** EIP-7928 encodes every uint256 field of a block access list as a minimal RLP scalar. */
class BlockAccessListDecoderTest {

  private static final Address ADDRESS =
      Address.fromHexString("0x00000000000000000000000000000000000000aa");
  private static final UInt256 SLOT = UInt256.ONE;
  private static final UInt256 VALUE = UInt256.valueOf(2);
  private static final UInt256 READ = UInt256.valueOf(3);
  private static final UInt256 BALANCE = UInt256.valueOf(4);

  /** The same scalar as {@code writeUInt256Scalar} emits, plus a redundant leading zero byte. */
  private static void writeNonMinimal(final RLPOutput out, final UInt256 value) {
    out.writeBytes(Bytes.concatenate(Bytes.of(0), value.trimLeadingZeros()));
  }

  private static Bytes encodeBlockAccessList(final String nonMinimalField) {
    return RLP.encode(
        out -> {
          out.startList();
          out.startList();
          out.writeBytes(ADDRESS.getBytes());

          out.startList(); // storage changes
          out.startList();
          if ("storage_slot".equals(nonMinimalField)) {
            writeNonMinimal(out, SLOT);
          } else {
            out.writeUInt256Scalar(SLOT);
          }
          out.startList();
          out.startList();
          out.writeUnsignedInt(1);
          if ("storage_value".equals(nonMinimalField)) {
            writeNonMinimal(out, VALUE);
          } else {
            out.writeUInt256Scalar(VALUE);
          }
          out.endList();
          out.endList();
          out.endList();
          out.endList();

          out.startList(); // storage reads
          if ("storage_read".equals(nonMinimalField)) {
            writeNonMinimal(out, READ);
          } else if ("storage_read_long_form".equals(nonMinimalField)) {
            // A single byte below 0x80 must be encoded as itself, not as a one-byte string.
            out.writeRaw(Bytes.of(0x81, READ.toBytes().get(31)));
          } else {
            out.writeUInt256Scalar(READ);
          }
          out.endList();

          out.startList(); // balance changes
          out.startList();
          out.writeUnsignedInt(1);
          if ("balance".equals(nonMinimalField)) {
            writeNonMinimal(out, BALANCE);
          } else {
            out.writeUInt256Scalar(BALANCE);
          }
          out.endList();
          out.endList();

          out.startList(); // nonce changes
          out.startList();
          out.writeUnsignedInt(1);
          out.writeLongScalar(7);
          out.endList();
          out.endList();
          out.startList(); // code changes
          out.startList();
          out.writeUnsignedInt(1);
          out.writeBytes(Bytes.fromHexString("0x6001600055"));
          out.endList();
          out.endList();

          out.endList();
          out.endList();
        });
  }

  private static BlockAccessList decode(final Bytes encoded) {
    return BlockAccessListDecoder.decode(new BytesValueRLPInput(encoded, false));
  }

  @Test
  void decodesMinimallyEncodedScalars() {
    final BlockAccessList bal = decode(encodeBlockAccessList(null));

    assertThat(bal.accountChanges()).hasSize(1);
    final BlockAccessList.AccountChanges account = bal.accountChanges().getFirst();
    assertThat(account.address()).isEqualTo(ADDRESS);
    assertThat(account.storageChanges().getFirst().slot().getSlotKey()).contains(SLOT);
    assertThat(account.storageChanges().getFirst().changes().getFirst().newValue())
        .isEqualTo(VALUE);
    assertThat(account.storageReads().getFirst().slot().getSlotKey()).contains(READ);
    assertThat(account.balanceChanges().getFirst().postBalance().toUInt256()).isEqualTo(BALANCE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"storage_slot", "storage_value", "storage_read", "balance"})
  void rejectsNonMinimallyEncodedScalars(final String field) {
    assertThatThrownBy(() -> decode(encodeBlockAccessList(field)))
        .isInstanceOf(RLPException.class)
        .hasMessageContaining("leading zero");
  }

  @Test
  void canonicalEncodingRoundTripsByteForByte() {
    final Bytes encoded = encodeBlockAccessList(null);
    final BlockAccessList bal = decode(encoded);

    assertThat(RLP.encode(bal::writeTo)).isEqualTo(encoded);
  }

  @Test
  void balHashOfDecodedListMatchesBalHashOfEncodedBytes() {
    // BodyValidation exposes two balHash definitions: one hashes the canonical re-encoding, the
    // other hashes bytes as received. They must agree for every input the decoder accepts.
    final Bytes encoded = encodeBlockAccessList(null);

    assertThat(BodyValidation.balHash(decode(encoded)))
        .isEqualTo(BodyValidation.balHashOfEncoded(encoded));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "storage_slot",
        "storage_value",
        "storage_read",
        "storage_read_long_form",
        "balance"
      })
  void nonCanonicalEncodingNeverYieldsABlockAccessList(final String field) {
    // If any of these decoded, balHash(decode(x)) would be the canonical hash while
    // balHashOfEncoded(x) would not, so the result would depend on which overload a caller used.
    final Bytes nonCanonical = encodeBlockAccessList(field);

    assertThat(BodyValidation.balHashOfEncoded(nonCanonical))
        .isNotEqualTo(BodyValidation.balHashOfEncoded(encodeBlockAccessList(null)));
    assertThatThrownBy(() -> decode(nonCanonical)).isInstanceOf(RLPException.class);
  }
}
