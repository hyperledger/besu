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
package org.hyperledger.besu.chainexport;

import org.hyperledger.besu.util.ssz.Merkleizer;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** A class for accumulating blocks to produce an ERA1 Accumulator section */
public class Era1Accumulator {
  private static final int ERA1_FILE_BLOCKS = 8192;

  private final Merkleizer merkleizer;
  private final List<Bytes32> blockAccumulations = new ArrayList<>();

  /**
   * Creates an Era1Accumulator using the supplied merkleizer
   *
   * @param merkleizer A Merkleizer object which handles the merkle tree operations
   */
  public Era1Accumulator(final Merkleizer merkleizer) {
    this.merkleizer = merkleizer;
  }

  /**
   * Add a block to the accumulator
   *
   * @param blockHash The hash of the block
   * @param totalDifficulty The total difficulty of the block
   */
  public void addBlock(final Bytes32 blockHash, final UInt256 totalDifficulty) {
    blockAccumulations.add(
        merkleizer.merkleizeChunks(
            List.of(blockHash, Bytes32.wrap(totalDifficulty.toArray(ByteOrder.LITTLE_ENDIAN)))));
  }

  /**
   * Performs the final accumulation and mixes in the length
   *
   * @return The final result of the accumulation process
   */
  public Bytes32 accumulate() {
    Bytes32 rootHash = merkleizer.merkleizeChunks(blockAccumulations, ERA1_FILE_BLOCKS);
    return merkleizer.mixinLength(rootHash, UInt256.valueOf(blockAccumulations.size()));
  }
}
