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
package org.hyperledger.besu.ethereum.eth.sync.snapsync.request;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapWorldDownloadState;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class AccountTrieNodeDataRequest extends TrieNodeDataRequest {

  private final HashSet<Bytes> inconsistentAccounts;

  AccountTrieNodeDataRequest(
      final Hash hash,
      final Hash originalRootHash,
      final Bytes location,
      final HashSet<Bytes> inconsistentAccounts) {
    super(hash, originalRootHash, location);
    this.inconsistentAccounts = inconsistentAccounts;
  }

  @Override
  protected int doPersist(
      final WorldStateStorage worldStateStorage,
      final WorldStateStorage.Updater updater,
      final SnapWorldDownloadState downloadState,
      final SnapSyncState snapSyncState) {
    if (isRoot()) {
      downloadState.setRootNodeData(data);
    }
    updater.putAccountStateTrieNode(getLocation(), getNodeHash(), data);
    return 1;
  }

  @Override
  public Optional<Bytes> getExistingData(final WorldStateStorage worldStateStorage) {
    return worldStateStorage
        .getAccountStateTrieNode(getLocation(), getNodeHash())
        .filter(data -> !getLocation().isEmpty())
        .filter(data -> Hash.hash(data).equals(getNodeHash()));
  }

  @Override
  protected SnapDataRequest createChildNodeDataRequest(final Hash childHash, final Bytes location) {
    return createAccountTrieNodeDataRequest(
        childHash, getRootHash(), location, getSubLocation(location));
  }

  private HashSet<Bytes> getSubLocation(final Bytes location) {
    final HashSet<Bytes> foundAccountsToHeal = new HashSet<>();
    for (Bytes account : inconsistentAccounts) {
      if (account.commonPrefixLength(location) == location.size()) {
        foundAccountsToHeal.add(account);
      }
    }
    return foundAccountsToHeal;
  }

  @Override
  public Stream<SnapDataRequest> getRootStorageRequests(final WorldStateStorage worldStateStorage) {
    final List<SnapDataRequest> requests = new ArrayList<>();
    final StoredMerklePatriciaTrie<Bytes, Bytes> accountTrie =
        new StoredMerklePatriciaTrie<>(
            worldStateStorage::getAccountStateTrieNode,
            Hash.hash(data),
            getLocation(),
            Function.identity(),
            Function.identity());
    for (Bytes account : inconsistentAccounts) {
      final Bytes32 accountHash = Bytes32.wrap(CompactEncoding.pathToBytes(account));
      accountTrie
          .getPath(
              Bytes.wrap(
                  account.toArrayUnsafe(),
                  getLocation().size(),
                  account.size() - getLocation().size()))
          .map(RLP::input)
          .map(StateTrieAccountValue::readFrom)
          .ifPresent(
              stateTrieAccountValue -> {
                // an account need a heal step
                requests.add(
                    createStorageTrieNodeDataRequest(
                        stateTrieAccountValue.getStorageRoot(),
                        Hash.wrap(accountHash),
                        getRootHash(),
                        Bytes.EMPTY));
              });
    }
    return requests.stream();
  }

  @Override
  protected Stream<SnapDataRequest> getRequestsFromTrieNodeValue(
      final WorldStateStorage worldStateStorage,
      final Bytes location,
      final Bytes path,
      final Bytes value) {
    final Stream.Builder<SnapDataRequest> builder = Stream.builder();
    final StateTrieAccountValue accountValue = StateTrieAccountValue.readFrom(RLP.input(value));

    // Retrieve account hash
    final Hash accountHash =
        Hash.wrap(
            Bytes32.wrap(CompactEncoding.pathToBytes(Bytes.concatenate(getLocation(), path))));
    if (worldStateStorage instanceof BonsaiWorldStateKeyValueStorage) {
      ((BonsaiWorldStateKeyValueStorage.Updater) worldStateStorage.updater())
          .putAccountInfoState(accountHash, value)
          .commit();
    }

    // Add code, if appropriate
    if (!accountValue.getCodeHash().equals(Hash.EMPTY)) {
      builder.add(createBytecodeRequest(accountHash, getRootHash(), accountValue.getCodeHash()));
    }
    // Add storage, if appropriate
    if (!accountValue.getStorageRoot().equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      // If we detect an account storage we fill it with snapsync before completing with a heal
      final SnapDataRequest storageTrieRequest =
          createStorageTrieNodeDataRequest(
              accountValue.getStorageRoot(), accountHash, getRootHash(), Bytes.EMPTY);
      builder.add(storageTrieRequest);
    }
    return builder.build();
  }

  @Override
  public List<Bytes> getTrieNodePath() {
    return List.of(CompactEncoding.encode(getLocation()));
  }
}
