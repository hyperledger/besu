/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredNodeFactory;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;

import java.util.Optional;
import java.util.function.Function;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.rlp.RLP;

public class BonsaiSnapshotWorldStateKeyValueStorage extends BonsaiWorldStateKeyValueStorage
    implements WorldStateStorage {

  private final Hash worldStateRootHash;

  public BonsaiSnapshotWorldStateKeyValueStorage(
      final Hash worldStateRootHash, final StorageProvider provider) {
    super(provider);
    this.worldStateRootHash = worldStateRootHash;
  }

  public BonsaiSnapshotWorldStateKeyValueStorage(
      final Hash worldStateRootHash,
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage,
      final KeyValueStorage trieBranchStorage,
      final Pair<KeyValueStorage, KeyValueStorage> snapshotTrieBranchStorage,
      final KeyValueStorage trieLogStorage) {
    super(
        accountStorage,
        codeStorage,
        storageStorage,
        trieBranchStorage,
        snapshotTrieBranchStorage,
        trieLogStorage);
    this.worldStateRootHash = worldStateRootHash;
  }

  public Optional<Bytes> getAccount(final Hash accountHash) {
    return new StoredMerklePatriciaTrie<>(
            new StoredNodeFactory<>(
                this::getAccountStateTrieNode, Function.identity(), Function.identity()),
            worldStateRootHash)
        .get(accountHash);
  }

  public Optional<Bytes> getStorageValueBySlotHash(final Hash accountHash, final Hash slotHash) {
    // after a snapsync/fastsync we only have the trie branches.
    final Optional<Bytes> account = getAccount(accountHash);
    if (account.isPresent()) {
      final StateTrieAccountValue accountValue =
          StateTrieAccountValue.readFrom(
              org.hyperledger.besu.ethereum.rlp.RLP.input(account.get()));
      return new StoredMerklePatriciaTrie<>(
              new StoredNodeFactory<>(
                  (location, hash) -> getAccountStorageTrieNode(accountHash, location, hash),
                  Function.identity(),
                  Function.identity()),
              accountValue.getStorageRoot())
          .get(slotHash)
          .map(bytes -> Bytes32.leftPad(RLP.decodeValue(bytes)));
    }
    return Optional.empty();
  }

  @Override
  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return snapshotTrieBranchStorage
          .getFirst()
          .get(nodeHash.toArrayUnsafe())
          .or(() -> snapshotTrieBranchStorage.getSecond().get(nodeHash.toArrayUnsafe()))
          .or(() -> trieBranchStorage.get(location.toArrayUnsafe()))
          .map(Bytes::wrap);
    }
  }

  @Override
  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return snapshotTrieBranchStorage
          .getFirst()
          .get(nodeHash.toArrayUnsafe())
          .or(() -> snapshotTrieBranchStorage.getSecond().get(nodeHash.toArrayUnsafe()))
          .or(() -> trieBranchStorage.get(Bytes.concatenate(accountHash, location).toArrayUnsafe()))
          .map(Bytes::wrap);
    }
  }
}
