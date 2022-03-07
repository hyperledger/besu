/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.datatypes;

import static com.google.common.base.Preconditions.checkArgument;
import static org.hyperledger.besu.crypto.Hash.keccak256;

import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.DelegatingBytes;

/** A 160-bits account address. */
public class Address extends DelegatingBytes implements org.hyperledger.besu.plugin.data.Address {

  public static final int SIZE = 20;

  /** Specific addresses of the "precompiled" contracts. */
  public static final Address ECREC = Address.precompiled(0x01);

  public static final Address SHA256 = Address.precompiled(0x02);
  public static final Address RIPEMD160 = Address.precompiled(0x03);
  public static final Address ID = Address.precompiled(0x04);
  public static final Address MODEXP = Address.precompiled(0x05);
  public static final Address ALTBN128_ADD = Address.precompiled(0x06);
  public static final Address ALTBN128_MUL = Address.precompiled(0x07);
  public static final Address ALTBN128_PAIRING = Address.precompiled(0x08);
  public static final Address BLAKE2B_F_COMPRESSION = Address.precompiled(0x09);
  public static final Address BLS12_G1ADD = Address.precompiled(0xA);
  public static final Address BLS12_G1MUL = Address.precompiled(0xB);
  public static final Address BLS12_G1MULTIEXP = Address.precompiled(0xC);
  public static final Address BLS12_G2ADD = Address.precompiled(0xD);
  public static final Address BLS12_G2MUL = Address.precompiled(0xE);
  public static final Address BLS12_G2MULTIEXP = Address.precompiled(0xF);
  public static final Address BLS12_PAIRING = Address.precompiled(0x10);
  public static final Address BLS12_MAP_FP_TO_G1 = Address.precompiled(0x11);
  public static final Address BLS12_MAP_FP2_TO_G2 = Address.precompiled(0x12);

  public static final Address ZERO = Address.fromHexString("0x0");

  protected Address(final Bytes bytes) {
    super(bytes);
  }

  public static Address wrap(final Bytes value) {
    checkArgument(
        value.size() == SIZE,
        "An account address must be %s bytes long, got %s",
        SIZE,
        value.size());
    return new Address(value);
  }

  /**
   * Creates an address from the given RLP-encoded input.
   *
   * @param input The input to read from
   * @return the input's corresponding address
   */
  public static Address readFrom(final RLPInput input) {
    final Bytes bytes = input.readBytes();
    if (bytes.size() != SIZE) {
      throw new RLPException(
          String.format("Address unexpected size of %s (needs %s)", bytes.size(), SIZE));
    }
    return Address.wrap(bytes);
  }

  /**
   * Extracts an address from a ECDSARECOVER result hash.
   *
   * @param hash A hash that has been obtained through hashing the return of the <code>ECDSARECOVER
   *     </code> function from Appendix F (Signing Transactions) of the Ethereum Yellow Paper.
   * @return The ethereum address from the provided hash.
   */
  public static Address extract(final Bytes32 hash) {
    return wrap(hash.slice(12, 20));
  }

  public static Address extract(final SECPPublicKey publicKey) {
    return Address.extract(keccak256(publicKey.getEncodedBytes()));
  }

  /**
   * Parse an hexadecimal string representing an account address.
   *
   * @param str An hexadecimal string (with or without the leading '0x') representing a valid
   *     account address.
   * @return The parsed address: {@code null} if the provided string is {@code null}.
   * @throws IllegalArgumentException if the string is either not hexadecimal, or not the valid
   *     representation of an address.
   */
  @JsonCreator
  public static Address fromHexString(final String str) {
    if (str == null) return null;
    return wrap(Bytes.fromHexStringLenient(str, SIZE));
  }

  /**
   * Parse an hexadecimal string representing an account address.
   *
   * @param str An hexadecimal string representing a valid account address (strictly 20 bytes).
   * @return The parsed address.
   * @throws IllegalArgumentException if the provided string is {@code null}.
   * @throws IllegalArgumentException if the string is either not hexadecimal, or not the valid
   *     representation of a 20 byte address.
   */
  public static Address fromHexStringStrict(final String str) {
    checkArgument(str != null);
    final Bytes value = Bytes.fromHexString(str);
    checkArgument(
        value.size() == SIZE,
        "An account address must be be %s bytes long, got %s",
        SIZE,
        value.size());
    return new Address(value);
  }

  public static Address precompiled(final int value) {
    // Keep it simple while we don't need precompiled above 127.
    checkArgument(value < Byte.MAX_VALUE);
    final byte[] address = new byte[SIZE];
    address[SIZE - 1] = (byte) value;
    return new Address(Bytes.wrap(address));
  }

  public static Address privacyPrecompiled(final int value) {
    return precompiled(value);
  }

  /**
   * Address of the created contract.
   *
   * <p>This implement equation (86) in Section 7 of the Yellow Paper (rev. a91c29c).
   *
   * @param senderAddress the address of the transaction sender.
   * @param nonce the nonce of this transaction.
   * @return The generated address of the created contract.
   */
  public static Address contractAddress(final Address senderAddress, final long nonce) {
    return Address.extract(
        keccak256(
            RLP.encode(
                out -> {
                  out.startList();
                  out.writeBytes(senderAddress);
                  out.writeLongScalar(nonce);
                  out.endList();
                })));
  }

  /**
   * Address of the created private contract.
   *
   * @param senderAddress the address of the transaction sender.
   * @param nonce the nonce of this transaction.
   * @param privacyGroupId hash of participants list ordered from Enclave response.
   * @return The generated address of the created private contract.
   */
  public static Address privateContractAddress(
      final Address senderAddress, final long nonce, final Bytes privacyGroupId) {
    return Address.extract(
        keccak256(
            RLP.encode(
                out -> {
                  out.startList();
                  out.writeBytes(senderAddress);
                  out.writeLongScalar(nonce);
                  out.writeBytes(privacyGroupId);
                  out.endList();
                })));
  }

  public static Address fromPlugin(final org.hyperledger.besu.plugin.data.Address address) {
    return address instanceof Address ? (Address) address : wrap(address.copy());
  }
}
