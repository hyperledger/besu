/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.MAX_RANGE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.TrieGenerator;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.StorageRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.heal.StorageTrieNodeHealingRequest;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.RangeStorageEntriesCollector;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.patricia.StoredNodeFactory;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AccountHealingTrackingTest {

  private final List<Address> accounts = List.of(Address.fromHexString("0xdeadbeef"));
  private final WorldStateStorage worldStateStorage =
      new BonsaiWorldStateKeyValueStorage(
          new InMemoryKeyValueStorageProvider(),
          new NoOpMetricsSystem(),
          DataStorageConfiguration.DEFAULT_BONSAI_CONFIG);

  private WorldStateProofProvider worldStateProofProvider;

  private MerkleTrie<Bytes, Bytes> accountStateTrie;

  @Mock SnapWorldDownloadState snapWorldDownloadState;

  @BeforeEach
  public void setup() {
    accountStateTrie =
        TrieGenerator.generateTrie(
            worldStateStorage,
            accounts.stream().map(Address::addressHash).collect(Collectors.toList()));
    worldStateProofProvider = new WorldStateProofProvider(worldStateStorage);
  }

  @Test
  void avoidMarkingAccountWhenStorageProofValid() {

    // generate valid proof
    final Hash accountHash = Hash.hash(accounts.get(0));
    final StateTrieAccountValue stateTrieAccountValue =
        StateTrieAccountValue.readFrom(RLP.input(accountStateTrie.get(accountHash).orElseThrow()));

    final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
        new StoredMerklePatriciaTrie<>(
            new StoredNodeFactory<>(
                (location, hash) ->
                    worldStateStorage.getAccountStorageTrieNode(accountHash, location, hash),
                Function.identity(),
                Function.identity()),
            stateTrieAccountValue.getStorageRoot());

    final RangeStorageEntriesCollector collector =
        RangeStorageEntriesCollector.createCollector(Hash.ZERO, MAX_RANGE, 10, Integer.MAX_VALUE);
    final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
    final TreeMap<Bytes32, Bytes> slots =
        (TreeMap<Bytes32, Bytes>)
            storageTrie.entriesFrom(
                root ->
                    RangeStorageEntriesCollector.collectEntries(
                        collector, visitor, root, Hash.ZERO));
    // generate the proof
    final List<Bytes> proofs =
        worldStateProofProvider.getStorageProofRelatedNodes(
            Hash.wrap(storageTrie.getRootHash()), accountHash, Hash.ZERO);
    proofs.addAll(
        worldStateProofProvider.getStorageProofRelatedNodes(
            Hash.wrap(storageTrie.getRootHash()), accountHash, slots.lastKey()));

    final StorageRangeDataRequest storageRangeDataRequest =
        SnapDataRequest.createStorageRangeDataRequest(
            Hash.wrap(accountStateTrie.getRootHash()),
            accountHash,
            storageTrie.getRootHash(),
            Hash.ZERO,
            MAX_RANGE);
    storageRangeDataRequest.addResponse(
        snapWorldDownloadState, worldStateProofProvider, slots, new ArrayDeque<>(proofs));
    storageRangeDataRequest.getChildRequests(snapWorldDownloadState, worldStateStorage, null);
    verify(snapWorldDownloadState, never()).addAccountToHealingList(any(Bytes.class));
  }

  @Test
  void markAccountOnInvalidStorageProof() {
    final Hash accountHash = Hash.hash(accounts.get(0));
    final StateTrieAccountValue stateTrieAccountValue =
        StateTrieAccountValue.readFrom(RLP.input(accountStateTrie.get(accountHash).orElseThrow()));

    final List<Bytes> proofs =
        List.of(
            worldStateStorage
                .getAccountStorageTrieNode(
                    accountHash, Bytes.EMPTY, stateTrieAccountValue.getStorageRoot())
                .get());

    final StorageRangeDataRequest storageRangeDataRequest =
        SnapDataRequest.createStorageRangeDataRequest(
            Hash.wrap(accountStateTrie.getRootHash()),
            accountHash,
            stateTrieAccountValue.getStorageRoot(),
            Hash.ZERO,
            MAX_RANGE);
    storageRangeDataRequest.addResponse(
        snapWorldDownloadState, worldStateProofProvider, new TreeMap<>(), new ArrayDeque<>(proofs));

    verify(snapWorldDownloadState).addAccountToHealingList(any(Bytes.class));
  }

  @Test
  void markAccountOnPartialStorageRange() {
    // generate valid proof
    final Hash accountHash = Hash.hash(accounts.get(0));
    final StateTrieAccountValue stateTrieAccountValue =
        StateTrieAccountValue.readFrom(RLP.input(accountStateTrie.get(accountHash).orElseThrow()));

    final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
        new StoredMerklePatriciaTrie<>(
            new StoredNodeFactory<>(
                (location, hash) ->
                    worldStateStorage.getAccountStorageTrieNode(accountHash, location, hash),
                Function.identity(),
                Function.identity()),
            stateTrieAccountValue.getStorageRoot());

    final RangeStorageEntriesCollector collector =
        RangeStorageEntriesCollector.createCollector(
            Hash.ZERO,
            MAX_RANGE,
            1,
            Integer.MAX_VALUE); // limit to 1 in order to have a partial range
    final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
    final TreeMap<Bytes32, Bytes> slots =
        (TreeMap<Bytes32, Bytes>)
            storageTrie.entriesFrom(
                root ->
                    RangeStorageEntriesCollector.collectEntries(
                        collector, visitor, root, Hash.ZERO));
    // generate the proof
    final List<Bytes> proofs =
        worldStateProofProvider.getStorageProofRelatedNodes(
            Hash.wrap(storageTrie.getRootHash()), accountHash, Hash.ZERO);
    proofs.addAll(
        worldStateProofProvider.getStorageProofRelatedNodes(
            Hash.wrap(storageTrie.getRootHash()), accountHash, slots.lastKey()));

    final StorageRangeDataRequest storageRangeDataRequest =
        SnapDataRequest.createStorageRangeDataRequest(
            Hash.wrap(accountStateTrie.getRootHash()),
            accountHash,
            storageTrie.getRootHash(),
            Hash.ZERO,
            MAX_RANGE);
    storageRangeDataRequest.addResponse(
        snapWorldDownloadState, worldStateProofProvider, slots, new ArrayDeque<>(proofs));
    verify(snapWorldDownloadState, never()).addAccountToHealingList(any(Bytes.class));

    // should mark during the getchild request
    storageRangeDataRequest.getChildRequests(snapWorldDownloadState, worldStateStorage, null);
    verify(snapWorldDownloadState).addAccountToHealingList(any(Bytes.class));
  }

  @Test
  void avoidMarkingAccountOnValidStorageTrieNodeDetection() {
    final Hash accountHash = Hash.hash(accounts.get(0));
    final StateTrieAccountValue stateTrieAccountValue =
        StateTrieAccountValue.readFrom(RLP.input(accountStateTrie.get(accountHash).orElseThrow()));
    final StorageTrieNodeHealingRequest storageTrieNodeHealingRequest =
        SnapDataRequest.createStorageTrieNodeDataRequest(
            stateTrieAccountValue.getStorageRoot(),
            accountHash,
            Hash.wrap(accountStateTrie.getRootHash()),
            Bytes.EMPTY);
    storageTrieNodeHealingRequest.getExistingData(snapWorldDownloadState, worldStateStorage);
    verify(snapWorldDownloadState, never()).addAccountToHealingList(any(Bytes.class));
  }
}
