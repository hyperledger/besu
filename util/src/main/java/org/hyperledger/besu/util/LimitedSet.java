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
package org.hyperledger.besu.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Helper that creates a thread-safe set with a maximum capacity. */
public final class LimitedSet {
  public enum Mode {
    DROP_LEAST_RECENTLY_ACCESSED,
    DROP_OLDEST_ELEMENT
  }

  private LimitedSet() {}

  /**
   * Creates a limited set of a initial size, maximum size, and eviction mode.
   *
   * @param initialCapacity The initial size to allocate for the set.
   * @param maxSize The maximum number of elements to keep in the set.
   * @param mode A mode that determines which element is evicted when the set exceeds its max size.
   * @param <T> The type of object held in the set.
   * @return A thread-safe set that will evict elements when the max size is exceeded.
   */
  public static final <T> Set<T> create(
      final int initialCapacity, final int maxSize, final Mode mode) {
    final boolean useAccessOrder = mode.equals(Mode.DROP_LEAST_RECENTLY_ACCESSED);
    return Collections.synchronizedSet(
        Collections.newSetFromMap(
            new LinkedHashMap<T, Boolean>(initialCapacity, 0.75f, useAccessOrder) {
              @Override
              protected boolean removeEldestEntry(final Map.Entry<T, Boolean> eldest) {
                return size() > maxSize;
              }
            }));
  }
}
