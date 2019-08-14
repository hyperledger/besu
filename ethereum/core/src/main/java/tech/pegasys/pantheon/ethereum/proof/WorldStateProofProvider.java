/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.proof;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.pantheon.ethereum.trie.Proof;
import tech.pegasys.pantheon.ethereum.trie.StoredMerklePatriciaTrie;
import tech.pegasys.pantheon.ethereum.worldstate.StateTrieAccountValue;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

public class WorldStateProofProvider {

  private final WorldStateStorage worldStateStorage;

  public WorldStateProofProvider(final WorldStateStorage worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
  }

  public Optional<WorldStateProof> getAccountProof(
      final Hash worldStateRoot,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys) {

    if (!worldStateStorage.isWorldStateAvailable(worldStateRoot)) {
      return Optional.empty();
    } else {
      final Hash addressHash = Hash.hash(accountAddress);
      final Proof<BytesValue> accountProof =
          newAccountStateTrie(worldStateRoot).getValueWithProof(addressHash);

      return accountProof
          .getValue()
          .map(RLP::input)
          .map(StateTrieAccountValue::readFrom)
          .map(
              account -> {
                final SortedMap<UInt256, Proof<BytesValue>> storageProofs =
                    getStorageProofs(account, accountStorageKeys);
                return new WorldStateProof(account, accountProof, storageProofs);
              });
    }
  }

  private SortedMap<UInt256, Proof<BytesValue>> getStorageProofs(
      final StateTrieAccountValue account, final List<UInt256> accountStorageKeys) {
    final MerklePatriciaTrie<Bytes32, BytesValue> storageTrie =
        newAccountStorageTrie(account.getStorageRoot());
    final SortedMap<UInt256, Proof<BytesValue>> storageProofs = new TreeMap<>();
    accountStorageKeys.forEach(
        key -> storageProofs.put(key, storageTrie.getValueWithProof(Hash.hash(key.getBytes()))));
    return storageProofs;
  }

  private MerklePatriciaTrie<Bytes32, BytesValue> newAccountStateTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStateTrieNode, rootHash, b -> b, b -> b);
  }

  private MerklePatriciaTrie<Bytes32, BytesValue> newAccountStorageTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStorageTrieNode, rootHash, b -> b, b -> b);
  }
}
