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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache;

import static org.hyperledger.besu.metrics.BesuMetricCategory.BONSAI_CACHE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.function.Supplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/** The Code cache. */
public class CodeCache implements org.hyperledger.besu.evm.internal.CodeCache {

  // private final MemoryBoundCache<Hash, Code> cache;
  private final Cache<Hash, Code> cache;
  private OperationTimer lookupTimer;

  /** Instantiates a new Code cache. */
  public CodeCache() {
    // this.cache = new MemoryBoundCache<>(256 * 1024 * 1024, CodeMemoryFootprint::estimate);
    this.cache = Caffeine.newBuilder().maximumSize(16_000L).recordStats().build();
  }

  /**
   * Sets up the metrics system for the code cache.
   *
   * @param metricsSystem the metrics system to use
   */
  public void setupMetricsSystem(final ObservableMetricsSystem metricsSystem) {
    this.lookupTimer =
        metricsSystem.createTimer(
            BONSAI_CACHE, "code_cache_lookup_time", "Time spent performing CodeCache lookups");

    metricsSystem.createLongGauge(
        BONSAI_CACHE,
        "code_cache_size",
        "Current number of entries in the code cache",
        cache::estimatedSize);

    metricsSystem.createLongGauge(
        BONSAI_CACHE,
        "code_cache_evictions",
        "Total number of evictions from the code cache",
        () -> cache.stats().evictionCount());

    metricsSystem.createGauge(
        BONSAI_CACHE,
        "code_cache_hit_rate",
        "Hit rate of the code cache",
        () -> cache.stats().hitRate());
  }

  /**
   * Gets if present.
   *
   * @param codeHash the code hash
   * @return the code if present
   */
  @Override
  public Code getIfPresent(final Hash codeHash) {
    if (lookupTimer == null) {
      return cache.getIfPresent(codeHash);
    }

    try (var ignored = lookupTimer.startTimer()) {
      return cache.getIfPresent(codeHash);
    }
  }

  /**
   * Put.
   *
   * @param codeHash the code hash
   * @param code the code
   */
  @Override
  public void put(final Hash codeHash, final Code code) {
    cache.put(codeHash, code);
  }
}
