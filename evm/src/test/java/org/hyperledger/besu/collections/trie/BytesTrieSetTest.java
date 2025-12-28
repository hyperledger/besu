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
package org.hyperledger.besu.collections.trie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.datatypes.BytesHolder;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class BytesTrieSetTest {

  private static final BytesHolder BYTES_1234 =
      BytesHolder.createDefaultHolder(Bytes.of(1, 2, 3, 4));
  private static final BytesHolder BYTES_4321 =
      BytesHolder.createDefaultHolder(Bytes.of(4, 3, 2, 1));
  private static final BytesHolder BYTES_4567 =
      BytesHolder.createDefaultHolder(Bytes.of(4, 5, 6, 7));
  private static final BytesHolder BYTES_4568 =
      BytesHolder.createDefaultHolder(Bytes.of(4, 5, 6, 8));
  private static final BytesHolder BYTES_4556 =
      BytesHolder.createDefaultHolder(Bytes.of(4, 5, 5, 6));
  private static final BytesHolder BYTES_123 = BytesHolder.createDefaultHolder(Bytes.of(1, 2, 3));

  @Test
  void testInserts() {
    BytesTrieSet<BytesHolder> trieSet = new BytesTrieSet<>(4);
    assertThat(trieSet).isEmpty();
    System.out.println(trieSet);

    assertThat(trieSet.add(BYTES_1234)).isTrue();
    assertThat(trieSet).hasSize(1);

    assertThat(trieSet.add(BYTES_1234)).isFalse();
    assertThat(trieSet).hasSize(1);

    assertThat(trieSet.add(BYTES_4321)).isTrue();
    assertThat(trieSet).hasSize(2);

    assertThat(trieSet.add(BYTES_4567)).isTrue();
    assertThat(trieSet).hasSize(3);

    assertThat(trieSet.add(BYTES_4567)).isFalse();
    assertThat(trieSet).hasSize(3);

    System.out.println(trieSet);
  }

  @Test
  void testRemoves() {
    BytesTrieSet<BytesHolder> trieSet = new BytesTrieSet<>(4);

    assertThat(trieSet.remove(BYTES_1234)).isFalse();

    trieSet.add(BYTES_1234);
    assertThat(trieSet.remove(BYTES_4321)).isFalse();
    assertThat(trieSet.remove(BYTES_1234)).isTrue();
    assertThat(trieSet).isEmpty();

    trieSet.add(BYTES_1234);
    trieSet.add(BYTES_4321);
    assertThat(trieSet.remove(BYTES_4567)).isFalse();
    assertThat(trieSet.remove(BYTES_4568)).isFalse();

    trieSet.add(BYTES_4567);
    trieSet.add(BYTES_4568);
    assertThat(trieSet).hasSize(4);

    assertThat(trieSet.remove(BYTES_4556)).isFalse();
    assertThat(trieSet.remove(BYTES_4568)).isTrue();
    assertThat(trieSet.remove(BYTES_4568)).isFalse();
    assertThat(trieSet).hasSize(3);
    assertThat(trieSet.remove(BYTES_4567)).isTrue();
    assertThat(trieSet).hasSize(2);

    assertThat(trieSet.remove(BYTES_4321)).isTrue();
    assertThat(trieSet).hasSize(1);

    assertThat(trieSet.remove(BYTES_1234)).isTrue();
    assertThat(trieSet).isEmpty();
  }

  @Test
  @SuppressWarnings(
      "squid:S5838") // contains and doesNotContains uses iterables, not the contains method
  void testContains() {
    BytesTrieSet<BytesHolder> trieSet = new BytesTrieSet<>(4);

    assertThat(trieSet.contains(BYTES_1234)).isFalse();

    trieSet.add(BYTES_1234);
    assertThat(trieSet.contains(BYTES_4321)).isFalse();
    assertThat(trieSet.contains(BYTES_1234)).isTrue();
    assertThat(trieSet).hasSize(1);

    trieSet.add(BYTES_1234);
    trieSet.add(BYTES_4321);
    assertThat(trieSet.contains(BYTES_4567)).isFalse();
    assertThat(trieSet.contains(BYTES_4568)).isFalse();

    trieSet.add(BYTES_4567);
    trieSet.add(BYTES_4568);
    assertThat(trieSet).hasSize(4);

    assertThat(trieSet.contains(BYTES_4556)).isFalse();
    assertThat(trieSet.contains(BYTES_4568)).isTrue();
    trieSet.remove(BYTES_4568);
    assertThat(trieSet).hasSize(3);
    assertThat(trieSet.contains(BYTES_4567)).isTrue();
    trieSet.remove(BYTES_4567);
    assertThat(trieSet).hasSize(2);
    assertThat(trieSet.contains(BYTES_4567)).isFalse();

    assertThat(trieSet.contains(BYTES_4321)).isTrue();
    trieSet.remove(BYTES_4321);
    assertThat(trieSet.contains(BYTES_4321)).isFalse();
    assertThat(trieSet).hasSize(1);

    assertThat(trieSet.contains(BYTES_1234)).isTrue();
    trieSet.remove(BYTES_1234);
    assertThat(trieSet.contains(BYTES_4321)).isFalse();

    assertThat(trieSet).isEmpty();
  }

  @Test
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  void checkLengthAdd() {
    BytesTrieSet<BytesHolder> trieSet = new BytesTrieSet<>(4);
    assertThatThrownBy(() -> trieSet.add(BYTES_123)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  void checkLengthRemove() {
    BytesTrieSet<BytesHolder> trieSet = new BytesTrieSet<>(4);
    assertThatThrownBy(() -> trieSet.remove(BYTES_123))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("4");
  }

  @Test
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  void checkLengthContains() {
    BytesTrieSet<BytesHolder> trieSet = new BytesTrieSet<>(4);
    assertThatThrownBy(() -> trieSet.contains(BYTES_123))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("4");
  }

  @Test
  @SuppressWarnings({
    "MismatchedQueryAndUpdateOfCollection",
    "SuspiciousMethodCalls",
    "CollectionIncompatibleType"
  })
  void checkWrongClassRemove() {
    BytesTrieSet<BytesHolder> trieSet = new BytesTrieSet<>(4);
    assertThatThrownBy(() -> trieSet.remove(this))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Bytes");
  }

  @Test
  @SuppressWarnings({
    "MismatchedQueryAndUpdateOfCollection",
    "SuspiciousMethodCalls",
    "CollectionIncompatibleType"
  })
  void checkWrongClassContains() {
    BytesTrieSet<BytesHolder> trieSet = new BytesTrieSet<>(4);
    assertThatThrownBy(() -> trieSet.contains(this))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Bytes");
  }
}
