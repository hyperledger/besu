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

import org.hyperledger.besu.datatypes.Hash;

import java.util.ArrayDeque;
import java.util.Optional;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.tuweni.bytes.Bytes;

/**
 * Holds the RLP of the most recently imported blocks. As soon as it holds more than {@code
 * maxBlocks} entries or more than {@code maxBytes}, it evicts the oldest imported block.
 */
final class RecentBlocksRlpCache {
  // cache node, hash key, Bytes wrapper and queue slot; small entries would otherwise weigh a few
  // bytes
  static final int ENTRY_OVERHEAD = 200;

  private final int maxBlocks;
  private final long maxBytes;
  // unbounded, as this class evicts itself; Guava gives lock-free reads and the cache metrics
  private final Cache<Hash, Bytes> cache = CacheBuilder.newBuilder().recordStats().build();
  private final ArrayDeque<Hash> importOrder = new ArrayDeque<>();
  private long bytes;
  private long evictions;

  RecentBlocksRlpCache(final int maxBlocks, final long maxBytes) {
    this.maxBlocks = maxBlocks;
    this.maxBytes = maxBytes;
  }

  Optional<Bytes> get(final Hash blockHash) {
    return Optional.ofNullable(cache.getIfPresent(blockHash));
  }

  synchronized void put(final Hash blockHash, final Bytes rlp) {
    final long weight = weigh(rlp);
    // a single entry over the limit would otherwise evict every other block
    if (maxBlocks == 0 || weight > maxBytes || cache.asMap().containsKey(blockHash)) {
      return;
    }
    cache.put(blockHash, rlp);
    importOrder.addLast(blockHash);
    bytes += weight;
    while (importOrder.size() > maxBlocks || bytes > maxBytes) {
      bytes -= weigh(cache.asMap().remove(importOrder.removeFirst()));
      evictions++;
    }
  }

  synchronized long weight() {
    return bytes;
  }

  synchronized long evictionCount() {
    return evictions;
  }

  Cache<Hash, Bytes> cache() {
    return cache;
  }

  private static long weigh(final Bytes rlp) {
    return (long) rlp.size() + ENTRY_OVERHEAD;
  }
}
