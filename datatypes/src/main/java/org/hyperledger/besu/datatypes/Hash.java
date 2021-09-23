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

import static org.hyperledger.besu.crypto.Hash.keccak256;

import org.hyperledger.besu.ethereum.rlp.RLP;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.DelegatingBytes32;

/** A 32-bytes hash value as used in Ethereum blocks, that is the result of the KEC algorithm. */
public class Hash extends DelegatingBytes32 implements org.hyperledger.besu.plugin.data.Hash {

  public static final Hash ZERO = new Hash(Bytes32.ZERO);

  /**
   * Hash of an RLP encoded trie hash with no content, or
   * "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"
   */
  public static final Hash EMPTY_TRIE_HASH = Hash.hash(RLP.NULL);

  /**
   * Hash of a zero length RLP list, or
   * "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347"
   */
  public static final Hash EMPTY_LIST_HASH = Hash.hash(RLP.EMPTY_LIST);

  /**
   * Hash of an empty string, or
   * "0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"
   */
  public static final Hash EMPTY = hash(Bytes.EMPTY);

  private Hash(final Bytes32 bytes) {
    super(bytes);
  }

  public static Hash hash(final Bytes value) {
    return new Hash(keccak256(value));
  }

  public static Hash wrap(final Bytes32 bytes) {
    if (bytes instanceof Hash) {
      return (Hash) bytes;
    }
    return new Hash(bytes);
  }

  /**
   * Parse an hexadecimal string representing a hash value.
   *
   * @param str An hexadecimal string (with or without the leading '0x') representing a valid hash
   *     value.
   * @return The parsed hash.
   * @throws NullPointerException if the provided string is {@code null}.
   * @throws IllegalArgumentException if the string is either not hexadecimal, or not the valid
   *     representation of a hash (not 32 bytes).
   */
  @JsonCreator
  public static Hash fromHexString(final String str) {
    return new Hash(Bytes32.fromHexStringStrict(str));
  }

  public static Hash fromHexStringLenient(final String str) {
    return new Hash(Bytes32.fromHexStringLenient(str));
  }

  public static Hash fromPlugin(final org.hyperledger.besu.plugin.data.Hash blockHash) {
    return blockHash instanceof Hash ? (Hash) blockHash : wrap(blockHash);
  }
}
