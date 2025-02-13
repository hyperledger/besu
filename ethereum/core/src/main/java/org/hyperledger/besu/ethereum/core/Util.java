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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.patricia.SimpleMerklePatriciaTrie;

import java.util.List;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class Util {

  /**
   * Converts a Signature to an Address, underlying math requires the hash of the data used to
   * create the signature.
   *
   * @param seal the signature from which an address is to be extracted
   * @param dataHash the hash of the data which was signed.
   * @return The Address of the Ethereum node which signed the data defined by the supplied dataHash
   */
  public static Address signatureToAddress(final SECPSignature seal, final Hash dataHash) {
    return SignatureAlgorithmFactory.getInstance()
        .recoverPublicKeyFromSignature(dataHash, seal)
        .map(Util::publicKeyToAddress)
        .orElse(null);
  }

  public static Address publicKeyToAddress(final SECPPublicKey publicKey) {
    return publicKeyToAddress(publicKey.getEncodedBytes());
  }

  public static Address publicKeyToAddress(final Bytes publicKeyBytes) {
    return Address.extract(Hash.hash(publicKeyBytes));
  }

  /**
   * Implements a fast version of ceiling(numerator/denominator) that does not require using
   * floating point math
   *
   * @param numerator Numerator
   * @param denominator Denominator
   * @return result of ceiling(numerator/denominator)
   */
  public static int fastDivCeiling(final int numerator, final int denominator) {
    return ((numerator - 1) / denominator) + 1;
  }

  /**
   * RLP encoded index for an MPT based on the index.
   *
   * @param i index of the index key
   * @return the RLP encoded index key
   */
  public static Bytes indexKey(final int i) {
    return RLP.encodeOne(UInt256.valueOf(i).trimLeadingZeros());
  }

  /**
   * Calculates the root of an MPT based on it's entries.
   *
   * @param bytes list of the entries strictly ordered by index, starting at 0
   * @return the root hash of the MPT
   */
  public static Hash getRootFromListOfBytes(final List<Bytes> bytes) {
    if (bytes.isEmpty()) {
      return Hash.EMPTY_TRIE_HASH;
    }
    final MerkleTrie<Bytes, Bytes> trie = new SimpleMerklePatriciaTrie<>(b -> b);
    IntStream.range(0, bytes.size())
        .forEach(
            i -> {
              trie.put(indexKey(i), bytes.get(i));
            });
    return Hash.wrap(trie.getRootHash());
  }
}
