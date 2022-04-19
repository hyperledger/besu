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

import java.util.List;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class StackTrieTest {

  final Bytes32 lastAccount = RangeManager.MIN_RANGE;

  @Test
  public void shouldNotSaveTheRootWhenIncomplete() {

    final int nbAccounts = 15;

    final WorldStateStorage worldStateStorage =
        new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());

    final WorldStateStorage recreatedWorldStateStorage =
        new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());

    final MerklePatriciaTrie<Bytes32, Bytes> accountStateTrie =
        TrieGenerator.generateTrie(worldStateStorage, nbAccounts);

    final StackTrie stackTrie =
        new StackTrie(Hash.wrap(accountStateTrie.getRootHash()), 0, 256, lastAccount);
    stackTrie.addSegment();

    final RangeStorageEntriesCollector collector =
        RangeStorageEntriesCollector.createCollector(
            lastAccount, RangeManager.MAX_RANGE, 5, Integer.MAX_VALUE);
    final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            accountStateTrie.entriesFrom(
                root ->
                    RangeStorageEntriesCollector.collectEntries(
                        collector, visitor, root, lastAccount));

    final WorldStateProofProvider worldStateProofProvider =
        new WorldStateProofProvider(worldStateStorage);

    // generate the proof
    final List<Bytes> proofs =
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), lastAccount);
    proofs.addAll(
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), accounts.lastKey()));

    stackTrie.addElement(Bytes32.random(), proofs, accounts);

    final WorldStateStorage.Updater updater = recreatedWorldStateStorage.updater();
    stackTrie.commit(updater::putAccountStateTrieNode);
    updater.commit();

    Assertions.assertThat(
            recreatedWorldStateStorage.getAccountStateTrieNode(
                Bytes.EMPTY, accountStateTrie.getRootHash()))
        .isEmpty();
  }

  @Test
  public void shouldSaveTheRootWhenComplete() {

    final int nbAccounts = 15;

    final WorldStateStorage worldStateStorage =
        new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());

    final WorldStateStorage recreatedWorldStateStorage =
        new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());

    final MerklePatriciaTrie<Bytes32, Bytes> accountStateTrie =
        TrieGenerator.generateTrie(worldStateStorage, nbAccounts);

    final StackTrie stackTrie =
        new StackTrie(Hash.wrap(accountStateTrie.getRootHash()), 0, 256, lastAccount);

    for (int i = 0; i < nbAccounts; i += 5) {
      stackTrie.addSegment();
      final RangeStorageEntriesCollector collector =
          RangeStorageEntriesCollector.createCollector(
              lastAccount, RangeManager.MAX_RANGE, 5, Integer.MAX_VALUE);
      final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
      final TreeMap<Bytes32, Bytes> accounts =
          (TreeMap<Bytes32, Bytes>)
              accountStateTrie.entriesFrom(
                  root ->
                      RangeStorageEntriesCollector.collectEntries(
                          collector, visitor, root, lastAccount));

      final WorldStateProofProvider worldStateProofProvider =
          new WorldStateProofProvider(worldStateStorage);

      // generate the proof
      final List<Bytes> proofs =
          worldStateProofProvider.getAccountProofRelatedNodes(
              Hash.wrap(accountStateTrie.getRootHash()), lastAccount);
      proofs.addAll(
          worldStateProofProvider.getAccountProofRelatedNodes(
              Hash.wrap(accountStateTrie.getRootHash()), accounts.lastKey()));

      stackTrie.addElement(Bytes32.random(), proofs, accounts);

      final WorldStateStorage.Updater updater = recreatedWorldStateStorage.updater();
      stackTrie.commit(updater::putAccountStateTrieNode);
      updater.commit();
    }

    Assertions.assertThat(
            worldStateStorage.getAccountStateTrieNode(Bytes.EMPTY, accountStateTrie.getRootHash()))
        .isPresent();
  }
}
