/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.TrieGenerator;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.RangeStorageEntriesCollector;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public final class RangeManagerTest {

  @Test
  public void testGenerateAllRangesWithSize1() {
    final Map<Bytes32, Bytes32> expectedResult = new HashMap<>();
    expectedResult.put(
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
        Bytes32.fromHexString(
            "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    final Map<Bytes32, Bytes32> ranges = RangeManager.generateAllRanges(1);
    Assertions.assertThat(ranges.size()).isEqualTo(1);
    Assertions.assertThat(ranges).isEqualTo(expectedResult);
  }

  @Test
  public void testGenerateAllRangesWithSize3() {
    final Map<Bytes32, Bytes32> expectedResult = new HashMap<>();
    expectedResult.put(
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
        Bytes32.fromHexString(
            "0x5555555555555555555555555555555555555555555555555555555555555555"));
    expectedResult.put(
        Bytes32.fromHexString("0x5555555555555555555555555555555555555555555555555555555555555556"),
        Bytes32.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab"));
    expectedResult.put(
        Bytes32.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaac"),
        Bytes32.fromHexString(
            "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    final Map<Bytes32, Bytes32> ranges = RangeManager.generateAllRanges(3);
    Assertions.assertThat(ranges.size()).isEqualTo(3);
    Assertions.assertThat(ranges).isEqualTo(expectedResult);
  }

  @Test
  public void testGenerateRangesWithSize3() {
    final Map<Bytes32, Bytes32> expectedResult = new HashMap<>();
    expectedResult.put(
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
        Bytes32.fromHexString(
            "0x5555555555555555555555555555555555555555555555555555555555555555"));
    expectedResult.put(
        Bytes32.fromHexString("0x5555555555555555555555555555555555555555555555555555555555555556"),
        Bytes32.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab"));
    expectedResult.put(
        Bytes32.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaac"),
        Bytes32.fromHexString(
            "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    final Map<Bytes32, Bytes32> ranges =
        RangeManager.generateRanges(
            Bytes32.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000"),
            Bytes32.fromHexString(
                "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
            3);
    Assertions.assertThat(ranges.size()).isEqualTo(3);
    Assertions.assertThat(ranges).isEqualTo(expectedResult);
  }

  @Test
  public void testFindNewBeginElement() {

    final WorldStateStorage worldStateStorage =
        new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());

    final MerklePatriciaTrie<Bytes32, Bytes> accountStateTrie =
        TrieGenerator.generateTrie(worldStateStorage, 15);

    final RangeStorageEntriesCollector collector =
        RangeStorageEntriesCollector.createCollector(
            Hash.ZERO, RangeManager.MAX_RANGE, 10, Integer.MAX_VALUE);
    final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            accountStateTrie.entriesFrom(
                root ->
                    RangeStorageEntriesCollector.collectEntries(
                        collector, visitor, root, Hash.ZERO));

    final WorldStateProofProvider worldStateProofProvider =
        new WorldStateProofProvider(worldStateStorage);

    // generate the proof
    final List<Bytes> proofs =
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), Hash.ZERO);
    proofs.addAll(
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), accounts.lastKey()));

    final Optional<Bytes32> newBeginElementInRange =
        RangeManager.findNewBeginElementInRange(
            accountStateTrie.getRootHash(), proofs, accounts, RangeManager.MAX_RANGE);

    Assertions.assertThat(newBeginElementInRange)
        .contains(Bytes32.leftPad(Bytes.wrap(Bytes.ofUnsignedShort(0x0b))));
  }

  @Test
  public void testFindNewBeginElementWhenNothingIsMissing() {

    final WorldStateStorage worldStateStorage =
        new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());

    final MerklePatriciaTrie<Bytes32, Bytes> accountStateTrie =
        TrieGenerator.generateTrie(worldStateStorage, 15);

    final RangeStorageEntriesCollector collector =
        RangeStorageEntriesCollector.createCollector(
            Hash.ZERO, RangeManager.MAX_RANGE, 15, Integer.MAX_VALUE);
    final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            accountStateTrie.entriesFrom(
                root ->
                    RangeStorageEntriesCollector.collectEntries(
                        collector, visitor, root, Hash.ZERO));

    final WorldStateProofProvider worldStateProofProvider =
        new WorldStateProofProvider(worldStateStorage);

    // generate the proof
    final List<Bytes> proofs =
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), Hash.ZERO);
    proofs.addAll(
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), accounts.lastKey()));

    final Optional<Bytes32> newBeginElementInRange =
        RangeManager.findNewBeginElementInRange(
            accountStateTrie.getRootHash(), proofs, accounts, RangeManager.MAX_RANGE);

    Assertions.assertThat(newBeginElementInRange).isEmpty();
  }
}
