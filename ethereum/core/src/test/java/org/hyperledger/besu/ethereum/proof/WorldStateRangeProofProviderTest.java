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
package org.hyperledger.besu.ethereum.proof;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.TrieGenerator;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.RangeStorageEntriesCollector;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.trie.forest.storage.ForestWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WorldStateRangeProofProviderTest {

  private static final Hash MAX_RANGE =
      Hash.fromHexString("0x0fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
  private static final ForestWorldStateKeyValueStorage worldStateKeyValueStorage =
      new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());

  private WorldStateStorageCoordinator worldStateStorageCoordinator;
  private static WorldStateProofProvider worldStateProofProvider;

  @BeforeEach
  public void setup() {
    worldStateStorageCoordinator = new WorldStateStorageCoordinator(worldStateKeyValueStorage);
    worldStateProofProvider = new WorldStateProofProvider(worldStateStorageCoordinator);
  }

  @Test
  public void rangeProofValidationNominalCase() {
    final MerkleTrie<Bytes, Bytes> accountStateTrie =
        TrieGenerator.generateTrie(worldStateStorageCoordinator, 15);
    // collect accounts in range
    final RangeStorageEntriesCollector collector =
        RangeStorageEntriesCollector.createCollector(Hash.ZERO, MAX_RANGE, 10, Integer.MAX_VALUE);
    final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            accountStateTrie.entriesFrom(
                root ->
                    RangeStorageEntriesCollector.collectEntries(
                        collector, visitor, root, Hash.ZERO));
    // generate the proof
    final List<Bytes> proofs =
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), Hash.ZERO);
    proofs.addAll(
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), accounts.lastKey()));

    // validate the range proof
    boolean isValidRangeProof =
        worldStateProofProvider.isValidRangeProof(
            Bytes32.ZERO, accounts.lastKey(), accountStateTrie.getRootHash(), proofs, accounts);
    assertThat(isValidRangeProof).isTrue();
  }

  @Test
  public void rangeProofValidationMissingAccount() {
    MerkleTrie<Bytes, Bytes> accountStateTrie =
        TrieGenerator.generateTrie(worldStateStorageCoordinator, 15);
    // collect accounts in range
    final RangeStorageEntriesCollector collector =
        RangeStorageEntriesCollector.createCollector(Hash.ZERO, MAX_RANGE, 10, Integer.MAX_VALUE);
    final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            accountStateTrie.entriesFrom(
                root ->
                    RangeStorageEntriesCollector.collectEntries(
                        collector, visitor, root, Hash.ZERO));

    // generate the proof
    final List<Bytes> proofs =
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), Hash.ZERO);
    proofs.addAll(
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), accounts.lastKey()));

    // remove an account
    final Iterator<Map.Entry<Bytes32, Bytes>> iterator = accounts.entrySet().iterator();
    int i = 0;
    while (iterator.hasNext()) {
      iterator.next();
      i++;
      if (i == 7) iterator.remove();
    }
    // validate the range proof
    boolean isValidRangeProof =
        worldStateProofProvider.isValidRangeProof(
            Bytes32.ZERO, accounts.lastKey(), accountStateTrie.getRootHash(), proofs, accounts);
    assertThat(isValidRangeProof).isFalse();
  }

  @Test
  public void rangeProofValidationNoMonotonicIncreasing() {
    MerkleTrie<Bytes, Bytes> accountStateTrie =
        TrieGenerator.generateTrie(worldStateStorageCoordinator, 15);

    // generate the invalid proof
    final RangeStorageEntriesCollector collector =
        RangeStorageEntriesCollector.createCollector(Hash.ZERO, MAX_RANGE, 12, Integer.MAX_VALUE);
    final TrieIterator<Bytes> invalidVisitor =
        RangeStorageEntriesCollector.createVisitor(collector);
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            accountStateTrie.entriesFrom(
                root ->
                    RangeStorageEntriesCollector.collectEntries(
                        collector, invalidVisitor, root, Hash.ZERO));
    final List<Bytes> proofs =
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), Hash.ZERO);
    proofs.addAll(
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), accounts.lastKey()));

    accounts.remove(Hash.ZERO);

    accounts.putIfAbsent(Hash.ZERO, Bytes.EMPTY);
    // validate the range proof
    boolean isValidRangeProof =
        worldStateProofProvider.isValidRangeProof(
            Bytes32.ZERO,
            accounts.lastKey(),
            accountStateTrie.getRootHash(),
            new ArrayList<>(),
            accounts);
    assertThat(isValidRangeProof).isFalse();
  }

  @Test
  public void rangeProofValidationEmptyProof() {
    MerkleTrie<Bytes, Bytes> accountStateTrie =
        TrieGenerator.generateTrie(worldStateStorageCoordinator, 15);

    // generate the invalid proof
    final RangeStorageEntriesCollector collector =
        RangeStorageEntriesCollector.createCollector(Hash.ZERO, MAX_RANGE, 15, Integer.MAX_VALUE);
    final TrieIterator<Bytes> invalidVisitor =
        RangeStorageEntriesCollector.createVisitor(collector);
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            accountStateTrie.entriesFrom(
                root ->
                    RangeStorageEntriesCollector.collectEntries(
                        collector, invalidVisitor, root, Hash.ZERO));

    // validate the range proof
    boolean isValidRangeProof =
        worldStateProofProvider.isValidRangeProof(
            Bytes32.ZERO,
            accounts.lastKey(),
            accountStateTrie.getRootHash(),
            new ArrayList<>(),
            accounts);
    assertThat(isValidRangeProof).isTrue();
  }

  @Test
  public void rangeProofValidationInvalidEmptyProof() {
    MerkleTrie<Bytes, Bytes> accountStateTrie =
        TrieGenerator.generateTrie(worldStateStorageCoordinator, 15);

    // generate the invalid proof
    final RangeStorageEntriesCollector collector =
        RangeStorageEntriesCollector.createCollector(Hash.ZERO, MAX_RANGE, 9, Integer.MAX_VALUE);
    final TrieIterator<Bytes> invalidVisitor =
        RangeStorageEntriesCollector.createVisitor(collector);
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            accountStateTrie.entriesFrom(
                root ->
                    RangeStorageEntriesCollector.collectEntries(
                        collector, invalidVisitor, root, Hash.ZERO));

    // validate the range proof
    boolean isValidRangeProof =
        worldStateProofProvider.isValidRangeProof(
            Bytes32.ZERO,
            accounts.lastKey(),
            accountStateTrie.getRootHash(),
            new ArrayList<>(),
            accounts);
    assertThat(isValidRangeProof).isFalse();
  }
}
