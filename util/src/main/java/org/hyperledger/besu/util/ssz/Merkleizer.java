/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.util.ssz;

import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.bouncycastle.jcajce.provider.digest.SHA256.Digest;

/** Handles functions relevant to merkleization from the SSZ specification */
public class Merkleizer {
  private final MessageDigest hashFunction;

  /** Constructs a Merkleizer */
  public Merkleizer() {
    this.hashFunction = new Digest();
  }

  /**
   * Merkleize the supplied leaves as chunks
   *
   * @param leaves the leaves to merkleized
   * @return the root hash from merkle tree produced by the supplied leaves
   */
  public Bytes32 merkleizeChunks(final List<Bytes32> leaves) {
    return merkleizeChunks(leaves, leaves.size());
  }

  /**
   * Merkleize the supplied leaves as chunks, padding the leaves with zero chunks to padToSize if
   * necessary
   *
   * @param leaves the leaves to merkleized
   * @param padToSize the desired size to which leaves should be padded before merkleization
   * @return the root hash from merkle tree produced by the supplied leaves
   */
  public Bytes32 merkleizeChunks(final List<Bytes32> leaves, final int padToSize) {
    if (leaves.isEmpty()) {
      throw new RuntimeException("Unable to merkleize empty list of leaves");
    }
    // take a copy to ensure our chunk storage is mutable, and original input remains untouched
    final List<Bytes32> chunks = new ArrayList<>(leaves);

    // add the requested padding
    int requiredPadding = padToSize - chunks.size();
    while (requiredPadding-- > 0) {
      chunks.add(Bytes32.ZERO);
    }

    // ensure the total leaves is a power of 2 (use zero chunks to pad)
    int requiredLeafCount = nextPowerOfTwo(chunks.size()) - chunks.size();
    while (requiredLeafCount-- > 0) {
      chunks.add(Bytes32.ZERO);
    }

    List<Bytes32> result = chunks;
    while (result.size() != 1) {
      result = hashPairs(result);
    }
    return result.getFirst();
  }

  /**
   * Mixes the supplied length with the supplied rootHash
   *
   * @param rootHash The root hash to have the supplied length mixed in to
   * @param length The length to mixed in to the supplied root hash
   * @return the supplied length mixed in with the supplied rootHash
   */
  public Bytes32 mixinLength(final Bytes32 rootHash, final UInt256 length) {
    return hashPairs(List.of(rootHash, Bytes32.wrap(length.toArray(ByteOrder.LITTLE_ENDIAN))))
        .getFirst();
  }

  private List<Bytes32> hashPairs(final List<Bytes32> chunks) {
    List<Bytes32> result = new ArrayList<>();
    for (int i = 0; i < chunks.size() / 2; i++) {
      byte[] concatenatedLeaves =
          Bytes.concatenate(chunks.get(i * 2), chunks.get(i * 2 + 1)).toArray();
      result.add(Bytes32.wrap(hashFunction.digest(concatenatedLeaves)));
      hashFunction.reset();
    }
    return result;
  }

  private int nextPowerOfTwo(final int number) {
    if (number == 1) {
      return 2;
    }
    int power = 1;
    while (power < number) {
      power *= 2;
    }
    return power;
  }
}
