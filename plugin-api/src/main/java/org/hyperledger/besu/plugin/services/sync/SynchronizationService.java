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
package org.hyperledger.besu.plugin.services.sync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BesuService;

/** Synchronization service wraps the sync state and sync event lifecycle. */
public interface SynchronizationService extends BesuService {

  /**
   * Enables P2P discovery.
   *
   * @param head the head of the chain.
   * @param safeBlock the safe block.
   * @param finalizedBlock the finalized block.
   */
  void fireNewUnverifiedForkchoiceEvent(Hash head, Hash safeBlock, Hash finalizedBlock);

  /**
   * Set the head of the chain.
   *
   * @param blockHeader the block header
   * @param blockBody the block body
   * @return true if the head was set, false otherwise.
   */
  boolean setHead(final BlockHeader blockHeader, final BlockBody blockBody);

  /**
   * Adds the block header and body to the head of the chain directly, without using a block
   * importer or validation.
   *
   * @param blockHeader the block header
   * @param blockBody the block body
   * @return true if the head was set, false otherwise.
   */
  boolean setHeadUnsafe(BlockHeader blockHeader, BlockBody blockBody);

  /**
   * Returns whether the initial chain and worldstate sync is complete.
   *
   * @return true if the initial sync phase is done, false otherwise.
   */
  boolean isInitialSyncPhaseDone();

  /** Disables the worldstate trie for update. */
  void disableWorldStateTrie();
}
