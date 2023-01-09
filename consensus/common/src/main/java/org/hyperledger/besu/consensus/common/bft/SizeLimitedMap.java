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
package org.hyperledger.besu.consensus.common.bft;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Map that is limited to a specified size and will evict oldest entries when the size limit is
 * reached.
 *
 * @param <K> the type parameter
 * @param <V> the type parameter
 */
public class SizeLimitedMap<K, V> extends LinkedHashMap<K, V> {
  /** Maximum size of map */
  private final int maxEntries;

  /**
   * Instantiates a new Size limited map.
   *
   * @param maxEntries the max entries
   */
  public SizeLimitedMap(final int maxEntries) {
    this.maxEntries = maxEntries;
  }

  @Override
  protected boolean removeEldestEntry(final Map.Entry<K, V> ignored) {
    return size() > maxEntries;
  }

  @Override
  public Object clone() {
    return super.clone();
  }
}
