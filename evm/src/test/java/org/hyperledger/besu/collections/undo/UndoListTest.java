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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UndoListTest {
  UndoList<String> subject;

  @BeforeEach
  void createUndoMap() {
    final List<String> set = new ArrayList<>();
    subject = new UndoList<>(set);
  }

  @Test
  void markMovesForward() {
    long mark = subject.mark();

    subject.add("Hello");
    assertThat(subject.mark()).isGreaterThan(mark);

    mark = subject.mark();
    subject.add("Hello");
    assertThat(subject.mark()).isGreaterThan(mark);

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
    assertThat(subject.mark()).isGreaterThan(mark);

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
    assertThat(subject).hasSize(2);

    subject.remove(0);
    assertThat(subject).hasSize(1);

    subject.remove("Hello");
    assertThat(subject).isEmpty();

    subject.undo(mark2);
    assertThat(subject).hasSize(2);

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
    subject.add(1, "qux");
    long level3 = subject.mark();
    subject.add("foo");
    long level4 = subject.mark();
    subject.add(2, "foo");
    long level5 = subject.mark();
    subject.add("foo");
    long level6 = subject.mark();
    subject.remove("foo");
    long level7 = subject.mark();
    subject.add("foo");
    long level8 = subject.mark();
    subject.set(3, "qux");
    long level9 = subject.mark();
    subject.clear();

    assertThat(subject).isEmpty();

    subject.undo(level9);
    assertThat(subject).containsExactly("qux", "foo", "baz", "qux", "foo", "foo");

    subject.undo(level8);
    assertThat(subject).containsExactly("qux", "foo", "baz", "foo", "foo", "foo");

    subject.undo(level7);
    assertThat(subject).containsExactly("qux", "foo", "baz", "foo", "foo");

    subject.undo(level6);
    assertThat(subject).containsExactly("foo", "qux", "foo", "baz", "foo", "foo");

    subject.undo(level5);
    assertThat(subject).containsExactly("foo", "qux", "foo", "baz", "foo");

    subject.undo(level4);
    assertThat(subject).containsExactly("foo", "qux", "baz", "foo");

    subject.undo(level3);
    assertThat(subject).containsExactly("foo", "qux", "baz");

    subject.undo(level2);
    assertThat(subject).containsExactly("foo", "baz");

    subject.undo(level1);
    assertThat(subject).containsExactly("foo");

    subject.undo(mark0);
    assertThat(subject).isEmpty();
  }

  @Test
  void addAll() {
    subject.add("foo");

    long mark = subject.mark();
    subject.addAll(List.of("Alpha", "Charlie"));
    assertThat(subject).containsExactly("foo", "Alpha", "Charlie");

    long mark2 = subject.mark();
    subject.addAll(2, List.of("foo", "bar"));
    assertThat(subject).containsExactly("foo", "Alpha", "foo", "bar", "Charlie");

    subject.undo(mark2);
    assertThat(subject).containsExactly("foo", "Alpha", "Charlie");

    subject.undo(mark);
    assertThat(subject).containsExactly("foo");
  }

  @Test
  void removeAll() {
    subject.add("foo");
    subject.add("bar");
    subject.add("baz");
    subject.add("qux");
    subject.add("foo");

    long mark = subject.mark();
    subject.removeAll(Set.of("bar", "baz"));

    assertThat(subject).containsExactly("foo", "qux", "foo");

    subject.undo(mark);
    assertThat(subject).containsExactly("foo", "bar", "baz", "qux", "foo");
  }

  @Test
  void retainAll() {
    subject.add("foo");
    subject.add("bar");
    subject.add("baz");
    subject.add("qux");
    subject.add("foo");

    long mark = subject.mark();
    subject.retainAll(Set.of("bar", "baz"));

    assertThat(subject).containsExactly("bar", "baz");

    subject.undo(mark);
    assertThat(subject).containsExactly("foo", "bar", "baz", "qux", "foo");
  }

  @SuppressWarnings(("java:S5838")) // direct use of contains and containsAll need to be tested here
  @Test
  void contains() {
    subject.add("one");
    long mark1 = subject.mark();
    subject.add("three");

    assertThat(subject).containsOnly("one", "three");

    subject.undo(mark1);
    assertThat(subject).containsOnly("one");

    subject.add("three");
    long mark2 = subject.mark();
    subject.remove("three");
    assertThat(subject).containsOnly("one");

    subject.undo(mark2);
    assertThat(subject).containsOnly("one", "three");

    assertThat(subject.contains("one")).isTrue();
    assertThat(subject.contains("two")).isFalse();
    assertThat(subject.containsAll(Set.of("one", "three"))).isTrue();
    assertThat(subject.containsAll(Set.of("one", "two"))).isFalse();
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

  @SuppressWarnings("JdkObsolete")
  @Test
  void equalityTests() {
    subject.add("Hello");
    long mark = subject.mark();
    subject.add("Hello");
    subject.add("Bonjour");
    subject.undo(mark);

    UndoList<String> second = new UndoList<>(new LinkedList<>());
    second.add("Hello");

    assertThat(subject).hasSameHashCodeAs(second).isEqualTo(second);
  }

  @SuppressWarnings("JdkObsolete")
  @Test
  void globalMark() {
    subject.add("Hello");
    UndoList<String> second = new UndoList<>(new LinkedList<>());

    second.add("Hello");
    // assert that a mark gathered from another undoSet works in another undoSet
    long mark = second.mark();

    subject.add("Hello");
    subject.add("Bonjour");
    subject.undo(mark);

    assertThat(subject).hasSameHashCodeAs(second).isEqualTo(second);
  }

  @Test
  void reading() {
    subject.add("foo");
    subject.add("bar");
    long mark = subject.mark();
    subject.set(1, "bif");
    subject.add(1, "baz");
    subject.add("qux");
    subject.add("foo");

    System.out.println(subject);

    assertThat(subject.get(1)).isEqualTo("baz");
    assertThat(subject.get(2)).isEqualTo("bif");
    assertThat(subject.indexOf("foo")).isZero();
    assertThat(subject.lastIndexOf("foo")).isEqualTo(4);
    assertThat(subject.lastIndexOf("bar")).isNegative();
    subject.undo(mark);
    assertThat(subject.get(1)).isEqualTo("bar");
    assertThat(subject.indexOf("foo")).isZero();
    assertThat(subject.lastIndexOf("foo")).isZero();
    assertThat(subject.lastIndexOf("bif")).isNegative();
  }

  @Test
  void sublist() {
    subject.add("foo");
    subject.add("bar");
    long mark1 = subject.mark();
    subject.add("baz");
    subject.add("qux");
    subject.add("foo");

    assertThat(subject.subList(1, 4)).containsExactly("bar", "baz", "qux");
    subject.undo(mark1);
    subject.add("one");
    subject.add("two");
    assertThat(subject.subList(1, 4)).containsExactly("bar", "one", "two");
  }

  @Test
  void listIterator() {
    subject.add("foo");
    subject.add("bar");
    long mark1 = subject.mark();
    subject.add("baz");
    subject.add("qux");
    subject.add("foo");

    ListIterator<String> listIterator = subject.listIterator();
    assertThat(listIterator.hasPrevious()).isFalse();
    listIterator.next();
    listIterator.next();
    assertThat(listIterator.next()).isEqualTo("baz");

    assertThatThrownBy(listIterator::remove)
        .isExactlyInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> listIterator.add("quux"))
        .isExactlyInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> listIterator.set("quux"))
        .isExactlyInstanceOf(UnsupportedOperationException.class);

    assertThat(listIterator.previousIndex()).isEqualTo(2);
    assertThat(listIterator.nextIndex()).isEqualTo(3);
    assertThat(listIterator.previous()).isEqualTo("baz");

    subject.undo(mark1);
    ListIterator<String> listIterator2 = subject.listIterator(1);
    assertThat(listIterator2.next()).isEqualTo("bar");
    assertThat(listIterator2.hasNext()).isFalse();
  }
}
