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
package org.hyperledger.besu.ethereum.chain;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A range of blocks of the canonical chain whose headers are stored but whose bodies and receipts
 * are not yet. A snap sync leaves it behind when it completes without the history of the chain: the
 * node then follows the chain from the blocks right below its pivot block, while the bodies and
 * receipts of the blocks before those are downloaded in the background. The range shrinks from its
 * first block as that download proceeds.
 *
 * @param firstBlock the number of the first block without body and receipts
 * @param lastBlock the number of the last block without body and receipts. Every block above it, up
 *     to the chain head, has them.
 */
public record MissingBlockBodies(long firstBlock, long lastBlock) {

  public MissingBlockBodies(final long firstBlock, final long lastBlock) {
    checkArgument(firstBlock >= 0, "First block must not be negative");
    checkArgument(
        lastBlock >= firstBlock, "A range of missing bodies must hold at least one block");
    this.firstBlock = firstBlock;
    this.lastBlock = lastBlock;
  }

  /**
   * How many blocks are still without body and receipts.
   *
   * @return the number of blocks in the range
   */
  public long size() {
    return lastBlock - firstBlock + 1;
  }
}
