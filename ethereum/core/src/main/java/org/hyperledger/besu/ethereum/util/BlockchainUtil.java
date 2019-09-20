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
package org.hyperledger.besu.ethereum.util;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

public class BlockchainUtil {

  private BlockchainUtil() {}

  /**
   * General utility to process a list of headers and a blockchain, sussing out which header in the
   * input list is simultaneously the highest order block number and a direct match with one of the
   * headers of the local chain. The purpose of which being to determine the point of departure in
   * fork scenarios.
   *
   * @param blockchain our local copy of the blockchain
   * @param headers the list of remote headers
   * @param ascendingHeaderOrder whether the headers are sorted in ascending or descending order
   * @return index of the highest known header, or an empty value if no header is known
   */
  public static OptionalInt findHighestKnownBlockIndex(
      final Blockchain blockchain,
      final List<BlockHeader> headers,
      final boolean ascendingHeaderOrder) {
    final int offset = ascendingHeaderOrder ? -1 : 0;
    final Comparator<BlockHeader> comparator =
        knownBlockComparator(blockchain, ascendingHeaderOrder);

    final int insertionIndex = -Collections.binarySearch(headers, null, comparator) - 1;
    final int ancestorIndex = insertionIndex + offset;
    if (ancestorIndex < 0 || ancestorIndex >= headers.size()) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(ancestorIndex);
  }

  private static Comparator<BlockHeader> knownBlockComparator(
      final Blockchain blockchain, final boolean ascendingHeaderOrder) {
    final Comparator<BlockHeader> comparator =
        (final BlockHeader element0, final BlockHeader element1) -> {
          if (element0 == null) {
            return blockchain.contains(element1.getHash()) ? -1 : 1;
          }
          return blockchain.contains(element0.getHash()) ? 1 : -1;
        };
    return ascendingHeaderOrder ? comparator.reversed() : comparator;
  }
}
