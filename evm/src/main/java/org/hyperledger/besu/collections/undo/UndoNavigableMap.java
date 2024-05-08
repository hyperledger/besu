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

import java.util.Comparator;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.SortedMap;

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
public class UndoNavigableMap<K, V> extends UndoMap<K, V> implements NavigableMap<K, V> {

  /**
   * Create an UndoMap backed by another Map instance.
   *
   * @param delegate The Map instance to use for backing storage
   */
  public UndoNavigableMap(final NavigableMap<K, V> delegate) {
    super(delegate);
  }

  /**
   * Create an undo navigable map backed by a specific map.
   *
   * @param map The map storing the current state
   * @return an undoable map
   * @param <K> the key type
   * @param <V> the value type
   */
  public static <K, V> UndoNavigableMap<K, V> of(final NavigableMap<K, V> map) {
    return new UndoNavigableMap<>(map);
  }

  @Override
  public Comparator<? super K> comparator() {
    return ((NavigableMap<K, V>) delegate).comparator();
  }

  @Override
  public SortedMap<K, V> subMap(final K fromKey, final K toKey) {
    return ((NavigableMap<K, V>) delegate).subMap(fromKey, toKey);
  }

  @Override
  public SortedMap<K, V> headMap(final K toKey) {
    return ((NavigableMap<K, V>) delegate).headMap(toKey);
  }

  @Override
  public SortedMap<K, V> tailMap(final K fromKey) {
    return ((NavigableMap<K, V>) delegate).tailMap(fromKey);
  }

  @Override
  public K firstKey() {
    return ((NavigableMap<K, V>) delegate).firstKey();
  }

  @Override
  public K lastKey() {
    return ((NavigableMap<K, V>) delegate).lastKey();
  }

  @Override
  public Entry<K, V> lowerEntry(final K key) {
    return ((NavigableMap<K, V>) delegate).lowerEntry(key);
  }

  @Override
  public K lowerKey(final K key) {
    return ((NavigableMap<K, V>) delegate).lowerKey(key);
  }

  @Override
  public Entry<K, V> floorEntry(final K key) {
    return ((NavigableMap<K, V>) delegate).floorEntry(key);
  }

  @Override
  public K floorKey(final K key) {
    return ((NavigableMap<K, V>) delegate).floorKey(key);
  }

  @Override
  public Entry<K, V> ceilingEntry(final K key) {
    return ((NavigableMap<K, V>) delegate).ceilingEntry(key);
  }

  @Override
  public K ceilingKey(final K key) {
    return ((NavigableMap<K, V>) delegate).ceilingKey(key);
  }

  @Override
  public Entry<K, V> higherEntry(final K key) {
    return ((NavigableMap<K, V>) delegate).higherEntry(key);
  }

  @Override
  public K higherKey(final K key) {
    return ((NavigableMap<K, V>) delegate).higherKey(key);
  }

  @Override
  public Entry<K, V> firstEntry() {
    return ((NavigableMap<K, V>) delegate).firstEntry();
  }

  @Override
  public Entry<K, V> lastEntry() {
    return ((NavigableMap<K, V>) delegate).lastEntry();
  }

  @Override
  public Entry<K, V> pollFirstEntry() {
    return ((NavigableMap<K, V>) delegate).pollFirstEntry();
  }

  @Override
  public Entry<K, V> pollLastEntry() {
    return ((NavigableMap<K, V>) delegate).pollLastEntry();
  }

  @Override
  public NavigableMap<K, V> descendingMap() {
    return ((NavigableMap<K, V>) delegate).descendingMap();
  }

  @Override
  public NavigableSet<K> navigableKeySet() {
    return ((NavigableMap<K, V>) delegate).navigableKeySet();
  }

  @Override
  public NavigableSet<K> descendingKeySet() {
    return ((NavigableMap<K, V>) delegate).descendingKeySet();
  }

  @Override
  public NavigableMap<K, V> subMap(
      final K fromKey, final boolean fromInclusive, final K toKey, final boolean toInclusive) {
    return ((NavigableMap<K, V>) delegate).subMap(fromKey, fromInclusive, toKey, toInclusive);
  }

  @Override
  public NavigableMap<K, V> headMap(final K toKey, final boolean inclusive) {
    return ((NavigableMap<K, V>) delegate).headMap(toKey, inclusive);
  }

  @Override
  public NavigableMap<K, V> tailMap(final K fromKey, final boolean inclusive) {
    return ((NavigableMap<K, V>) delegate).tailMap(fromKey, inclusive);
  }

  @Override
  public String toString() {
    return "UndoSortedSet{" + "delegate=" + delegate + ", undoLog=" + undoLog + '}';
  }
}
