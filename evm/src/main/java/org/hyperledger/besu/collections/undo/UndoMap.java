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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * A map that supports rolling back the map to a prior state.
 *
 * <p>To register a prior state you want to roll back to call `mark()`. Then use that value in a
 * subsequent call to `undo(mark)`. Every mutation operation across all undoable collections
 * increases the global mark, so a mark set in once collection is usable across all
 * UndoableCollection instances.
 *
 * @param <K> The type of the keys maintained by this map.
 * @param <V> The type of mapped values.
 */
public class UndoMap<K, V> implements Map<K, V>, Undoable {

  record UndoEntry<K, V>(K key, V value, long level) {
    UndoEntry(final K key, final V value) {
      this(key, value, Undoable.incrementMarkStatic());
    }
  }

  Map<K, V> delegate;
  List<UndoEntry<K, V>> undoLog;

  /**
   * Create an UndoMap backed by another Map instance.
   *
   * @param delegate The Map instance to use for backing storage
   */
  public UndoMap(final Map<K, V> delegate) {
    this.delegate = delegate;
    undoLog = new ArrayList<>();
  }

  @Override
  public void undo(final long mark) {
    int pos = undoLog.size() - 1;
    while (pos >= 0 && undoLog.get(pos).level > mark) {
      final var entry = undoLog.get(pos);
      undoLog.remove(pos);
      if (entry.value() == null) {
        delegate.remove(entry.key());
      } else {
        delegate.put(entry.key(), entry.value());
      }
      pos--;
    }
  }

  @Override
  public long lastUpdate() {
    return undoLog.isEmpty() ? 0L : undoLog.get(undoLog.size() - 1).level;
  }

  /**
   * Has the map been changed
   *
   * @return true if there are any undo entries in the log
   */
  public boolean updated() {
    return !undoLog.isEmpty();
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
  public boolean containsKey(final Object key) {
    return delegate.containsKey(key);
  }

  @Override
  public boolean containsValue(final Object value) {
    return delegate.containsValue(value);
  }

  @Override
  public V get(final Object key) {
    return delegate.get(key);
  }

  @Override
  public V put(final @Nonnull K key, final @Nonnull V value) {
    Objects.requireNonNull(value);
    final V oldValue = delegate.put(key, value);
    if (!value.equals(oldValue)) {
      undoLog.add(new UndoEntry<>(key, oldValue));
    }
    return oldValue;
  }

  @SuppressWarnings("unchecked")
  @Override
  public V remove(final Object key) {
    final V oldValue = delegate.remove(key);
    if (oldValue != null) {
      undoLog.add(new UndoEntry<>((K) key, oldValue));
    }
    return oldValue;
  }

  @Override
  public void putAll(@Nonnull final Map<? extends K, ? extends V> m) {
    m.forEach(this::put);
  }

  @Override
  public void clear() {
    delegate.forEach((k, v) -> undoLog.add(new UndoEntry<>(k, v)));
    delegate.clear();
  }

  @Nonnull
  @Override
  public Set<K> keySet() {
    return Collections.unmodifiableSet(delegate.keySet());
  }

  @Nonnull
  @Override
  public Collection<V> values() {
    return Collections.unmodifiableCollection(delegate.values());
  }

  @Nonnull
  @Override
  public Set<Entry<K, V>> entrySet() {
    return Collections.unmodifiableSet(delegate.entrySet());
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof UndoMap && delegate.equals(o);
  }

  @Override
  public int hashCode() {
    return delegate.hashCode() ^ 0xde1e647e;
  }
}
