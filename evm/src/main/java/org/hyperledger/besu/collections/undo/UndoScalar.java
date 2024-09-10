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
import java.util.List;
import java.util.Objects;

/**
 * An undoable value that tracks the value across time.
 *
 * @param <T> The type of the scaler.
 */
public class UndoScalar<T> implements Undoable {
  record UndoEntry<T>(T value, long level) {
    UndoEntry(final T value) {
      this(value, Undoable.incrementMarkStatic());
    }
  }

  T value;
  final List<UndoEntry<T>> undoLog;

  /**
   * Create an undoable scalar with an initial value
   *
   * @param value the initial value
   * @return the undoable scalar
   * @param <T> the type of the scalar
   */
  public static <T> UndoScalar<T> of(final T value) {
    return new UndoScalar<>(value);
  }

  /**
   * Create an undo scalar with an initial value
   *
   * @param value the initial value
   */
  public UndoScalar(final T value) {
    undoLog = new ArrayList<>();
    this.value = value;
  }

  @Override
  public long lastUpdate() {
    return undoLog.isEmpty() ? 0L : undoLog.get(undoLog.size() - 1).level;
  }

  /**
   * Has this scalar had any change since the initial value
   *
   * @return true if there are any changes to undo
   */
  public boolean updated() {
    return !undoLog.isEmpty();
  }

  /**
   * Get the current value of the scalar.
   *
   * @return the current value
   */
  public T get() {
    return value;
  }

  /**
   * Set a new value in the scalar.
   *
   * @param value new value
   */
  public void set(final T value) {
    if (!Objects.equals(this.value, value)) {
      undoLog.add(new UndoEntry<>(this.value));
      this.value = value;
    }
  }

  @Override
  public void undo(final long mark) {
    if (undoLog.isEmpty()) {
      return;
    }
    int pos = undoLog.size() - 1;
    while (pos >= 0) {
      UndoEntry<T> entry = undoLog.get(pos);
      if (entry.level <= mark) {
        return;
      }
      value = entry.value;
      undoLog.remove(pos);
      pos--;
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof UndoScalar<?> that)) return false;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return "UndoScalar{" + "value=" + value + ", undoLog=" + undoLog + '}';
  }
}
