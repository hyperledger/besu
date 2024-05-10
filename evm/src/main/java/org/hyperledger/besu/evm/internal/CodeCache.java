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
package org.hyperledger.besu.evm.internal;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/** The Code cache. */
public class CodeCache {

  private final Cache<Hash, Code> cache;
  private final long weightLimit;

  /**
   * Instantiates a new Code cache.
   *
   * @param config the config
   */
  public CodeCache(final EvmConfiguration config) {
    this(config.getJumpDestCacheWeightBytes());
  }

  private CodeCache(final long maxWeightBytes) {
    this.weightLimit = maxWeightBytes;
    this.cache =
        Caffeine.newBuilder().maximumWeight(maxWeightBytes).weigher(new CodeScale()).build();
  }

  /**
   * Invalidate cache for given key.
   *
   * @param key the key
   */
  public void invalidate(final Hash key) {
    this.cache.invalidate(key);
  }

  /** Clean up. */
  public void cleanUp() {
    this.cache.cleanUp();
  }

  /**
   * Gets if present.
   *
   * @param codeHash the code hash
   * @return the if present
   */
  public Code getIfPresent(final Hash codeHash) {
    return cache.getIfPresent(codeHash);
  }

  /**
   * Put.
   *
   * @param key the key
   * @param value the value
   */
  public void put(final Hash key, final Code value) {
    cache.put(key, value);
  }

  /**
   * Size of cache.
   *
   * @return the long
   */
  public long size() {
    cache.cleanUp();
    return cache.estimatedSize();
  }

  /**
   * Gets weight limit.
   *
   * @return the weight limit
   */
  public long getWeightLimit() {
    return weightLimit;
  }
}
