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

import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStorageFixture;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStorageFixture.Format;
import org.hyperledger.besu.ethereum.eth.sync.snap2.storage.SyncStorageFixture.Storages;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.RangeManager;
import org.hyperledger.besu.ethereum.trie.patricia.SimpleMerklePatriciaTrie;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class RangeProofTest {

  private static final Bytes32 ACCOUNT = Bytes32.fromHexString("0x" + "ac".repeat(32));

  private NavigableMap<Bytes32, Bytes> entries;
  private SimpleMerklePatriciaTrie<Bytes32, Bytes> trie;
  private Bytes32 root;

  @BeforeEach
  void setUp() {
    entries = AscendingTrieTest.randomEntries(new Random(42), 500, 40);
    trie = new SimpleMerklePatriciaTrie<>(Function.identity());
    entries.forEach(trie::put);
    root = trie.getRootHash();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 17, 250, 498})
  void acceptsAValidSlice(final int startIndex) {
    final List<Bytes32> keys = new ArrayList<>(entries.keySet());
    final Bytes32 origin = keys.get(startIndex);
    final NavigableMap<Bytes32, Bytes> slice =
        entries.subMap(origin, true, keys.get(Math.min(startIndex + 40, keys.size() - 1)), true);

    final RangeProof.Result result = verify(origin, slice, proof(origin, slice.lastKey()));

    assertThat(result.valid()).isTrue();
    assertThat(result.hasMore()).isEqualTo(!slice.lastKey().equals(entries.lastKey()));
  }

  @Test
  void acceptsAnOriginThatIsNotAKey() {
    final Bytes32 origin = keyBefore(entries.ceilingKey(Bytes32.fromHexString("0x80")));
    final NavigableMap<Bytes32, Bytes> slice = firstEntriesFrom(origin, 30);

    assertThat(verify(origin, slice, proof(origin, slice.lastKey())).valid()).isTrue();
  }

  @Test
  void acceptsTheWholeTrieWithoutProof() {
    final RangeProof.Result result = verify(Bytes32.ZERO, entries, List.of());
    assertThat(result.valid()).isTrue();
    assertThat(result.hasMore()).isFalse();
  }

  @Test
  void rejectsAPartialTrieWithoutProof() {
    assertThat(verify(Bytes32.ZERO, firstEntriesFrom(Bytes32.ZERO, 10), List.of()).valid())
        .isFalse();
  }

  @Test
  void acceptsAnEmptyRangeAfterTheLastKey() {
    final Bytes32 origin = keyAfter(entries.lastKey());
    final RangeProof.Result result = verify(origin, new TreeMap<>(), proof(origin, origin));
    assertThat(result.valid()).isTrue();
    assertThat(result.hasMore()).isFalse();
  }

  @Test
  void rejectsAnEmptyRangeWhenKeysExist() {
    final Bytes32 origin = keyBefore(entries.ceilingKey(Bytes32.fromHexString("0x80")));
    assertThat(verify(origin, new TreeMap<>(), proof(origin, origin)).valid()).isFalse();
  }

  @Test
  void rejectsAGap() {
    final Bytes32 origin = entries.firstKey();
    final TreeMap<Bytes32, Bytes> slice = new TreeMap<>(firstEntriesFrom(origin, 30));
    final List<Bytes> proof = proof(origin, slice.lastKey());
    slice.remove(new ArrayList<>(slice.keySet()).get(10));

    assertThat(verify(origin, slice, proof).valid()).isFalse();
  }

  @Test
  void rejectsAMissingFirstKey() {
    final Bytes32 origin = entries.higherKey(entries.firstKey());
    final Bytes32 requested = keyBefore(origin);
    final TreeMap<Bytes32, Bytes> slice = new TreeMap<>(firstEntriesFrom(origin, 20));
    slice.remove(origin);

    assertThat(verify(requested, slice, proof(requested, slice.lastKey())).valid()).isFalse();
  }

  @Test
  void rejectsAModifiedValue() {
    final Bytes32 origin = entries.firstKey();
    final TreeMap<Bytes32, Bytes> slice = new TreeMap<>(firstEntriesFrom(origin, 30));
    final Bytes32 victim = new ArrayList<>(slice.keySet()).get(5);
    slice.put(victim, Bytes.of(1, 2, 3));

    assertThat(verify(origin, slice, proof(origin, slice.lastKey())).valid()).isFalse();
  }

  @Test
  void rejectsAnExtraKey() {
    final Bytes32 origin = entries.firstKey();
    final TreeMap<Bytes32, Bytes> slice = new TreeMap<>(firstEntriesFrom(origin, 30));
    final List<Bytes> proof = proof(origin, slice.lastKey());
    slice.put(keyAfter(new ArrayList<>(slice.keySet()).get(3)), Bytes.of(9));

    assertThat(verify(origin, slice, proof).valid()).isFalse();
  }

  @Test
  void rejectsAMissingProofNode() {
    final Bytes32 origin = entries.firstKey();
    final NavigableMap<Bytes32, Bytes> slice = firstEntriesFrom(origin, 30);
    final List<Bytes> proof = proof(origin, slice.lastKey());
    proof.removeLast();

    assertThat(verify(origin, slice, proof).valid()).isFalse();
  }

  @Test
  void rejectsKeysBeforeOrigin() {
    final Bytes32 origin = new ArrayList<>(entries.keySet()).get(10);
    final NavigableMap<Bytes32, Bytes> slice = firstEntriesFrom(entries.firstKey(), 30);
    assertThat(verify(origin, slice, proof(origin, slice.lastKey())).valid()).isFalse();
  }

  @Test
  void acceptsASingleLeafTrie() {
    final SimpleMerklePatriciaTrie<Bytes32, Bytes> single =
        new SimpleMerklePatriciaTrie<>(Function.identity());
    final Bytes32 key = entries.firstKey();
    single.put(key, entries.firstEntry().getValue());
    final List<Bytes> proof = single.getValueWithProof(key).getProofRelatedNodes();

    final RangeProof.Result result =
        RangeProof.verify(
            single.getRootHash(),
            Bytes32.ZERO,
            List.of(key),
            List.of(entries.firstEntry().getValue()),
            proof);
    assertThat(result.valid()).isTrue();
    assertThat(result.hasMore()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(Format.class)
  void handsEveryNodeOfAWholeTrie(final Format format) {
    final Storages storages = SyncStorageFixture.create(format);
    final SyncStateStorage.TrieWriter writer = storages.sync().trieWriter();

    assertThat(verify(Bytes32.ZERO, entries, List.of(), storing(writer)).valid()).isTrue();
    writer.commit();

    assertStored(storages, root, entries);
  }

  // the ranges of one root, verified in any order, give every node of its trie
  @ParameterizedTest
  @EnumSource(Format.class)
  void handsEveryNodeOfATrieOverItsRanges(final Format format) {
    for (final long seed : new long[] {2, 3, 5, 7, 11}) {
      final Storages storages = SyncStorageFixture.create(format);
      final Random random = new Random(seed);
      final NavigableMap<Bytes32, Bytes> slots =
          AscendingTrieTest.randomEntries(random, 300 + random.nextInt(2000), 40);
      final SimpleMerklePatriciaTrie<Bytes32, Bytes> other =
          new SimpleMerklePatriciaTrie<>(Function.identity());
      slots.forEach(other::put);
      final List<Map.Entry<Bytes32, NavigableMap<Bytes32, Bytes>>> ranges = split(random, slots);
      Collections.shuffle(ranges, random);
      final SyncStateStorage.TrieWriter writer = storages.sync().trieWriter();

      for (final Map.Entry<Bytes32, NavigableMap<Bytes32, Bytes>> range : ranges) {
        final List<Bytes> proof = proof(other, range.getKey(), range.getValue().lastKey());
        assertThat(
                RangeProof.verify(
                        other.getRootHash(),
                        range.getKey(),
                        new ArrayList<>(range.getValue().keySet()),
                        new ArrayList<>(range.getValue().values()),
                        proof,
                        storing(writer))
                    .valid())
            .isTrue();
      }
      writer.commit();

      assertStored(storages, other.getRootHash(), slots);
    }
  }

  @Test
  void handsTheEdgeNodesOfAnEmptyRangeAtMost() {
    final Bytes32 origin = keyAfter(entries.lastKey());
    final List<Bytes> handed = new ArrayList<>();

    assertThat(
            verify(origin, new TreeMap<>(), proof(origin, origin), (l, h, node) -> handed.add(node))
                .valid())
        .isTrue();
    assertThat(handed).hasSizeLessThanOrEqualTo(proof(origin, origin).size());
  }

  // consecutive ranges as peers serve them, cut at random keys, keyed by their origin
  private static List<Map.Entry<Bytes32, NavigableMap<Bytes32, Bytes>>> split(
      final Random random, final NavigableMap<Bytes32, Bytes> slots) {
    final List<Map.Entry<Bytes32, NavigableMap<Bytes32, Bytes>>> ranges = new ArrayList<>();
    final List<Bytes32> keys = List.copyOf(slots.keySet());
    Bytes32 origin = RangeManager.MIN_RANGE;
    int from = 0;
    while (from < keys.size()) {
      final int to = Math.min(keys.size(), from + 1 + random.nextInt(keys.size() / 4));
      final Bytes32 last = keys.get(to - 1);
      ranges.add(Map.entry(origin, new TreeMap<>(slots.subMap(keys.get(from), true, last, true))));
      origin = keyAfter(last);
      from = to;
    }
    return ranges;
  }

  private static NodeUpdater storing(final SyncStateStorage.TrieWriter writer) {
    return (location, hash, node) -> writer.storageNode(ACCOUNT, location, hash, node);
  }

  // every leaf reads back through the stored nodes
  private static void assertStored(
      final Storages storages, final Bytes32 root, final Map<Bytes32, Bytes> slots) {
    assertThat(storages.sync().hasStorageTrie(ACCOUNT, root)).isTrue();
    assertThat(storages.storageTrie(ACCOUNT, root).entriesFrom(Bytes32.ZERO, Integer.MAX_VALUE))
        .isEqualTo(slots);
  }

  private RangeProof.Result verify(
      final Bytes32 origin,
      final Map<Bytes32, Bytes> slice,
      final List<Bytes> proof,
      final NodeUpdater sink) {
    return RangeProof.verify(
        root,
        origin,
        new ArrayList<>(slice.keySet()),
        new ArrayList<>(slice.values()),
        proof,
        sink);
  }

  private RangeProof.Result verify(
      final Bytes32 origin, final Map<Bytes32, Bytes> slice, final List<Bytes> proof) {
    return RangeProof.verify(
        root, origin, new ArrayList<>(slice.keySet()), new ArrayList<>(slice.values()), proof);
  }

  private List<Bytes> proof(final Bytes32 origin, final Bytes32 last) {
    return proof(trie, origin, last);
  }

  private static List<Bytes> proof(
      final SimpleMerklePatriciaTrie<Bytes32, Bytes> trie,
      final Bytes32 origin,
      final Bytes32 last) {
    final Set<Bytes> nodes = new LinkedHashSet<>();
    nodes.addAll(trie.getValueWithProof(origin).getProofRelatedNodes());
    nodes.addAll(trie.getValueWithProof(last).getProofRelatedNodes());
    return new ArrayList<>(nodes);
  }

  private NavigableMap<Bytes32, Bytes> firstEntriesFrom(final Bytes32 origin, final int count) {
    final TreeMap<Bytes32, Bytes> out = new TreeMap<>();
    for (final Map.Entry<Bytes32, Bytes> e : entries.tailMap(origin, true).entrySet()) {
      if (out.size() == count) {
        break;
      }
      out.put(e.getKey(), e.getValue());
    }
    return out;
  }

  private static Bytes32 keyBefore(final Bytes32 key) {
    return UInt256.fromBytes(key).subtract(1).toBytes();
  }

  private static Bytes32 keyAfter(final Bytes32 key) {
    return RangeManager.incrementBytes32(key).orElseThrow();
  }
}
