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
package org.hyperledger.besu.collections.undo;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A manually ticked clock to determine when in execution an item was added to an undo collection.
 * This allows for tracking of only one undo marker across multiple collections and rolling back
 * multiple collections to a consistent point with only one number.
 */
public interface Undoable {
  /** The global mark clock for registering marks in undoable collections. */
  AtomicLong markState = new AtomicLong();

  /**
   * Retrieves an identifier that represents the current state of the collection.
   *
   * <p>This marker is tracked globally so getting a mark in one Undoable collection will provide a
   * mark that can be used in other UndoableCollections
   *
   * @return a long representing the current state.
   */
  default long mark() {
    return markState.get();
  }

  /**
   * Advances the mark to a state greater than when it was before.
   *
   * @return a new mark that is guaranteed to be after the prior mark's value.
   */
  static long incrementMarkStatic() {
    return markState.incrementAndGet();
  }

  /**
   * The last time this object was updated. Any undo requests greater than this mark will result in
   * no changes.
   *
   * @return The most recent mark.
   */
  long lastUpdate();

  /**
   * Returns the state of the collection to the state it was in when the mark was retrieved.
   * Additions and removals are undone un reverse order until the collection state is restored.
   *
   * @param mark The mark to which the undo should proceed to, but not prior to
   */
  void undo(long mark);

  /**
   * Since we are relying on delegation, iterators should not be able to modify the collection.
   *
   * @param <V> the type of the collection
   */
  final class ReadOnlyIterator<V> implements Iterator<V> {
    Iterator<V> delegate;

    /**
     * Create a read-only delegated iterator
     *
     * @param delegate the iterator to pass read only calls to.
     */
    public ReadOnlyIterator(final Iterator<V> delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override
    public V next() {
      return delegate.next();
    }
  }
}
