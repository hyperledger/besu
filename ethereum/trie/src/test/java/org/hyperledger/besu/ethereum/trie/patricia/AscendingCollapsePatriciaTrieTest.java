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
package org.hyperledger.besu.ethereum.trie.patricia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AscendingCollapsePatriciaTrieTest {

  @Test
  void emptyTrieHasEmptyRoot() {
    assertThat(new AscendingCollapsePatriciaTrie().rootHash())
        .isEqualTo(MerkleTrie.EMPTY_TRIE_NODE_HASH);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 17, 256, 1000, 5000})
  void rootMatchesReferenceTrie(final int size) {
    final NavigableMap<Bytes32, Bytes> entries = randomEntries(new Random(size), size, 40);
    assertThat(ascendingRoot(entries)).isEqualTo(referenceRoot(entries));
  }

  @Test
  void rootMatchesWithSmallValuesAndSharedPrefixes() {
    final Random random = new Random(7);
    final TreeMap<Bytes32, Bytes> entries = new TreeMap<>();
    // keys sharing long prefixes and tiny values produce inlined nodes and long extensions
    final byte[] base = new byte[32];
    random.nextBytes(base);
    for (int i = 0; i < 200; i++) {
      final byte[] key = base.clone();
      key[31] = (byte) i;
      if (i % 3 == 0) {
        key[30] = (byte) (key[30] ^ 0x01);
      }
      entries.put(Bytes32.wrap(key), Bytes.of(i % 0x7f + 1));
    }
    assertThat(ascendingRoot(entries)).isEqualTo(referenceRoot(entries));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 50, 3000})
  void handsTheNodesAStoredTrieCommits(final int size) {
    final NavigableMap<Bytes32, Bytes> entries = randomEntries(new Random(31L * size), size, 60);
    final Map<Bytes, Bytes> stored = new HashMap<>();
    final StoredMerklePatriciaTrie<Bytes32, Bytes> trie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) -> Optional.empty(), Function.identity(), Function.identity());
    entries.forEach(trie::put);
    trie.commit((location, hash, value) -> stored.put(location, value));

    final Map<Bytes, Bytes> handed = new HashMap<>();
    final AscendingCollapsePatriciaTrie ascending =
        new AscendingCollapsePatriciaTrie((location, hash, node) -> handed.put(location, node));
    entries.forEach(ascending::insert);

    assertThat(ascending.rootHash()).isEqualTo(trie.getRootHash());
    assertThat(handed).isEqualTo(stored);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 15, 16, 40, 2000})
  void partitionedBuildMatchesSingleBuild(final int size) {
    final NavigableMap<Bytes32, Bytes> entries = randomEntries(new Random(99L + size), size, 50);
    final Map<Bytes, Bytes> single = new HashMap<>();
    final AscendingCollapsePatriciaTrie full =
        new AscendingCollapsePatriciaTrie((location, hash, node) -> single.put(location, node));
    entries.forEach(full::insert);
    final Bytes32 expectedRoot = full.rootHash();

    // one partition per first byte
    final Map<Bytes, Bytes> partitioned = new HashMap<>();
    final NodeUpdater sink = (location, hash, node) -> partitioned.put(location, node);
    final List<AscendingCollapsePatriciaTrie.Partition> partitions = new ArrayList<>();
    for (int prefix = 0; prefix < 256; prefix++) {
      final AscendingCollapsePatriciaTrie part = new AscendingCollapsePatriciaTrie(sink);
      final int p = prefix;
      entries.forEach(
          (key, value) -> {
            if ((key.get(0) & 0xff) == p) {
              part.insert(key, value);
            }
          });
      final AscendingCollapsePatriciaTrie.Partition partition = part.finishAsPartition();
      if (partition != null) {
        partitions.add(partition);
      }
    }
    final AscendingCollapsePatriciaTrie top = new AscendingCollapsePatriciaTrie(sink);
    partitions.forEach(top::insertPartition);

    assertThat(top.rootHash()).isEqualTo(expectedRoot);
    assertThat(partitioned).isEqualTo(single);
  }

  @Test
  void rejectsUnorderedKeys() {
    final AscendingCollapsePatriciaTrie trie = new AscendingCollapsePatriciaTrie();
    trie.insert(Bytes32.fromHexString("0x02"), Bytes.of(1));
    assertThatThrownBy(() -> trie.insert(Bytes32.fromHexString("0x01"), Bytes.of(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> trie.insert(Bytes32.fromHexString("0x02"), Bytes.of(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void countsTheLeavesAndRejectsEmptyValues() {
    final AscendingCollapsePatriciaTrie trie = new AscendingCollapsePatriciaTrie();
    trie.insert(Bytes32.fromHexString("0x01"), Bytes.of(1));
    trie.insert(Bytes32.fromHexString("0x02"), Bytes.of(2));

    assertThat(trie.insertCount()).isEqualTo(2);
    assertThatThrownBy(() -> trie.insert(Bytes32.fromHexString("0x03"), Bytes.EMPTY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static NavigableMap<Bytes32, Bytes> randomEntries(
      final Random random, final int size, final int maxValueSize) {
    final TreeMap<Bytes32, Bytes> entries = new TreeMap<>();
    while (entries.size() < size) {
      final byte[] key = new byte[32];
      random.nextBytes(key);
      final byte[] value = new byte[1 + random.nextInt(maxValueSize)];
      random.nextBytes(value);
      entries.put(Bytes32.wrap(key), Bytes.wrap(value));
    }
    return entries;
  }

  private static Bytes32 referenceRoot(final Map<Bytes32, Bytes> entries) {
    final SimpleMerklePatriciaTrie<Bytes32, Bytes> trie =
        new SimpleMerklePatriciaTrie<>(Function.identity());
    entries.forEach(trie::put);
    return trie.getRootHash();
  }

  private static Bytes32 ascendingRoot(final NavigableMap<Bytes32, Bytes> entries) {
    final AscendingCollapsePatriciaTrie trie = new AscendingCollapsePatriciaTrie();
    entries.forEach(trie::insert);
    return trie.rootHash();
  }
}
