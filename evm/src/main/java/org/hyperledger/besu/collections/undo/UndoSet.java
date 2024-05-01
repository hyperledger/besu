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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * A set that supports rolling back the set to a prior state.
 *
 * <p>To register a prior state you want to roll back to call `mark()`. Then use that value in a
 * subsequent call to `undo(mark)`. Every mutation operation across all undoable collections
 * increases the global mark, so a mark set in once collection is usable across all
 * UndoableCollection instances.
 *
 * @param <V> The type of the collection.
 */
public class UndoSet<V> implements Set<V>, Undoable {

  record UndoEntry<V>(V value, boolean add, long level) {
    static <V> UndoSet.UndoEntry<V> add(final V value) {
      return new UndoEntry<>(value, true, Undoable.incrementMarkStatic());
    }

    static <V> UndoSet.UndoEntry<V> remove(final V value) {
      return new UndoEntry<>(value, false, Undoable.incrementMarkStatic());
    }
  }

  Set<V> delegate;
  List<UndoEntry<V>> undoLog;

  /**
   * Create an UndoSet backed by another Set instance.
   *
   * @param delegate The Set instance to use for backing storage
   * @param <V> The type of the collection.
   * @return an unduable set
   */
  public static <V> UndoSet<V> of(final Set<V> delegate) {
    return new UndoSet<>(delegate);
  }

  UndoSet(final Set<V> delegate) {
    this.delegate = delegate;
    undoLog = new ArrayList<>();
  }

  @Override
  public void undo(final long mark) {
    int pos = undoLog.size() - 1;
    while (pos >= 0 && undoLog.get(pos).level > mark) {
      final var entry = undoLog.get(pos);
      if (entry.add) {
        delegate.remove(entry.value());
      } else {
        delegate.add(entry.value());
      }
      undoLog.remove(pos);
      pos--;
    }
  }

  @Override
  public long lastUpdate() {
    return undoLog.isEmpty() ? 0L : undoLog.get(undoLog.size() - 1).level;
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public boolean contains(final Object key) {
    return delegate.contains(key);
  }

  @Override
  public boolean add(final V key) {
    final boolean added = delegate.add(key);
    if (added) {
      undoLog.add(UndoEntry.add(key));
    }
    return added;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean remove(final Object key) {
    final boolean removed = delegate.remove(key);
    if (removed) {
      undoLog.add(UndoEntry.remove((V) key));
    }
    return removed;
  }

  @Override
  public boolean addAll(@Nonnull final Collection<? extends V> m) {
    boolean added = false;
    for (V v : m) {
      // don't use short circuit, we need to evaluate all entries
      // we also need undo entries for each added entry
      added &= add(v);
    }
    return added;
  }

  @Override
  public boolean removeAll(@Nonnull final Collection<?> c) {
    boolean removed = false;
    for (Object v : c) {
      // don't use short circuit, we need to evaluate all entries
      // we also need undo entries for each removed entry
      removed &= remove(v);
    }
    return removed;
  }

  @Override
  public boolean retainAll(@Nonnull final Collection<?> c) {
    boolean removed = false;
    HashSet<?> hashed = new HashSet<>(c);
    Iterator<V> iter = delegate.iterator();
    while (iter.hasNext()) {
      V v = iter.next();
      if (!hashed.contains(v)) {
        removed = true;
        undoLog.add(UndoEntry.remove(v));
        iter.remove();
      }
    }
    return removed;
  }

  @Override
  public void clear() {
    delegate.forEach(v -> undoLog.add(UndoEntry.remove(v)));
    delegate.clear();
  }

  @Nonnull
  @Override
  public Iterator<V> iterator() {
    return new ReadOnlyIterator<>(delegate.iterator());
  }

  @Nonnull
  @Override
  public Object[] toArray() {
    return delegate.toArray();
  }

  @Nonnull
  @Override
  public <T> T[] toArray(@Nonnull final T[] a) {
    return delegate.toArray(a);
  }

  @Override
  public boolean containsAll(@Nonnull final Collection<?> c) {
    return delegate.containsAll(c);
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof UndoSet && delegate.equals(o);
  }

  @Override
  public int hashCode() {
    return delegate.hashCode() ^ 0xde1e647e;
  }
}
