/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.trie.diffbased.transition;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.diffbased.transition.storage.VerkleTransitionWorldStateKeyValueStorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerkleTransitionContext {

  public VerkleTransitionContext(final long verkleTimestamp) {
    // TODO, this should be a singleton to prevent transition event spam
    this.verkleTransitionTimestamp = verkleTimestamp;
  }

  private static final Logger LOG =
      LoggerFactory.getLogger(VerkleTransitionWorldStateKeyValueStorage.class);

  final long verkleTransitionTimestamp;

  /**
   * Method which specifies if a worldstate for a given block header should be using the verkle
   * worldstate for mutation.
   *
   * <p>This essentially _IS_ the verkle transition logic.
   *
   * @param blockHeader blockheader corresponding to a specific worldstate, typically a parent block
   *     used to fetch the worldstate for mutation.
   * @return boolean indicating true for verkle, false for pre-transition
   */
  public boolean isVerkleForMutation(final BlockHeader blockHeader) {
    // return true if the timestamp is post transtion
    if (blockHeader.getTimestamp() >= verkleTransitionTimestamp) {
      return true;
    }

    // return true if the timestamp is within 1 block (12 seconds) of the transition, such that
    // any state mutation will happen on verkle rather than patricia merkle trie
    if (blockHeader.getTimestamp() + 12_000 > verkleTransitionTimestamp) {
      LOG.warn(
          "Returning implicit verkle transition worldstate for mutation of {}",
          blockHeader.toLogString());
      return true;
    }

    // otherwise this timestamp should be using patricia merkle trie:
    return false;
  }

  public boolean isBeforeTransition() {
    return System.currentTimeMillis() < verkleTransitionTimestamp;
  }

  public boolean isBeforeTransition(final long timestamp) {
    return timestamp < verkleTransitionTimestamp;
  }

  /**
   * Return whether the world state has fully migrated to verkle
   *
   * @return boolean indicating whether verkle transition is complete or not
   */
  public boolean isTransitionFinalized() {
    return isTransitionFinalized(System.currentTimeMillis());
  }

  /**
   * Return whether the state has fully transitioned and no state should be
   * read from MPT
   *
   * @param timestamp ts for which the verkle transition should have completed
   *
   * @return boolean indicating whether verkle transition is complete or not
   */
  public boolean isTransitionFinalized(final long timestamp) {
    //TODO: write me once we have a frozen MPT and defined migration cadence
    return false;
  }
}
