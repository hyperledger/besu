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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UndoSetTest {
  UndoSet<String> subject;

  @BeforeEach
  void createUndoSet() {
    final Set<String> set = new HashSet<>();
    subject = new UndoSet<>(set);
  }

  @Test
  void markMovesForward() {
    long mark = subject.mark();

    subject.add("Hello");
    assertThat(subject.mark()).isGreaterThan(mark);

    mark = subject.mark();
    subject.add("Hello");
    assertThat(subject.mark()).isEqualTo(mark);

    mark = subject.mark();
    subject.add("There");
    assertThat(subject.mark()).isGreaterThan(mark);
  }

  @Test
  void markOnlyMovesOnWrite() {
    long mark;
    subject.add("Hello");

    mark = subject.mark();
    subject.add("Hi");
    assertThat(subject.mark()).isGreaterThan(mark);

    mark = subject.mark();
    subject.undo(mark);
    assertThat(subject.mark()).isEqualTo(mark);

    mark = subject.mark();
    subject.add("Hello");
    assertThat(subject.mark()).isEqualTo(mark);

    mark = subject.mark();
    subject.undo(mark);
    assertThat(subject.mark()).isEqualTo(mark);

    mark = subject.mark();
    // no actions
    assertThat(subject.mark()).isEqualTo(mark);

    mark = subject.mark();
    subject.remove("Hi");
    assertThat(subject.mark()).isGreaterThan(mark);

    mark = subject.mark();
    // non-changing undo does not advance mark
    subject.undo(mark);
    assertThat(subject.mark()).isEqualTo(mark);

    mark = subject.mark();
    // non-existent remove doesn't advance mark
    subject.remove("Bonjour");
    assertThat(subject.mark()).isEqualTo(mark);

    mark = subject.mark();
    subject.clear();
    assertThat(subject.mark()).isGreaterThan(mark);
  }

  @Test
  void sizeAdjustsWithUndo() {
    assertThat(subject).isEmpty();

    subject.add("Hello");
    long mark1 = subject.mark();
    assertThat(subject).hasSize(1);

    subject.add("Hello");
    long mark2 = subject.mark();
    assertThat(subject).hasSize(1);

    subject.remove("Hello");
    assertThat(subject).isEmpty();

    subject.undo(mark2);
    assertThat(subject).hasSize(1);

    subject.undo(mark1);
    assertThat(subject).hasSize(1);

    subject.undo(0);
    assertThat(subject).isEmpty();
  }

  @Test
  void checkUndoContents() {
    long mark0 = subject.mark();
    subject.add("foo");
    long level1 = subject.mark();
    subject.add("baz");
    long level2 = subject.mark();
    subject.add("qux");
    long level3 = subject.mark();
    subject.add("foo");
    long level4 = subject.mark();
    subject.add("foo");
    long level5 = subject.mark();
    subject.add("foo");
    long level6 = subject.mark();
    subject.remove("foo");
    long level7 = subject.mark();
    subject.add("foo");
    long level8 = subject.mark();
    subject.add("qux");
    long level9 = subject.mark();
    subject.clear();

    assertThat(subject).isEmpty();

    subject.undo(level9);
    assertThat(subject).containsOnly("foo", "baz", "qux");

    subject.undo(level8);
    assertThat(subject).containsOnly("foo", "baz", "qux");

    subject.undo(level7);
    assertThat(subject).containsOnly("baz", "qux");

    subject.undo(level6);
    assertThat(subject).containsOnly("foo", "baz", "qux");

    subject.undo(level5);
    assertThat(subject).containsOnly("foo", "baz", "qux");

    subject.undo(level4);
    assertThat(subject).containsOnly("foo", "baz", "qux");

    subject.undo(level3);
    assertThat(subject).containsOnly("foo", "baz", "qux");

    subject.undo(level2);
    assertThat(subject).containsOnly("foo", "baz");

    subject.undo(level1);
    assertThat(subject).containsOnly("foo");

    subject.undo(mark0);
    assertThat(subject).isEmpty();
  }

  @Test
  void addAll() {
    subject.add("foo");

    long mark = subject.mark();
    subject.addAll(Set.of("Alpha", "Charlie"));
    assertThat(subject).containsOnly("foo", "Alpha", "Charlie");

    subject.undo(mark);
    assertThat(subject).containsOnly("foo");

    mark = subject.mark();
    subject.addAll(Set.of("foo", "bar"));
    assertThat(subject).containsOnly("foo", "bar");

    subject.undo(mark);
    assertThat(subject).containsOnly("foo");
  }

  @Test
  void removeAll() {
    subject.add("foo");
    subject.add("bar");
    subject.add("baz");
    subject.add("qux");

    long mark = subject.mark();
    subject.removeAll(Set.of("bar", "baz"));

    assertThat(subject).containsOnly("foo", "qux");

    subject.undo(mark);
    assertThat(subject).containsOnly("foo", "bar", "baz", "qux");
  }

  @Test
  void retainAll() {
    subject.add("foo");
    subject.add("bar");
    subject.add("baz");
    subject.add("qux");

    long mark = subject.mark();
    subject.retainAll(Set.of("bar", "baz"));

    assertThat(subject).containsOnly("bar", "baz");

    subject.undo(mark);
    assertThat(subject).containsOnly("foo", "bar", "baz", "qux");
  }

  @SuppressWarnings(("java:S5838")) // direct use of contains and containsAll need to be tested here
  @Test
  void contains() {
    subject.add("one");
    long mark1 = subject.mark();
    subject.add("three");

    assertThat(subject).containsOnly("one", "three");

    assertThat(subject.contains("one")).isTrue();
    assertThat(subject.contains("two")).isFalse();
    assertThat(subject.containsAll(Set.of("one", "three"))).isTrue();
    assertThat(subject.containsAll(Set.of("one", "two"))).isFalse();

    subject.undo(mark1);
    assertThat(subject).containsOnly("one");

    subject.add("three");
    long mark2 = subject.mark();
    subject.remove("three");
    assertThat(subject).containsOnly("one");

    subject.undo(mark2);
    assertThat(subject).containsOnly("one", "three");
  }

  @Test
  void toArray() {
    subject.add("foo");
    subject.add("bar");
    long mark = subject.mark();
    subject.add("baz");
    subject.add("qux");

    String[] generated = subject.toArray(String[]::new);
    //noinspection RedundantCast
    assertThat(subject.toArray()).containsExactlyInAnyOrder((Object[]) generated);

    subject.undo(mark);

    assertThat(subject.toArray(new String[2]))
        .containsExactlyInAnyOrder(subject.toArray(new String[0]));
  }

  @Test
  void equalityTests() {
    subject.add("Hello");
    long mark = subject.mark();
    subject.add("Hello");
    subject.add("Bonjour");
    subject.undo(mark);

    UndoSet<String> second = new UndoSet<>(new TreeSet<>());
    second.add("Hello");

    assertThat(subject).hasSameHashCodeAs(second).isEqualTo(second);
  }

  @Test
  void globalMark() {
    subject.add("Hello");
    UndoSet<String> second = new UndoSet<>(new TreeSet<>());

    second.add("Hello");
    // assert that a mark gathered from another undoSet works in another undoSet
    long mark = second.mark();

    subject.add("Hello");
    subject.add("Bonjour");
    subject.undo(mark);

    assertThat(subject).hasSameHashCodeAs(second).isEqualTo(second);
  }
}
