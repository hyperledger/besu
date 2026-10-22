/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.snap2.state;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.trie.RangeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class KeyRangeSetTest {

  private static Bytes32 key(final long value) {
    return Bytes32.fromHexStringLenient(Long.toHexString(value));
  }

  @Test
  void stepsToTheNextKey() {
    assertThat(KeyRangeSet.next(key(0xff))).contains(key(0x100));
    assertThat(KeyRangeSet.next(RangeManager.MAX_RANGE)).isEmpty();
    // a plain key, hashed like the keys of the ranges
    assertThat(KeyRangeSet.next(key(1)).orElseThrow().hashCode()).isEqualTo(key(2).hashCode());
  }

  @Test
  void measuresTheShareOfTheKeySpace() {
    assertThat(KeyRangeSet.fraction(RangeManager.MIN_RANGE, RangeManager.MAX_RANGE)).isEqualTo(1.0);
    assertThat(KeyRangeSet.fraction(RangeManager.MIN_RANGE, key(0))).isLessThan(1e-70);
  }

  @Test
  void mergesTheRangesOnBothSidesOfACarry() {
    final KeyRangeSet set = new KeyRangeSet();
    set.add(key(0x10), key(0xff));
    set.add(key(0x100), key(0x1ff));

    assertThat(set.snapshot()).containsExactly(entry(key(0x10), key(0x1ff)));
  }

  @Test
  void mergesAdjacentAndOverlappingRanges() {
    final KeyRangeSet set = new KeyRangeSet();
    set.add(key(10), key(20));
    set.add(key(21), key(30));
    set.add(key(25), key(40));
    set.add(key(50), key(60));

    assertThat(set.snapshot()).containsExactly(entry(key(10), key(40)), entry(key(50), key(60)));
    assertThat(set.contains(key(10))).isTrue();
    assertThat(set.contains(key(40))).isTrue();
    assertThat(set.contains(key(41))).isFalse();
    assertThat(set.contains(key(9))).isFalse();

    set.add(key(41), key(49));
    assertThat(set.snapshot()).containsExactly(entry(key(10), key(60)));
  }

  @Test
  void detectsTheFullKeySpace() {
    final KeyRangeSet set = new KeyRangeSet();
    final List<Map.Entry<Bytes32, Bytes32>> parts =
        new ArrayList<>(
            RangeManager.generateRanges(RangeManager.MIN_RANGE, RangeManager.MAX_RANGE, 16)
                .entrySet());
    assertThat(parts).hasSize(16);
    for (final Map.Entry<Bytes32, Bytes32> part : parts.reversed()) {
      assertThat(set.isFull()).isFalse();
      set.add(part.getKey(), part.getValue());
    }
    assertThat(set.isFull()).isTrue();
    assertThat(set.coverage()).isEqualTo(1.0);
  }
}
