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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class JumpDestCache {

  private final Cache<Hash, long[]> cache;
  private final long weightLimit;

  public JumpDestCache(final JumpDestCacheConfiguration config) {
    this(config.getContractCacheWeightBytes());
  }

  private JumpDestCache(final long maxWeightBytes) {
    this.weightLimit = maxWeightBytes;
    this.cache =
        Caffeine.newBuilder().maximumWeight(maxWeightBytes).weigher(new CodeScale()).build();
  }

  public void invalidate(final Hash key) {
    this.cache.invalidate(key);
  }

  public void cleanUp() {
    this.cache.cleanUp();
  }

  public long[] getIfPresent(final Hash codeHash) {
    return cache.getIfPresent(codeHash);
  }

  public void put(final Hash key, final long[] value) {
    cache.put(key, value);
  }

  public long size() {
    cache.cleanUp();
    return cache.estimatedSize();
  }

  public long getWeightLimit() {
    return weightLimit;
  }
}
