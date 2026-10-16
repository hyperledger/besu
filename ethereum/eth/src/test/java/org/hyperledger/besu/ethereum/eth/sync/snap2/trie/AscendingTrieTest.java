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
package org.hyperledger.besu.ethereum.eth.sync.snap2.trie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.patricia.SimpleMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;

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

class AscendingTrieTest {

  @Test
  void emptyTrieHasEmptyRoot() {
    assertThat(new AscendingTrie(null).rootHash()).isEqualTo(MerkleTrie.EMPTY_TRIE_NODE_HASH);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 17, 256, 1000, 5000})
  void rootMatchesReferenceTrie(final int size) {
    final NavigableMap<Bytes32, Bytes> entries = randomEntries(new Random(size), size, 40);
    assertThat(stackRoot(entries)).isEqualTo(referenceRoot(entries));
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
      entries.put(Bytes32.wrap(key), Bytes.of(i % 0x7f));
    }
    assertThat(stackRoot(entries)).isEqualTo(referenceRoot(entries));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 50, 3000})
  void emittedNodesMatchStoredTrieCommit(final int size) {
    final NavigableMap<Bytes32, Bytes> entries = randomEntries(new Random(31L * size), size, 60);
    final Map<Bytes, Bytes> stored = new HashMap<>();
    final StoredMerklePatriciaTrie<Bytes32, Bytes> trie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) -> Optional.empty(), Function.identity(), Function.identity());
    entries.forEach(trie::put);
    trie.commit((location, hash, value) -> stored.put(location, value));

    final Map<Bytes, Bytes> emitted = new HashMap<>();
    final AscendingTrie stackTrie =
        new AscendingTrie((location, hash, rlp) -> emitted.put(location, rlp));
    entries.forEach(stackTrie::addLeaf);
    final Bytes32 root = stackTrie.rootHash();

    assertThat(root).isEqualTo(trie.getRootHash());
    assertThat(emitted).isEqualTo(stored);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 15, 16, 40, 2000})
  void partitionedBuildMatchesSingleBuild(final int size) {
    final NavigableMap<Bytes32, Bytes> entries = randomEntries(new Random(99L + size), size, 50);
    final Map<Bytes, Bytes> single = new HashMap<>();
    final AscendingTrie full =
        new AscendingTrie((location, hash, rlp) -> single.put(location, rlp));
    entries.forEach(full::addLeaf);
    final Bytes32 expectedRoot = full.rootHash();

    // partition on the first byte, as the world state builder does
    final Map<Bytes, Bytes> partitioned = new HashMap<>();
    final NodeUpdater sink = (location, hash, rlp) -> partitioned.put(location, rlp);
    final List<AscendingTrie.Item> items = new ArrayList<>();
    for (int prefix = 0; prefix < 256; prefix++) {
      final AscendingTrie part = new AscendingTrie(sink);
      final int p = prefix;
      entries.forEach(
          (key, value) -> {
            if ((key.get(0) & 0xff) == p) {
              part.addLeaf(key, value);
            }
          });
      final AscendingTrie.Item item = part.finishAsItem();
      if (item != null) {
        items.add(item);
      }
    }
    final AscendingTrie top = new AscendingTrie(sink);
    items.forEach(top::addItem);

    assertThat(top.rootHash()).isEqualTo(expectedRoot);
    assertThat(partitioned).isEqualTo(single);
  }

  @Test
  void rejectsUnorderedKeys() {
    final AscendingTrie trie = new AscendingTrie(null);
    trie.addLeaf(Bytes32.fromHexString("0x02"), Bytes.of(1));
    assertThatThrownBy(() -> trie.addLeaf(Bytes32.fromHexString("0x01"), Bytes.of(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> trie.addLeaf(Bytes32.fromHexString("0x02"), Bytes.of(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  static NavigableMap<Bytes32, Bytes> randomEntries(
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

  static Bytes32 referenceRoot(final Map<Bytes32, Bytes> entries) {
    final SimpleMerklePatriciaTrie<Bytes32, Bytes> trie =
        new SimpleMerklePatriciaTrie<>(Function.identity());
    entries.forEach(trie::put);
    return trie.getRootHash();
  }

  private static Bytes32 stackRoot(final NavigableMap<Bytes32, Bytes> entries) {
    final AscendingTrie trie = new AscendingTrie(null);
    entries.forEach(trie::addLeaf);
    return trie.rootHash();
  }
}
