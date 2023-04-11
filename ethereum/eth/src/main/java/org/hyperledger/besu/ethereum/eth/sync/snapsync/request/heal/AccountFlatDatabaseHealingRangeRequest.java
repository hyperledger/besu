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
package org.hyperledger.besu.ethereum.eth.sync.snapsync.request.heal;

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.MAX_RANGE;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.MIN_RANGE;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.findNewBeginElementInRange;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.RequestType;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapWorldDownloadState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.RangeStorageEntriesCollector;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** Returns a list of accounts and the merkle proofs of an entire range */
@SuppressWarnings({"MismatchedQueryAndUpdateOfCollection", "TooBroadScope", "ModifiedButNotUsed"})
public class AccountFlatDatabaseHealingRangeRequest extends SnapDataRequest {

  private final Bytes32 startKeyHash;
  private final Bytes32 endKeyHash;

  private List<Bytes> proofs;
  private TreeMap<Bytes32, Bytes> accounts;

  private Optional<Boolean> isProofValid;

  public AccountFlatDatabaseHealingRangeRequest(
      final Hash originalRootHash, final Bytes32 startKeyHash, final Bytes32 endKeyHash) {
    super(RequestType.ACCOUNT_RANGE, originalRootHash);
    this.startKeyHash = startKeyHash;
    this.endKeyHash = endKeyHash;
    this.proofs = new ArrayList<>();
    this.accounts = new TreeMap<>();
    this.isProofValid = Optional.empty();
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(
      final SnapWorldDownloadState downloadState,
      final WorldStateStorage worldStateStorage,
      final SnapSyncState snapSyncState) {
    final List<SnapDataRequest> childRequests = new ArrayList<>();

    // new request is added if the response does not match all the requested range
    findNewBeginElementInRange(getRootHash(), proofs, accounts, endKeyHash)
        .ifPresentOrElse(
            missingRightElement -> {
              downloadState
                  .getMetricsManager()
                  .notifyStateDownloaded(missingRightElement, endKeyHash);
              childRequests.add(
                  createAccountFlatHealingRangeRequest(
                      getRootHash(), missingRightElement, endKeyHash));
            },
            () -> downloadState.getMetricsManager().notifyStateDownloaded(endKeyHash, endKeyHash));

    // find missing storages and code
    for (Map.Entry<Bytes32, Bytes> account : accounts.entrySet()) {
      final StateTrieAccountValue accountValue =
          StateTrieAccountValue.readFrom(RLP.input(account.getValue()));
      childRequests.add(
          createStorageFlatHealingRangeRequest(
              getRootHash(),
              account.getKey(),
              accountValue.getStorageRoot(),
              MIN_RANGE,
              MAX_RANGE));
    }

    return childRequests.stream();
  }

  public Bytes32 getStartKeyHash() {
    return startKeyHash;
  }

  public Bytes32 getEndKeyHash() {
    return endKeyHash;
  }

  @Override
  public boolean isResponseReceived() {
    return true;
  }

  public void addLocalData(
      final WorldStateProofProvider worldStateProofProvider,
      final TreeMap<Bytes32, Bytes> accounts,
      final ArrayDeque<Bytes> proofs) {
    if (!accounts.isEmpty() || !proofs.isEmpty()) {
      if (!worldStateProofProvider.isValidRangeProof(
          startKeyHash, endKeyHash, getRootHash(), proofs, accounts)) {
        System.out.println("proof invalid " + proofs.toString() + " " + accounts.size());
        isProofValid = Optional.of(false);
      } else {
        isProofValid = Optional.of(true);
      }
      this.accounts = accounts;
      this.proofs = proofs;
    }
  }

  @Override
  protected int doPersist(
      final WorldStateStorage worldStateStorage,
      final WorldStateStorage.Updater updater,
      final SnapWorldDownloadState downloadState,
      final SnapSyncState snapSyncState) {

    if (!isProofValid.orElse(false)) {

      final BonsaiWorldStateKeyValueStorage.Updater bonsaiUpdater =
          (BonsaiWorldStateKeyValueStorage.Updater) updater;

      System.out.println(
          "Range not valid from "
              + startKeyHash
              + " to "
              + accounts.lastKey()
              + " "
              + isResponseReceived()
              + " fixed ");

      final MerkleTrie<Bytes, Bytes> accountTrie =
          new StoredMerklePatriciaTrie<>(
              worldStateStorage::getAccountStateTrieNode,
              getRootHash(),
              Function.identity(),
              Function.identity());

      final RangeStorageEntriesCollector collector =
          RangeStorageEntriesCollector.createCollector(
              startKeyHash, endKeyHash, Integer.MAX_VALUE, Integer.MAX_VALUE);
      final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
      final TreeMap<Bytes32, Bytes> accounts =
          (TreeMap<Bytes32, Bytes>)
              accountTrie.entriesFrom(
                  root ->
                      RangeStorageEntriesCollector.collectEntries(
                          collector, visitor, root, startKeyHash));

      Map<Bytes32, Bytes> keysAdd = new TreeMap<>();
      Map<Bytes32, Bytes> keysToDelete = new TreeMap<>(accounts);
      accounts.forEach(
          (key, value) -> {
            if (keysToDelete.containsKey(key)) {
              keysToDelete.remove(key);
            } else {
              keysAdd.put(key, value);
              bonsaiUpdater.putAccountInfoState(Hash.wrap(key), value);
            }
          });

      keysToDelete.forEach((key, value) -> bonsaiUpdater.removeAccountInfoState(Hash.wrap(key)));
    }
    return 0;
  }
}
