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
package org.hyperledger.besu.util.cache;

/**
 * A functional interface that represents a function that takes two arguments and produces an int
 * result. This is typically used for calculating the memory footprint of a key-value pair in a
 * cache.
 *
 * @param <K> the type of the first input argument
 * @param <V> the type of the second input argument
 */
@FunctionalInterface
public interface BiToIntFunction<K, V> {
  /**
   * Applies this function to the given arguments.
   *
   * @param key the first input argument
   * @param value the second input argument
   * @return the function result as an int
   */
  int applyAsInt(K key, V value);
}
