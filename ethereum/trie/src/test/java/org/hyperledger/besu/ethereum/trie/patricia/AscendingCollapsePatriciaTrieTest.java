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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.Node;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AscendingCollapsePatriciaTrieTest {

  @Test
  void matchesSimpleMerklePatriciaTrie() {
    final MerkleTrie<Bytes, Bytes> simple = new SimpleMerklePatriciaTrie<>(Function.identity());
    final AscendingCollapsePatriciaTrie ascending = new AscendingCollapsePatriciaTrie();
    final List<Bytes> keys = new ArrayList<>();
    for (int i = 0; i < 64; i++) {
      keys.add(Hash.hash(Bytes.ofUnsignedInt(i)).getBytes());
    }
    keys.sort(Comparator.naturalOrder());
    for (int i = 0; i < keys.size(); i++) {
      final int idx = i;
      final Bytes value = RLP.encode(out -> out.writeBytes(Bytes.ofUnsignedInt(idx + 1)));
      simple.put(keys.get(i), value);
      ascending.insert(keys.get(i), value);
    }
    assertThat(ascending.rootHash()).isEqualTo(simple.getRootHash());
  }

  @Test
  void emptyTrieHasTheEmptyRoot() {
    assertThat(new AscendingCollapsePatriciaTrie().rootHash())
        .isEqualTo(new SimpleMerklePatriciaTrie<>(Function.identity()).getRootHash());
  }

  @Test
  void rejectsDescendingKeys() {
    final AscendingCollapsePatriciaTrie ascending = new AscendingCollapsePatriciaTrie();
    final Bytes value = RLP.encode(out -> out.writeInt(1));
    ascending.insert(Bytes.fromHexString("0x" + "ff".repeat(32)), value);
    assertThatThrownBy(() -> ascending.insert(Bytes.fromHexString("0x" + "00".repeat(32)), value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ascending");
  }

  @Test
  void preparedPrefixGroupsMatchPlainInserts() {
    final List<Bytes> keys = hashedKeys(3000);
    final Bytes32 expected = plainRoot(keys);
    for (int nibbles = 1; nibbles <= 4; nibbles++) {
      final AscendingCollapsePatriciaTrie trie = new AscendingCollapsePatriciaTrie();
      for (final List<Bytes> group : prefixGroups(keys, nibbles)) {
        trie.insert(trie.prepare(group, values(group)));
      }
      assertThat(trie.insertCount()).isEqualTo(keys.size());
      assertThat(trie.rootHash()).as("groups on %d nibble(s)", nibbles).isEqualTo(expected);
    }
  }

  @Test
  void preparedGroupsMixWithPlainInserts() {
    final List<Bytes> keys = hashedKeys(500);
    final AscendingCollapsePatriciaTrie trie = new AscendingCollapsePatriciaTrie();
    final List<List<Bytes>> groups = prefixGroups(keys, 2);
    for (int i = 0; i < groups.size(); i++) {
      final List<Bytes> group = groups.get(i);
      if (i % 3 == 0) {
        group.forEach(key -> trie.insert(key, value(key)));
      } else {
        trie.insert(trie.prepare(group, values(group)));
      }
    }
    assertThat(trie.rootHash()).isEqualTo(plainRoot(keys));
  }

  @Test
  void aPreparedSubtreeAloneIsTheWholeTrie() {
    final List<Bytes> keys = hashedKeys(40);
    final AscendingCollapsePatriciaTrie trie = new AscendingCollapsePatriciaTrie();
    trie.insert(trie.prepare(keys, values(keys)));
    assertThat(trie.rootHash()).isEqualTo(plainRoot(keys));
  }

  @Test
  void rejectsAPreparedGroupMissingKeysOfItsPrefix() {
    final Bytes first = Bytes.fromHexString("0x11" + "00".repeat(31));
    final List<Bytes> group =
        List.of(
            Bytes.fromHexString("0x12" + "00".repeat(31)),
            Bytes.fromHexString("0x13" + "00".repeat(31)));
    final AscendingCollapsePatriciaTrie trie = new AscendingCollapsePatriciaTrie();
    trie.insert(first, value(first));
    // The group's prefix is nibble 1, which the first key also starts with.
    assertThatThrownBy(() -> trie.insert(trie.prepare(group, values(group))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("complete subtree");
  }

  @Test
  void rejectsAPreparedGroupBeforeTheLastKey() {
    final List<Bytes> keys = hashedKeys(10);
    final AscendingCollapsePatriciaTrie trie = new AscendingCollapsePatriciaTrie();
    trie.insert(keys.getLast(), value(keys.getLast()));
    final List<Bytes> earlier = keys.subList(0, 1);
    assertThatThrownBy(() -> trie.insert(trie.prepare(earlier, values(earlier))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ascending");
  }

  @Test
  void prepareRejectsUnsortedKeys() {
    final List<Bytes> keys = hashedKeys(2).reversed();
    final AscendingCollapsePatriciaTrie trie = new AscendingCollapsePatriciaTrie();
    assertThatThrownBy(() -> trie.prepare(keys, values(keys)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ascending");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 17, 256, 5000})
  void matchesSimpleMerklePatriciaTrieAtAnySize(final int size) {
    final List<Bytes> keys = hashedKeys(size);
    final AscendingCollapsePatriciaTrie trie = new AscendingCollapsePatriciaTrie();
    keys.forEach(key -> trie.insert(key, value(key)));
    assertThat(trie.rootHash()).isEqualTo(plainRoot(keys));
  }

  @Test
  void matchesWithInlinedNodesAndLongExtensions() {
    // keys sharing 30 bytes with 1-byte values: the deepest nodes are inlined in their parent
    final Bytes base = Bytes.fromHexString("0x" + "5a".repeat(30));
    final List<Bytes> keys = new ArrayList<>();
    for (int i = 0; i < 200; i++) {
      keys.add(Bytes.concatenate(base, Bytes.of(i % 3 == 0 ? 0x01 : 0x00, i)));
    }
    keys.sort(Comparator.naturalOrder());
    final AscendingCollapsePatriciaTrie trie = new AscendingCollapsePatriciaTrie();
    keys.forEach(key -> trie.insert(key, smallValue(key)));
    final MerkleTrie<Bytes, Bytes> simple = new SimpleMerklePatriciaTrie<>(Function.identity());
    keys.forEach(key -> simple.put(key, smallValue(key)));
    assertThat(trie.rootHash()).isEqualTo(simple.getRootHash());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 50, 3000})
  void handsTheNodesAStoredTrieCommits(final int size) {
    final List<Bytes> keys = hashedKeys(size);
    final Map<Bytes, Bytes> handed = new HashMap<>();
    final AscendingCollapsePatriciaTrie trie =
        new AscendingCollapsePatriciaTrie((location, hash, node) -> handed.put(location, node));
    keys.forEach(key -> trie.insert(key, value(key)));
    assertThat(trie.rootHash()).isEqualTo(plainRoot(keys));
    assertThat(handed).isEqualTo(storedNodes(keys));
  }

  @Test
  void preparedSubtreesHandTheNodesAStoredTrieCommits() {
    final List<Bytes> keys = hashedKeys(3000);
    final Map<Bytes, Bytes> handed = new HashMap<>();
    final AscendingCollapsePatriciaTrie trie =
        new AscendingCollapsePatriciaTrie((location, hash, node) -> handed.put(location, node));
    for (final List<Bytes> group : prefixGroups(keys, 2)) {
      trie.insert(trie.prepare(group, values(group)));
    }
    assertThat(trie.rootHash()).isEqualTo(plainRoot(keys));
    assertThat(handed).isEqualTo(storedNodes(keys));
  }

  @Test
  void sealedSubtreesStandForTheirNodes() {
    final List<Bytes> keys = hashedKeys(3000);
    final Map<Bytes, Bytes> stored = storedNodes(keys);
    final List<Bytes> sealed = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      sealed.add(Bytes.of(i));
    }
    for (int i = 0; i < 4; i++) {
      sealed.add(Bytes.of(5, i));
    }
    final Map<Bytes, Bytes> handed = new HashMap<>();
    final AscendingCollapsePatriciaTrie trie =
        new AscendingCollapsePatriciaTrie((location, hash, node) -> handed.put(location, node));
    // the root children 0 to 4 and the children 0 to 3 of child 5 by reference, then the keys
    sealed.forEach(location -> trie.insertSealed(location, hashRef(stored.get(location))));
    keys.stream()
        .filter(key -> (key.get(0) & 0xff) >= 0x54)
        .forEach(key -> trie.insert(key, value(key)));

    assertThat(trie.rootHash()).isEqualTo(plainRoot(keys));
    final Map<Bytes, Bytes> outside = new HashMap<>(stored);
    outside
        .keySet()
        .removeIf(
            location -> sealed.stream().anyMatch(s -> location.commonPrefixLength(s) == s.size()));
    assertThat(handed).isEqualTo(outside);
  }

  @Test
  void sealedInlinedLeavesStandForTheirNodes() {
    // 256 keys sharing 31 bytes with 1-byte values: the leaves at depth 64 are inlined
    final Bytes base = Bytes.fromHexString("0x" + "ab".repeat(31));
    final List<Bytes> keys =
        IntStream.range(0, 256).mapToObj(i -> Bytes.concatenate(base, Bytes.of(i))).toList();
    final MerkleTrie<Bytes, Bytes> simple = new SimpleMerklePatriciaTrie<>(Function.identity());
    keys.forEach(key -> simple.put(key, smallValue(key)));
    final StoredMerklePatriciaTrie<Bytes, Bytes> storedTrie = storedTrie();
    keys.forEach(key -> storedTrie.put(key, smallValue(key)));
    final Map<Bytes, Bytes> stored = new HashMap<>();
    storedTrie.commit((location, hash, node) -> stored.put(location, node));

    final Bytes basePath = CompactEncoding.bytesToPath(base).slice(0, 62);
    final Bytes firstBranch = Bytes.concatenate(basePath, Bytes.of(0));
    final Node<Bytes> branch = TrieNodeDecoder.decode(firstBranch, stored.get(firstBranch));
    final AscendingCollapsePatriciaTrie trie = new AscendingCollapsePatriciaTrie();
    // the leaves 0x00 to 0x07 inlined, the branch of 0x10 to 0x1f by hash, then the other keys
    for (int i = 0; i < 8; i++) {
      final Bytes ref = branch.getChildren().get(i).getEncodedBytesRef();
      assertThat(ref.size()).isLessThan(32);
      trie.insertSealed(Bytes.concatenate(firstBranch, Bytes.of(i)), ref);
    }
    keys.subList(0x08, 0x10).forEach(key -> trie.insert(key, smallValue(key)));
    final Bytes secondBranch = Bytes.concatenate(basePath, Bytes.of(1));
    trie.insertSealed(secondBranch, hashRef(stored.get(secondBranch)));
    keys.subList(0x20, 0x100).forEach(key -> trie.insert(key, smallValue(key)));

    assertThat(trie.insertCount()).isEqualTo(256 - 8 - 16);
    assertThat(trie.rootHash()).isEqualTo(simple.getRootHash());
  }

  @Test
  void aSealedRootIsTheWholeTrie() {
    final List<Bytes> keys = hashedKeys(10);
    final AscendingCollapsePatriciaTrie trie = new AscendingCollapsePatriciaTrie();
    trie.insertSealed(Bytes.EMPTY, RLP.encodeOne(plainRoot(keys)));
    assertThat(trie.rootHash()).isEqualTo(plainRoot(keys));
  }

  @Test
  void rejectsASealedSubtreeHoldingTheLastKey() {
    final Bytes key = Bytes.fromHexString("0x11" + "00".repeat(31));
    final AscendingCollapsePatriciaTrie trie = new AscendingCollapsePatriciaTrie();
    trie.insert(key, value(key));
    assertThatThrownBy(() -> trie.insertSealed(Bytes.of(1), RLP.encodeOne(Bytes32.ZERO)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ascending");
  }

  private static List<Bytes> hashedKeys(final int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> Hash.hash(Bytes.ofUnsignedInt(i)).getBytes())
        .sorted(Comparator.naturalOrder())
        .toList();
  }

  private static Bytes value(final Bytes key) {
    return RLP.encode(out -> out.writeBytes(key.slice(0, 1 + (key.get(31) & 0x1f))));
  }

  private static List<Bytes> values(final List<Bytes> keys) {
    return keys.stream().map(AscendingCollapsePatriciaTrieTest::value).toList();
  }

  private static Bytes smallValue(final Bytes key) {
    return Bytes.of((key.get(31) & 0x7f) | 0x01);
  }

  private static Bytes hashRef(final Bytes node) {
    return RLP.encodeOne(Hash.hash(node).getBytes());
  }

  private static StoredMerklePatriciaTrie<Bytes, Bytes> storedTrie() {
    return new StoredMerklePatriciaTrie<>(
        (location, hash) -> Optional.empty(), Function.identity(), Function.identity());
  }

  private static Map<Bytes, Bytes> storedNodes(final List<Bytes> keys) {
    final StoredMerklePatriciaTrie<Bytes, Bytes> trie = storedTrie();
    keys.forEach(key -> trie.put(key, value(key)));
    final Map<Bytes, Bytes> stored = new HashMap<>();
    trie.commit((location, hash, node) -> stored.put(location, node));
    return stored;
  }

  private static Bytes32 plainRoot(final List<Bytes> keys) {
    final MerkleTrie<Bytes, Bytes> simple = new SimpleMerklePatriciaTrie<>(Function.identity());
    keys.forEach(key -> simple.put(key, value(key)));
    return simple.getRootHash();
  }

  /** Consecutive keys sharing their first {@code nibbles} nibbles. */
  private static List<List<Bytes>> prefixGroups(final List<Bytes> keys, final int nibbles) {
    final List<List<Bytes>> groups = new ArrayList<>();
    Bytes prefix = null;
    for (final Bytes key : keys) {
      final Bytes keyPrefix = CompactEncoding.bytesToPath(key).slice(0, nibbles);
      if (!keyPrefix.equals(prefix)) {
        groups.add(new ArrayList<>());
        prefix = keyPrefix;
      }
      groups.getLast().add(key);
    }
    return groups;
  }
}
