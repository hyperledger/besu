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
package org.hyperledger.besu.ethereum.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.chain.RecentBlocksRlpCache.ENTRY_OVERHEAD;

import org.hyperledger.besu.datatypes.Hash;

import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class RecentBlocksRlpCacheTest {

  @Test
  void evictsOldestBlockWhenBlockLimitIsExceeded() {
    final RecentBlocksRlpCache cache = new RecentBlocksRlpCache(3, Long.MAX_VALUE);

    for (int i = 0; i < 4; i++) {
      cache.put(hash(i), rlp(i, 10));
    }

    assertThat(cache.get(hash(0))).isEmpty();
    for (int i = 1; i < 4; i++) {
      assertThat(cache.get(hash(i))).contains(rlp(i, 10));
    }
    assertThat(cache.evictionCount()).isEqualTo(1);
    assertThat(cache.weight()).isEqualTo(3 * (10 + ENTRY_OVERHEAD));
  }

  @Test
  void evictsOldestBlocksUntilUnderByteLimit() {
    final RecentBlocksRlpCache cache = new RecentBlocksRlpCache(10, 3 * (100 + ENTRY_OVERHEAD));
    for (int i = 0; i < 3; i++) {
      cache.put(hash(i), rlp(i, 100));
    }
    assertThat(cache.evictionCount()).isZero();

    cache.put(hash(3), rlp(3, 250));

    assertThat(cache.get(hash(0))).isEmpty();
    assertThat(cache.get(hash(1))).isEmpty();
    assertThat(cache.get(hash(2))).contains(rlp(2, 100));
    assertThat(cache.get(hash(3))).contains(rlp(3, 250));
    assertThat(cache.evictionCount()).isEqualTo(2);
    assertThat(cache.weight()).isEqualTo(100 + 250 + 2 * ENTRY_OVERHEAD);
  }

  @Test
  void doesNotCacheBlockOverByteLimit() {
    final RecentBlocksRlpCache cache = new RecentBlocksRlpCache(10, 1000);
    cache.put(hash(0), rlp(0, 100));

    cache.put(hash(1), rlp(1, 1000));

    assertThat(cache.get(hash(0))).contains(rlp(0, 100));
    assertThat(cache.get(hash(1))).isEmpty();
    assertThat(cache.evictionCount()).isZero();
    assertThat(cache.weight()).isEqualTo(100 + ENTRY_OVERHEAD);
  }

  @Test
  void keepsFirstRlpOfBlockAlreadyCached() {
    final RecentBlocksRlpCache cache = new RecentBlocksRlpCache(10, Long.MAX_VALUE);
    cache.put(hash(0), rlp(0, 100));

    cache.put(hash(0), rlp(1, 50));

    assertThat(cache.get(hash(0))).contains(rlp(0, 100));
    assertThat(cache.weight()).isEqualTo(100 + ENTRY_OVERHEAD);
    assertThat(cache.cache().size()).isEqualTo(1);
  }

  @Test
  void recordsHitsAndMisses() {
    final RecentBlocksRlpCache cache = new RecentBlocksRlpCache(10, Long.MAX_VALUE);
    cache.put(hash(0), rlp(0, 10));

    assertThat(cache.get(hash(0))).isPresent();
    assertThat(cache.get(hash(1))).isEmpty();

    assertThat(cache.cache().stats().hitCount()).isEqualTo(1);
    assertThat(cache.cache().stats().missCount()).isEqualTo(1);
  }

  private static Hash hash(final int i) {
    return Hash.hash(Bytes.ofUnsignedInt(i));
  }

  private static Bytes rlp(final int fill, final int size) {
    final byte[] bytes = new byte[size];
    Arrays.fill(bytes, (byte) fill);
    return Bytes.wrap(bytes);
  }
}
