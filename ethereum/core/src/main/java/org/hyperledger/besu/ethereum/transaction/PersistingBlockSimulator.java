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
package org.hyperledger.besu.ethereum.transaction;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

/**
 * BlockSimulator is responsible for simulating the execution of a block. It processes transactions
 * and applies state overrides to simulate the block execution.
 */
public class PersistingBlockSimulator {
  private final BlockSimulator blockSimulator;
  private final WorldStateArchive worldStateArchive;

  /**
   * Construct a new PersistingBlockSimulator.
   *
   * @param blockSimulator The block simulator to use.
   * @param worldStateArchive The world state archive to use.
   */
  public PersistingBlockSimulator(
      final BlockSimulator blockSimulator, final WorldStateArchive worldStateArchive) {
    this.blockSimulator = blockSimulator;
    this.worldStateArchive = worldStateArchive;
  }

  /**
   * Process a block with the given header and state call.
   *
   * @param header The block header to process.
   * @param blockStateCall The block state call to use.
   * @return The result of processing the block.
   */
  public BlockSimulationResult process(
      final BlockHeader header, final BlockStateCall blockStateCall) {
    try (final MutableWorldState ws = getWorldState(header)) {
      var result = blockSimulator.processWithMutableWorldState(header, blockStateCall, ws);
      ws.persist(result.getBlock().getHeader());
      return result;
    } catch (final Exception e) {
      throw new RuntimeException("Error simulating block", e);
    }
  }

  /**
   * Process a block with the given header and state call.
   *
   * @param header The block header to process.
   * @return The result of processing the block.
   */
  private MutableWorldState getWorldState(final BlockHeader header) {
    return worldStateArchive
        .getMutable(header, true)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Public world state not available for block " + header.toLogString()));
  }
}
