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
package org.hyperledger.besu.ethereum.eth.sync.fullsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.util.function.BooleanSupplier;

import org.apache.tuweni.units.bigints.UInt256;

/** return true when termination condition is fulfilled and the full sync should stop */
public interface SyncTerminationCondition extends BooleanSupplier {

  default boolean shouldContinueDownload() {
    return !shouldStopDownload();
  }

  default boolean shouldStopDownload() {
    return getAsBoolean();
  }

  /**
   * When we want full sync to continue forever (for instance when we don't want to merge)
   *
   * @return always false therefore continues forever *
   */
  static SyncTerminationCondition never() {
    return () -> false;
  }

  /**
   * When we want full sync to finish after reaching a difficulty. For instance when we merge on
   * total terminal difficulty.
   *
   * @param targetDifficulty target difficulty to reach
   * @param blockchain blockchain to reach the difficulty on
   * @return true when blockchain reaches difficulty
   */
  static SyncTerminationCondition difficulty(
      final UInt256 targetDifficulty, final Blockchain blockchain) {
    return difficulty(Difficulty.of(targetDifficulty), blockchain);
  }

  /**
   * When we want full sync to finish after reaching a difficulty. For instance when we merge on
   * total terminal difficulty.
   *
   * @param targetDifficulty target difficulty to reach
   * @param blockchain blockchain to reach the difficulty on*
   * @return true when blockchain reaches difficulty
   */
  static SyncTerminationCondition difficulty(
      final Difficulty targetDifficulty, final Blockchain blockchain) {
    return () ->
        blockchain.getChainHead().getTotalDifficulty().greaterOrEqualThan(targetDifficulty);
  }

  /**
   * When we want the full sync to finish on a target hash. For instance when we reach a merge
   * checkpoint.
   *
   * @param blockHash target hash to look for
   * @param blockchain blockchain to reach the difficulty on
   * @return true when blockchain contains target hash (target hash can be changed)
   */
  static FlexibleBlockHashTerminalCondition blockHash(
      final Hash blockHash, final Blockchain blockchain) {
    return new FlexibleBlockHashTerminalCondition(blockHash, blockchain);
  }
}
