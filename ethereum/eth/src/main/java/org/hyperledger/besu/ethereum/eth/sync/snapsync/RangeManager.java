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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.InnerNodeDiscoveryManager;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * This class helps to generate ranges according to several parameters (the start and the end of the
 * range, its size)
 */
public class RangeManager {

  public static final Hash MIN_RANGE =
      Hash.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000");
  public static final Hash MAX_RANGE =
      Hash.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

  public static Map<Bytes32, Bytes32> generateAllRanges(final int sizeRange) {
    return generateRanges(
        MIN_RANGE.toUnsignedBigInteger(), MAX_RANGE.toUnsignedBigInteger(), sizeRange);
  }

  /**
   * Generate a range
   *
   * @param min start of the range in bytes
   * @param max the max end of the range in bytes
   * @param sizeRange size of the range
   * @return the start and end of the generated range
   */
  public static Map<Bytes32, Bytes32> generateRanges(
      final Bytes32 min, final Bytes32 max, final int sizeRange) {
    return generateRanges(min.toUnsignedBigInteger(), max.toUnsignedBigInteger(), sizeRange);
  }

  /**
   * Generate a range
   *
   * @param min start of the range
   * @param max the max end of the range
   * @param sizeRange size of the range
   * @return the start and end of the generated range
   */
  public static Map<Bytes32, Bytes32> generateRanges(
      final BigInteger min, final BigInteger max, final int sizeRange) {
    final BigInteger rangeSize = max.subtract(min).divide(BigInteger.valueOf(sizeRange));
    final TreeMap<Bytes32, Bytes32> ranges = new TreeMap<>();
    BigInteger currentStart = min;
    BigInteger currentEnd = min;
    while (max.subtract(currentEnd).compareTo(rangeSize) > 0) {
      currentEnd = currentStart.add(rangeSize);
      ranges.put(format(currentStart), format(currentEnd));
      currentStart = currentStart.add(rangeSize).add(BigInteger.ONE);
    }
    if (max.subtract(currentEnd).compareTo(BigInteger.ZERO) > 0) {
      currentEnd = currentStart.add(max.subtract(currentEnd)).subtract(BigInteger.ONE);
      ranges.put(format(currentStart), format(currentEnd));
    }
    return ranges;
  }

  /**
   * Helps to create a new range according to the last data obtained. This happens when a peer
   * doesn't return all of the data in a range.
   *
   * @param endKeyHash the end of the range initially wanted
   * @param receivedKeys the last key received
   * @return the start of the new range
   */
  public static Optional<Bytes32> findNewBeginElementInRange(
      final Bytes32 worldstateRootHash,
      final List<Bytes> proofs,
      final TreeMap<Bytes32, Bytes> receivedKeys,
      final Bytes32 endKeyHash) {
    if (receivedKeys.isEmpty() || receivedKeys.lastKey().compareTo(endKeyHash) >= 0) {
      return Optional.empty();
    } else {
      final Map<Bytes32, Bytes> proofsEntries = Collections.synchronizedMap(new HashMap<>());
      for (Bytes proof : proofs) {
        proofsEntries.put(Hash.hash(proof), proof);
      }
      final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
          new StoredMerklePatriciaTrie<>(
              new InnerNodeDiscoveryManager<>(
                  (location, key) -> Optional.ofNullable(proofsEntries.get(key)),
                  Function.identity(),
                  Function.identity(),
                  receivedKeys.lastKey(),
                  endKeyHash,
                  false),
              worldstateRootHash);

      try {
        storageTrie.visitAll(bytesNode -> {});
      } catch (MerkleTrieException e) {
        return Optional.of(InnerNodeDiscoveryManager.decodePath(e.getLocation()));
      }
      return Optional.empty();
    }
  }

  private static Bytes32 format(final BigInteger data) {
    return Bytes32.leftPad(Bytes.of(data.toByteArray()).trimLeadingZeros());
  }
}
