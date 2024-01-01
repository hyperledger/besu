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
package org.hyperledger.besu.ethereum.eth.sync.snapsync.request.heal;

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest.createAccountTrieNodeDataRequest;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncProcessState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapWorldDownloadState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
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

/** Represents a healing request for an account trie node. */
public class AccountTrieNodeHealingRequest extends TrieNodeHealingRequest {

  private final HashSet<Bytes> inconsistentAccounts;

  public AccountTrieNodeHealingRequest(
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
      final SnapSyncProcessState snapSyncState,
      final SnapSyncConfiguration snapSyncConfiguration) {
    if (isRoot()) {
      downloadState.setRootNodeData(data);
    }
    updater.putAccountStateTrieNode(getLocation(), getNodeHash(), data);
    return 1;
  }

  @Override
  public Optional<Bytes> getExistingData(
      final SnapWorldDownloadState downloadState, final WorldStateStorage worldStateStorage) {
    return worldStateStorage
        .getAccountStateTrieNode(getLocation(), getNodeHash())
        .filter(data -> !getLocation().isEmpty());
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
          .filter(
              stateTrieAccountValue ->
                  // We need to ensure that the accounts to be healed do not have empty storage.
                  // Therefore, it is unnecessary to create trie heal requests for storage in this
                  // case.
                  // If we were to do so, we would be attempting to request storage that does not
                  // exist from our peers,
                  // which would cause sync issues.
                  !stateTrieAccountValue.getStorageRoot().equals(MerkleTrie.EMPTY_TRIE_NODE_HASH))
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
      final SnapWorldDownloadState downloadState,
      final Bytes location,
      final Bytes path,
      final Bytes value) {
    final Stream.Builder<SnapDataRequest> builder = Stream.builder();
    final StateTrieAccountValue accountValue = StateTrieAccountValue.readFrom(RLP.input(value));

    // Retrieve account hash
    final Hash accountHash =
        Hash.wrap(
            Bytes32.wrap(CompactEncoding.pathToBytes(Bytes.concatenate(getLocation(), path))));

    // update the flat db only for bonsai
    if (!worldStateStorage.getFlatDbMode().equals(FlatDbMode.NO_FLATTENED)) {
      ((BonsaiWorldStateKeyValueStorage.Updater) worldStateStorage.updater())
          .putAccountInfoState(accountHash, value)
          .commit();
    }

    // Add code, if appropriate
    if (!accountValue.getCodeHash().equals(Hash.EMPTY)) {
      builder.add(createBytecodeRequest(accountHash, getRootHash(), accountValue.getCodeHash()));
    }

    // Retrieve the storage root from the database, if available
    final Hash storageRootFoundInDb =
        worldStateStorage
            .getTrieNodeUnsafe(Bytes.concatenate(accountHash, Bytes.EMPTY))
            .map(Hash::hash)
            .orElse(Hash.wrap(MerkleTrie.EMPTY_TRIE_NODE_HASH));
    if (!storageRootFoundInDb.equals(accountValue.getStorageRoot())) {
      // If the storage root is not found in the database, add the account to the list of accounts
      // to be repaired
      downloadState.addAccountToHealingList(CompactEncoding.bytesToPath(accountHash));
      // If the account's storage root is not empty,
      // fill it with trie heal before completing with a flat heal
      if (!accountValue.getStorageRoot().equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
        SnapDataRequest storageTrieRequest =
            createStorageTrieNodeDataRequest(
                accountValue.getStorageRoot(), accountHash, getRootHash(), Bytes.EMPTY);
        builder.add(storageTrieRequest);
      }
    }
    return builder.build();
  }

  @Override
  public List<Bytes> getTrieNodePath() {
    return List.of(CompactEncoding.encode(getLocation()));
  }
}
