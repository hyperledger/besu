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
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.TrieGenerator;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.AccountRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.BytecodeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.StorageRangeDataRequest;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.RangeStorageEntriesCollector;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.Task;

import java.util.List;
import java.util.TreeMap;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class TaskGenerator {

  public static List<Task<SnapDataRequest>> createAccountRequest(final boolean withData) {

    final WorldStateStorage worldStateStorage =
        new InMemoryKeyValueStorageProvider().createWorldStateStorage(DataStorageFormat.FOREST);

    final WorldStateProofProvider worldStateProofProvider =
        new WorldStateProofProvider(worldStateStorage);

    final MerklePatriciaTrie<Bytes32, Bytes> trie =
        TrieGenerator.generateTrie(worldStateStorage, 1);
    final RangeStorageEntriesCollector collector =
        RangeStorageEntriesCollector.createCollector(
            Bytes32.ZERO, RangeManager.MAX_RANGE, 1, Integer.MAX_VALUE);
    final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            trie.entriesFrom(
                root ->
                    RangeStorageEntriesCollector.collectEntries(
                        collector, visitor, root, Bytes32.ZERO));

    final Hash rootHash = Hash.wrap(trie.getRootHash());

    final AccountRangeDataRequest accountRangeDataRequest =
        SnapDataRequest.createAccountRangeDataRequest(
            rootHash, RangeManager.MIN_RANGE, RangeManager.MAX_RANGE);
    if (withData) {
      accountRangeDataRequest.addResponse(worldStateProofProvider, accounts, new ArrayDeque<>());
    }

    final StateTrieAccountValue stateTrieAccountValue =
        StateTrieAccountValue.readFrom(RLP.input(accounts.firstEntry().getValue()));
    final Hash accountHash = Hash.wrap(accounts.firstKey());

    final StorageRangeDataRequest storageRangeDataRequest =
        createStorageRangeDataRequest(
            worldStateProofProvider,
            worldStateStorage,
            rootHash,
            accountHash,
            stateTrieAccountValue.getStorageRoot(),
            withData);
    final BytecodeRequest bytecodeRequest =
        createBytecodeDataRequest(
            worldStateStorage,
            rootHash,
            accountHash,
            stateTrieAccountValue.getCodeHash(),
            withData);

    return List.of(
        new StubTask(accountRangeDataRequest),
        new StubTask(storageRangeDataRequest),
        new StubTask(bytecodeRequest));
  }

  private static StorageRangeDataRequest createStorageRangeDataRequest(
      final WorldStateProofProvider worldStateProofProvider,
      final WorldStateStorage worldStateStorage,
      final Hash rootHash,
      final Hash accountHash,
      final Bytes32 storageRoot,
      final boolean withData) {

    final RangeStorageEntriesCollector collector =
        RangeStorageEntriesCollector.createCollector(
            Bytes32.ZERO, RangeManager.MAX_RANGE, 100, Integer.MAX_VALUE);
    final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) ->
                worldStateStorage.getAccountStorageTrieNode(accountHash, location, hash),
            storageRoot,
            b -> b,
            b -> b);

    final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
    final TreeMap<Bytes32, Bytes> slots =
        (TreeMap<Bytes32, Bytes>)
            storageTrie.entriesFrom(
                root ->
                    RangeStorageEntriesCollector.collectEntries(
                        collector, visitor, root, Bytes32.ZERO));

    final StorageRangeDataRequest request =
        SnapDataRequest.createStorageRangeDataRequest(
            rootHash, accountHash, storageRoot, RangeManager.MIN_RANGE, RangeManager.MAX_RANGE);
    if (withData) {
      request.setProofValid(true);
      request.addResponse(null, worldStateProofProvider, slots, new ArrayDeque<>());
    }
    return request;
  }

  private static BytecodeRequest createBytecodeDataRequest(
      final WorldStateStorage worldStateStorage,
      final Hash rootHash,
      final Hash accountHash,
      final Hash codeHash,
      final boolean withData) {
    final BytecodeRequest request =
        SnapDataRequest.createBytecodeRequest(accountHash, rootHash, codeHash);
    if (withData) {
      request.setCode(worldStateStorage.getCode(codeHash, accountHash).get());
    }
    return request;
  }
}
