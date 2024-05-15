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
import java.util.ListIterator;
import javax.annotation.Nonnull;

/**
 * A list that supports rolling back the list to a prior state.
 *
 * <p>To register a prior state you want to roll back to call `mark()`. Then use that value in a
 * subsequent call to `undo(mark)`. Every mutation operation across all undoable collections
 * increases the global mark, so a mark set in once collection is usable across all
 * UndoableCollection instances.
 *
 * @param <V> The type of the collection.
 */
public class UndoList<V> implements List<V>, Undoable {

  record UndoEntry<V>(int index, boolean set, V value, long level) {
    UndoEntry(final int index, final boolean set, final V value) {
      this(index, set, value, Undoable.incrementMarkStatic());
    }

    @Override
    public String toString() {
      return "UndoEntry{"
          + "index="
          + index
          + ", set="
          + set
          + ", value="
          + value
          + ", level="
          + level
          + '}';
    }
  }

  static class ReadOnlyListIterator<V> implements ListIterator<V> {
    ListIterator<V> iterDelegate;

    public ReadOnlyListIterator(final ListIterator<V> iterDelegate) {
      this.iterDelegate = iterDelegate;
    }

    @Override
    public boolean hasNext() {
      return iterDelegate.hasNext();
    }

    @Override
    public V next() {
      return iterDelegate.next();
    }

    @Override
    public boolean hasPrevious() {
      return iterDelegate.hasPrevious();
    }

    @Override
    public V previous() {
      return iterDelegate.previous();
    }

    @Override
    public int nextIndex() {
      return iterDelegate.nextIndex();
    }

    @Override
    public int previousIndex() {
      return iterDelegate.previousIndex();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException(
          "UndoList does not support iterator based modification");
    }

    @Override
    public void set(final V v) {
      throw new UnsupportedOperationException(
          "UndoList does not support iterator based modification");
    }

    @Override
    public void add(final V v) {
      throw new UnsupportedOperationException(
          "UndoList does not support iterator based modification");
    }
  }

  List<V> delegate;
  List<UndoList.UndoEntry<V>> undoLog = new ArrayList<>();

  /**
   * Create an UndoList backed by another List instance.
   *
   * @param delegate The List instance to use for backing storage
   */
  public UndoList(final List<V> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void undo(final long mark) {
    int pos = undoLog.size() - 1;
    while (pos >= 0 && undoLog.get(pos).level > mark) {
      final var entry = undoLog.get(pos);
      undoLog.remove(pos);
      if (entry.value() == null) {
        delegate.remove(entry.index);
      } else if (entry.set) {
        delegate.set(entry.index, entry.value());
      } else {
        delegate.add(entry.index, entry.value);
      }
      pos--;
    }
  }

  @Override
  public long lastUpdate() {
    return undoLog.isEmpty() ? 0L : undoLog.get(undoLog.size() - 1).level;
  }

  @Override
  public boolean add(final V v) {
    undoLog.add(new UndoEntry<>(delegate.size(), false, null));
    return delegate.add(v);
  }

  @SuppressWarnings({"unchecked", "SuspiciousMethodCalls"})
  @Override
  public boolean remove(final Object v) {
    int index = delegate.indexOf(v);
    if (index >= 0) {
      delegate.remove(index);
      undoLog.add(new UndoEntry<>(index, false, (V) v));
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean containsAll(final @Nonnull Collection<?> c) {
    return new HashSet<>(delegate).containsAll(c);
  }

  @Override
  public boolean addAll(final Collection<? extends V> c) {
    for (V v : c) {
      add(v);
    }
    return !c.isEmpty();
  }

  @Override
  public boolean addAll(final int index, final Collection<? extends V> c) {
    int pos = index;
    for (V v : c) {
      add(pos++, v);
    }
    return !c.isEmpty();
  }

  @Override
  public boolean removeAll(final @Nonnull Collection<?> c) {
    HashSet<?> hs = new HashSet<>(c);
    ListIterator<V> iter = delegate.listIterator();
    boolean updated = false;
    while (iter.hasNext()) {
      V v = iter.next();
      if (hs.contains(v)) {
        undoLog.add(new UndoEntry<>(iter.previousIndex(), false, v));
        iter.remove();
        updated = true;
      }
    }
    return updated;
  }

  @Override
  public boolean retainAll(final @Nonnull Collection<?> c) {
    HashSet<?> hs = new HashSet<>(c);
    ListIterator<V> iter = delegate.listIterator();
    boolean updated = false;
    while (iter.hasNext()) {
      V v = iter.next();
      if (!hs.contains(v)) {
        undoLog.add(new UndoEntry<>(iter.previousIndex(), false, v));
        iter.remove();
        updated = true;
      }
    }
    return updated;
  }

  @Override
  public void clear() {
    // store in log in reverse so when we restore them we are appending
    for (int i = delegate.size() - 1; i >= 0; i--) {
      remove(i);
    }
  }

  @Override
  public V set(final int index, final V element) {
    V oldValue = delegate.set(index, element);
    undoLog.add(new UndoEntry<>(index, true, oldValue));
    return oldValue;
  }

  @Override
  public void add(final int index, final V element) {
    delegate.add(index, element);
    undoLog.add(new UndoEntry<>(index, false, null));
  }

  @Override
  public V remove(final int index) {
    undoLog.add(new UndoEntry<>(index, false, delegate.get(index)));
    return delegate.remove(index);
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
  public boolean contains(final Object o) {
    return delegate.contains(o);
  }

  @Override
  public Iterator<V> iterator() {
    return delegate.iterator();
  }

  @Override
  public Object[] toArray() {
    return delegate.toArray();
  }

  @Override
  public <T> T[] toArray(final @Nonnull T[] a) {
    return delegate.toArray(a);
  }

  @Override
  public V get(final int index) {
    return delegate.get(index);
  }

  @Override
  public int indexOf(final Object o) {
    return delegate.indexOf(o);
  }

  @Override
  public int lastIndexOf(final Object o) {
    return delegate.lastIndexOf(o);
  }

  @Override
  public ListIterator<V> listIterator() {
    return new ReadOnlyListIterator<>(delegate.listIterator());
  }

  @Override
  public ListIterator<V> listIterator(final int index) {
    return new ReadOnlyListIterator<>(delegate.listIterator(index));
  }

  @Override
  public List<V> subList(final int fromIndex, final int toIndex) {
    return delegate.subList(fromIndex, toIndex);
  }

  @Override
  public String toString() {
    return "UndoList{" + "delegate=" + delegate + ", undoLog=" + undoLog + '}';
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof UndoList && delegate.equals(o);
  }

  @Override
  public int hashCode() {
    return delegate.hashCode() ^ 0xde1e647e;
  }
}
