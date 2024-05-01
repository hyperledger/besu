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

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UndoMapTest {
  UndoMap<String, String> subject;

  @BeforeEach
  void createUndoMap() {
    final Map<String, String> map = new HashMap<>();
    subject = new UndoMap<>(map);
  }

  @Test
  void markMovesForward() {
    long mark = subject.mark();

    subject.put("Hello", "World");
    assertThat(subject.mark()).isGreaterThan(mark);

    mark = subject.mark();
    subject.put("Hello", "There");
    assertThat(subject.mark()).isGreaterThan(mark);
  }

  @Test
  void markOnlyMovesOnWrite() {
    long mark;
    subject.put("Hello", "World");

    mark = subject.mark();
    subject.put("Hi", "There");
    assertThat(subject.mark()).isGreaterThan(mark);

    mark = subject.mark();
    subject.undo(mark);
    assertThat(subject.mark()).isEqualTo(mark);

    mark = subject.mark();
    subject.put("Hello", "Again");
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

    subject.put("Bonjour", "Hi");

    mark = subject.mark();
    // failed specified value remove does not advance mark
    subject.remove("Bonjour", "Hello");
    assertThat(subject.mark()).isEqualTo(mark);

    mark = subject.mark();
    // non-changing undo does not advance mark
    subject.undo(mark);
    assertThat(subject.mark()).isEqualTo(mark);

    mark = subject.mark();
    // modifying change advances mark
    subject.remove("Bonjour");
    subject.undo(mark);
    assertThat(subject.mark()).isGreaterThan(mark);

    mark = subject.mark();
    subject.clear();
    assertThat(subject.mark()).isGreaterThan(mark);
  }

  @Test
  void sizeAdjustsWithUndo() {
    assertThat(subject).isEmpty();

    subject.put("Hello", "World");
    long mark1 = subject.mark();
    assertThat(subject).hasSize(1);

    subject.put("Hello", "There");
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
    subject.put("foo", "bar");
    long level1 = subject.mark();
    subject.put("baz", "bif");
    long level2 = subject.mark();
    subject.put("qux", "quux");
    long level3 = subject.mark();
    subject.put("foo", "FEE");
    long level4 = subject.mark();
    subject.put("foo", "bar");
    long level5 = subject.mark();
    subject.put("foo", "FIE");
    long level6 = subject.mark();
    subject.remove("foo");
    long level7 = subject.mark();
    subject.put("foo", "FUM");
    long level8 = subject.mark();
    subject.put("qux", "quuux");
    long level9 = subject.mark();
    subject.clear();

    assertThat(subject).isEmpty();

    subject.undo(level9);
    assertThat(subject)
        .containsOnly(Map.entry("foo", "FUM"), Map.entry("baz", "bif"), Map.entry("qux", "quuux"));

    subject.undo(level8);
    assertThat(subject)
        .containsOnly(Map.entry("foo", "FUM"), Map.entry("baz", "bif"), Map.entry("qux", "quux"));

    subject.undo(level7);
    assertThat(subject).containsOnly(Map.entry("baz", "bif"), Map.entry("qux", "quux"));

    subject.undo(level6);
    assertThat(subject)
        .containsOnly(Map.entry("foo", "FIE"), Map.entry("baz", "bif"), Map.entry("qux", "quux"));

    subject.undo(level5);
    assertThat(subject)
        .containsOnly(Map.entry("foo", "bar"), Map.entry("baz", "bif"), Map.entry("qux", "quux"));

    subject.undo(level4);
    assertThat(subject)
        .containsOnly(Map.entry("foo", "FEE"), Map.entry("baz", "bif"), Map.entry("qux", "quux"));

    subject.undo(level3);
    assertThat(subject)
        .containsOnly(Map.entry("foo", "bar"), Map.entry("baz", "bif"), Map.entry("qux", "quux"));

    subject.undo(level2);
    assertThat(subject).containsOnly(Map.entry("foo", "bar"), Map.entry("baz", "bif"));

    subject.undo(level1);
    assertThat(subject).containsOnly(Map.entry("foo", "bar"));

    subject.undo(mark0);
    assertThat(subject).isEmpty();
  }

  @Test
  void putAll() {
    subject.put("foo", "bar");

    long mark = subject.mark();
    subject.putAll(Map.of("Alpha", "Bravo", "Charlie", "Delta"));
    assertThat(subject)
        .containsOnly(
            Map.entry("foo", "bar"), Map.entry("Alpha", "Bravo"), Map.entry("Charlie", "Delta"));

    subject.undo(mark);
    assertThat(subject).containsOnly(Map.entry("foo", "bar"));

    mark = subject.mark();
    subject.putAll(Map.of("foo", "foo", "bar", "bar"));
    assertThat(subject).containsOnly(Map.entry("foo", "foo"), Map.entry("bar", "bar"));

    subject.undo(mark);
    assertThat(subject).containsOnly(Map.entry("foo", "bar"));
  }

  @Test
  void contains() {
    subject.put("one", "two");
    long mark1 = subject.mark();
    subject.put("three", "four");

    assertThat(subject).containsKey("three").containsValue("four").containsOnlyKeys("one", "three");
    assertThat(subject.values()).containsOnly("two", "four");

    subject.undo(mark1);
    assertThat(subject)
        .doesNotContainKey("three")
        .doesNotContainValue("four")
        .containsOnlyKeys("one");
    assertThat(subject.values()).containsOnly("two");

    subject.put("three", "four");
    long mark2 = subject.mark();
    subject.remove("three");
    assertThat(subject)
        .doesNotContainKey("three")
        .doesNotContainValue("four")
        .containsOnlyKeys("one");
    assertThat(subject.values()).containsOnly("two");

    subject.undo(mark2);
    assertThat(subject).containsKey("three").containsValue("four").containsOnlyKeys("one", "three");
    assertThat(subject.values()).containsOnly("two", "four");
  }

  @Test
  void equalityTests() {
    subject.put("Hello", "There");
    long mark = subject.mark();
    subject.put("Hello", "World");
    subject.put("Bonjour", "Hi");
    subject.undo(mark);

    UndoMap<String, String> second = new UndoMap<>(new TreeMap<>());
    second.put("Hello", "There");

    assertThat(subject.keySet()).isEqualTo(second.keySet());
    assertThat(subject).hasSameHashCodeAs(second).isEqualTo(second);
  }

  @Test
  void globalMark() {
    subject.put("Hello", "There");
    UndoMap<String, String> second = new UndoMap<>(new TreeMap<>());

    second.put("Hello", "There");
    // assert that a mark gathered from another undomap works in another undomap
    long mark = second.mark();

    subject.put("Hello", "World");
    subject.put("Bonjour", "Hi");
    subject.undo(mark);

    assertThat(subject.keySet()).isEqualTo(second.keySet());
    assertThat(subject).hasSameHashCodeAs(second).isEqualTo(second);
  }
}
