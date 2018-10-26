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
package tech.pegasys.pantheon.ethereum.core;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.DelegatingBytesValue;

import com.fasterxml.jackson.annotation.JsonCreator;

/** A 160-bits account address. */
public class Address extends DelegatingBytesValue {

  public static final int SIZE = 20;

  /** Specific addresses of the "precompiled" contracts. */
  public static final Address ECREC = Address.precompiled(1);

  public static final Address SHA256 = Address.precompiled(2);
  public static final Address RIPEMD160 = Address.precompiled(3);
  public static final Address ID = Address.precompiled(4);
  public static final Address MODEXP = Address.precompiled(5);
  public static final Address ALTBN128_ADD = Address.precompiled(6);
  public static final Address ALTBN128_MUL = Address.precompiled(7);
  public static final Address ALTBN128_PAIRING = Address.precompiled(8);

  protected Address(final BytesValue bytes) {
    super(bytes);
    checkArgument(
        bytes.size() == SIZE,
        "An account address must be be %s bytes long, got %s",
        SIZE,
        bytes.size());
  }

  public static Address wrap(final BytesValue value) {
    return new Address(value);
  }

  /**
   * Creates an address from the given RLP-encoded input.
   *
   * @param input The input to read from
   * @return the input's corresponding address
   */
  public static Address readFrom(final RLPInput input) {
    final BytesValue bytes = input.readBytesValue();
    if (bytes.size() != SIZE) {
      throw new RLPException(
          String.format("Address unexpected size of %s (needs %s)", bytes.size(), SIZE));
    }
    return Address.wrap(bytes);
  }

  /**
   * @param hash A hash that has been obtained through hashing the return of the <code>ECDSARECOVER
   *     </code> function from Appendix F (Signing Transactions) of the Ethereum Yellow Paper.
   * @return The ethereum address from the provided hash.
   */
  public static Address extract(final Hash hash) {
    return wrap(hash.slice(12, 20));
  }

  /**
   * Parse an hexadecimal string representing an account address.
   *
   * @param str An hexadecimal string (with or without the leading '0x') representing a valid
   *     account address.
   * @return The parsed address.
   * @throws NullPointerException if the provided string is {@code null}.
   * @throws IllegalArgumentException if the string is either not hexadecimal, or not the valid
   *     representation of an address.
   */
  @JsonCreator
  public static Address fromHexString(final String str) {
    if (str == null) return null;

    return new Address(BytesValue.fromHexStringLenient(str, SIZE));
  }

  private static Address precompiled(final int value) {
    // Keep it simple while we don't need precompiled above 127.
    checkArgument(value < Byte.MAX_VALUE);
    final byte[] address = new byte[SIZE];
    address[SIZE - 1] = (byte) value;
    return new Address(BytesValue.wrap(address));
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
        Hash.hash(
            RLP.encode(
                out -> {
                  out.startList();
                  out.writeBytesValue(senderAddress);
                  out.writeLongScalar(nonce);
                  out.endList();
                })));
  }

  @Override
  public Address copy() {
    final BytesValue copiedStorage = wrapped.copy();
    return Address.wrap(copiedStorage);
  }
}
