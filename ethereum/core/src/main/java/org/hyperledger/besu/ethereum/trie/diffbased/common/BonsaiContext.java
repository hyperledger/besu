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
package org.hyperledger.besu.ethereum.trie.diffbased.common;

import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Context which holds information relevant to a bonsai archive storage query. */
public class BonsaiContext {

  private final AtomicReference<BlockHeader> blockHeader;

  /** Context for Bonsai storage i.e. the block the storage applies to */
  public BonsaiContext() {
    blockHeader = new AtomicReference<>();
  }

  /**
   * Create a copy the current context
   *
   * @return the copied context
   */
  public BonsaiContext copy() {
    var newCtx = new BonsaiContext();
    Optional.ofNullable(blockHeader.get()).ifPresent(newCtx::setBlockHeader);
    return newCtx;
  }

  /**
   * Set the new block header for the context
   *
   * @param blockHeader the new block header
   * @return the updated context
   */
  public BonsaiContext setBlockHeader(final BlockHeader blockHeader) {
    this.blockHeader.set(blockHeader);
    return this;
  }

  /**
   * Get the block header currently applied to this context
   *
   * @return the optional block header
   */
  public Optional<BlockHeader> getBlockHeader() {
    return Optional.ofNullable(blockHeader.get());
  }
}
