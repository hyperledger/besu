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
package org.hyperledger.besu.ethereum.trie.pathbased.common.worldview;

/**
 * Interface for collecting state-layer metrics during block execution.
 *
 * <p>Implementations are threaded through the world state object graph (PathBasedWorldState →
 * accumulator → accounts) so that state operations can record metrics without relying on
 * ThreadLocal statics.
 */
public interface StateMetricsCollector {

  /** Increments the account write counter. */
  void incrementAccountWrites();

  /** Increments the storage write counter. */
  void incrementStorageWrites();

  /** Increments the code read counter. */
  void incrementCodeReads();

  /**
   * Adds bytes read for code.
   *
   * @param bytes the number of bytes read
   */
  void addCodeBytesRead(long bytes);

  /** Increments the code cache hit counter. */
  void incrementCodeCacheHits();

  /** Increments the code cache miss counter. */
  void incrementCodeCacheMisses();

  /** A no-op implementation that discards all metrics. */
  StateMetricsCollector NOOP =
      new StateMetricsCollector() {
        @Override
        public void incrementAccountWrites() {}

        @Override
        public void incrementStorageWrites() {}

        @Override
        public void incrementCodeReads() {}

        @Override
        public void addCodeBytesRead(final long bytes) {}

        @Override
        public void incrementCodeCacheHits() {}

        @Override
        public void incrementCodeCacheMisses() {}
      };
}
