/*
 * Copyright Hyperledger Besu Contributors.
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

public class ForkchoiceEvent {

  private final Hash headBlockHash;
  private final Hash safeBlockHash;
  private final Hash finalizedBlockHash;

  public ForkchoiceEvent(
      final Hash headBlockHash, final Hash safeBlockHash, final Hash finalizedBlockHash) {
    this.headBlockHash = headBlockHash;
    this.finalizedBlockHash = finalizedBlockHash;
    this.safeBlockHash = safeBlockHash;
  }

  public boolean hasValidFinalizedBlockHash() {
    return !finalizedBlockHash.equals(Hash.ZERO);
  }

  public Hash getHeadBlockHash() {
    return headBlockHash;
  }

  public Hash getFinalizedBlockHash() {
    return finalizedBlockHash;
  }

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
        + ", safeBlockHash="
        + safeBlockHash
        + '}';
  }
}
