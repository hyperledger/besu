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
package org.hyperledger.besu.ethereum.chain;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collection;
import java.util.Optional;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class BadBlockManager {

  private final Cache<Hash, Block> badBlocks =
      CacheBuilder.newBuilder().maximumSize(100).concurrencyLevel(1).build();
  private final Cache<Hash, BlockHeader> badHeaders =
      CacheBuilder.newBuilder().maximumSize(100).concurrencyLevel(1).build();
  private final Cache<Hash, Hash> latestValidHashes =
      CacheBuilder.newBuilder().maximumSize(100).concurrencyLevel(1).build();

  /**
   * Add a new invalid block.
   *
   * @param badBlock the invalid block
   */
  public void addBadBlock(final Block badBlock) {
    if (badBlock != null) {
      this.badBlocks.put(badBlock.getHash(), badBlock);
    }
  }

  /**
   * Return all invalid blocks
   *
   * @return a collection of invalid blocks
   */
  public Collection<Block> getBadBlocks() {
    return badBlocks.asMap().values();
  }

  /**
   * Return an invalid block based on the hash
   *
   * @param hash of the block
   * @return an invalid block
   */
  public Optional<Block> getBadBlock(final Hash hash) {
    return Optional.ofNullable(badBlocks.getIfPresent(hash));
  }

  public void addBadHeader(final BlockHeader header) {
    badHeaders.put(header.getHash(), header);
  }

  public Optional<BlockHeader> getBadHash(final Hash blockHash) {
    return Optional.ofNullable(badHeaders.getIfPresent(blockHash));
  }

  public void addLatestValidHash(final Hash blockHash, final Hash latestValidHash) {
    this.latestValidHashes.put(blockHash, latestValidHash);
  }

  public Optional<Hash> getLatestValidHash(final Hash blockHash) {
    return Optional.ofNullable(latestValidHashes.getIfPresent(blockHash));
  }
}
