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
package org.hyperledger.besu.ethereum.trie.pathbased.common;

import org.hyperledger.besu.plugin.data.BlockHeader;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Context which holds information relevant to a bonsai archive storage query. */
public class BonsaiContext {

  private final AtomicReference<Long> blockNumber;
  private final AtomicReference<BlockHeader> blockHeader;

  /** Context for Bonsai storage i.e. the block the storage applies to */
  public BonsaiContext(final long blockNumber) {
    this.blockNumber = new AtomicReference<>(blockNumber);
    this.blockHeader = new AtomicReference<>();
  }

  /** Default constructor with no context */
  public BonsaiContext() {
    this.blockNumber = new AtomicReference<>();
    this.blockHeader = new AtomicReference<>();
  }

  /**
   * Creates a copy of this context with the same block header
   *
   * @return a new BonsaiContext with the same state
   */
  public BonsaiContext copy() {
    var newCtx = new BonsaiContext();
    Optional.ofNullable(blockHeader.get()).ifPresent(newCtx::setBlockHeader);
    return newCtx;
  }

  /**
   * Set the block header for this context
   *
   * @param blockHeader the block header
   * @return this context for chaining
   */
  public BonsaiContext setBlockHeader(final BlockHeader blockHeader) {
    this.blockHeader.set(blockHeader);
    this.blockNumber.set(blockHeader.getNumber());
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

  /**
   * Get the block number currently applied to this context
   *
   * @return the optional block number
   */
  public Optional<Long> getBlockNumber() {
    return Optional.ofNullable(blockNumber.get());
  }
}
