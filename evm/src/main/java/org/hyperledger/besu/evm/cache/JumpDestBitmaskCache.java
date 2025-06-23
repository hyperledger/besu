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
package org.hyperledger.besu.evm.cache;

import org.hyperledger.besu.datatypes.Hash;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * A cache for storing jump destination bitmasks associated with code hashes. This cache is used to
 * optimize the retrieval of jump destination bitmasks during EVM execution, reducing the need to
 * recompute them for the same code.
 */
public class JumpDestBitmaskCache {
  private final Cache<Hash, long[]> cache;

  /**
   * Constructs a new JumpDestBitmaskCache with a maximum size of 16,000 entries. The cache uses
   * Caffeine for efficient in-memory caching.
   */
  public JumpDestBitmaskCache() {
    this.cache = Caffeine.newBuilder().maximumSize(16_000L).build();
  }

  /**
   * Retrieves the jump destination bitmask for the given code hash from the cache.
   *
   * @param codeHash the hash of the code for which to retrieve the jump destinations
   * @return an array of long values representing the jump destinations, or null if not present
   */
  public long[] getIfPresent(final Hash codeHash) {
    return cache.getIfPresent(codeHash);
  }

  /**
   * Puts a jump destination bitmask into the cache associated with the given code hash.
   *
   * @param codeHash the hash of the code to associate with the jump destinations
   * @param jumpDests an array of long values representing the jump destinations
   */
  public void put(final Hash codeHash, final long[] jumpDests) {
    cache.put(codeHash, jumpDests);
  }
}
