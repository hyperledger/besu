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
package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.datatypes.Hash;

/** The Forkchoice event. */
public class ForkchoiceEvent {

  private final Hash headBlockHash;
  private final Hash safeBlockHash;
  private final Hash finalizedBlockHash;

  /**
   * Instantiates a new Forkchoice event.
   *
   * @param headBlockHash the head block hash
   * @param safeBlockHash the safe block hash
   * @param finalizedBlockHash the finalized block hash
   */
  public ForkchoiceEvent(
      final Hash headBlockHash, final Hash safeBlockHash, final Hash finalizedBlockHash) {
    this.headBlockHash = headBlockHash;
    this.finalizedBlockHash = finalizedBlockHash;
    this.safeBlockHash = safeBlockHash;
  }

  /**
   * Has valid finalized block hash.
   *
   * @return the boolean
   */
  public boolean hasValidFinalizedBlockHash() {
    return !finalizedBlockHash.equals(Hash.ZERO);
  }

  /**
   * Has valid safe block hash
   *
   * @return boolean
   */
  public boolean hasValidSafeBlockHash() {
    return !safeBlockHash.equals(Hash.ZERO);
  }

  /**
   * Gets head block hash.
   *
   * @return the head block hash
   */
  public Hash getHeadBlockHash() {
    return headBlockHash;
  }

  /**
   * Gets finalized block hash.
   *
   * @return the finalized block hash
   */
  public Hash getFinalizedBlockHash() {
    return finalizedBlockHash;
  }

  /**
   * Gets safe block hash.
   *
   * @return the safe block hash
   */
  public Hash getSafeBlockHash() {
    return safeBlockHash;
  }

  @Override
  public String toString() {
    return "ForkchoiceEvent{"
        + "headBlockHash="
        + headBlockHash
        + ", safeBlockHash="
        + safeBlockHash
        + ", finalizedBlockHash="
        + finalizedBlockHash
        + '}';
  }
}
