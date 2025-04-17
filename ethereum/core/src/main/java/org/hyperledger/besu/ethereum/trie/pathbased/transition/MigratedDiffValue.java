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
package org.hyperledger.besu.ethereum.trie.pathbased.transition;

import org.hyperledger.besu.ethereum.trie.pathbased.common.PathBasedDiffValue;

import java.util.Objects;

/**
 * A {@link PathBasedDiffValue} used during state transitions to represent values that exist in the
 * Patricia Merkle Trie (PMT), but are not yet present in the Verkle trie.
 *
 * <p>This class is used in migration or hybrid scenarios where data may be read from the PMT, but
 * must be written to the Verkle trie if it changes. Although a {@code prior} value is provided
 * (from PMT), it is treated as {@code null} from the Verkle perspective, since the key did not yet
 * exist there.
 *
 * <p>In practice:
 *
 * <ul>
 *   <li>{@link #isUnchanged()} uses the actual {@code prior} to detect whether the value changed.
 *   <li>{@link #getPrior()} returns {@code null} to indicate that the value did not exist in
 *       Verkle.
 * </ul>
 *
 * @param <T> the type of value being tracked
 */
public class MigratedDiffValue<T> extends PathBasedDiffValue<T> {

  /**
   * Constructs a new {@code PmtSourcedDiffBasedValue}.
   *
   * @param prior the value from the PMT used for comparison
   * @param updated the current value after execution
   */
  public MigratedDiffValue(final T prior, final T updated) {
    super(prior, updated);
  }

  /**
   * Determines if the value has changed by comparing the actual {@code prior} and {@code updated}
   * values. This is used to decide whether the value must be migrated to Verkle.
   *
   * @return true if the values are equal, false if a change occurred
   */
  @Override
  public boolean isUnchanged() {
    return Objects.equals(updated, prior);
  }

  /**
   * Returns {@code null} to signal that this value did not previously exist in the Verkle trie.
   * Although a {@code prior} value is provided for comparison, it is not exposed as part of the
   * Verkle state and should be treated as if the key was newly introduced.
   *
   * @return always {@code null}, from the Verkle trie’s point of view
   */
  @Override
  public T getPrior() {
    return null;
  }

  @Override
  public String toString() {
    return "PmtPathBasedValue{" + "prior=" + prior + ", updated=" + updated + '}';
  }
}
