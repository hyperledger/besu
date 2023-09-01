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
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapsyncMetricsManager.Step.HEAL_FLAT;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.RequestType;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncProcessState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapWorldDownloadState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.RangeStorageEntriesCollector;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * The AccountFlatDatabaseHealingRangeRequest class represents a request to heal a range of account
 * in the flat databases. It encapsulates the necessary information to identify the range and
 * initiate the healing process.
 */
public class AccountFlatDatabaseHealingRangeRequest extends SnapDataRequest {

  private final Bytes32 startKeyHash;
  private final Bytes32 endKeyHash;
  private TreeMap<Bytes32, Bytes> accountsInDb;

  private TreeMap<Bytes32, Bytes> accountsToDelete;
  private boolean isProofValid;

  public AccountFlatDatabaseHealingRangeRequest(
      final Hash originalRootHash, final Bytes32 startKeyHash, final Bytes32 endKeyHash) {
    super(RequestType.ACCOUNT_RANGE, originalRootHash);
    this.startKeyHash = startKeyHash;
    this.endKeyHash = endKeyHash;
    this.accountsInDb = new TreeMap<>();
    this.accountsToDelete = new TreeMap<>();
    this.isProofValid = false;
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(
      final SnapWorldDownloadState downloadState,
      final WorldStateStorage worldStateStorage,
      final SnapSyncProcessState snapSyncState) {
    final List<SnapDataRequest> childRequests = new ArrayList<>();

    // If we have found some accounts, we will try to check if we have indeed gone through the
    // entire range by making
    // a new request with the address of the last account in the list as the startKeyHash
    if (!accountsInDb.isEmpty()) {
      RangeManager.generateRanges(
              accountsInDb.lastKey().toUnsignedBigInteger().add(BigInteger.ONE),
              endKeyHash.toUnsignedBigInteger(),
              1)
          .forEach(
              (key, value) -> {
                downloadState.getMetricsManager().notifyRangeProgress(HEAL_FLAT, key, endKeyHash);
                final AccountFlatDatabaseHealingRangeRequest storageRangeDataRequest =
                    createAccountFlatHealingRangeRequest(getRootHash(), key, value);
                childRequests.add(storageRangeDataRequest);
              });
    } else {
      downloadState.getMetricsManager().notifyRangeProgress(HEAL_FLAT, endKeyHash, endKeyHash);
    }

    // We will go through all the accounts in the range and look for those that need to be repaired.
    // For those that need to be repaired, we will check their storage and fix it.
    Stream.of(accountsInDb.entrySet(), accountsToDelete.entrySet())
        .flatMap(Collection::stream)
        .forEach(
            account -> {
              if (downloadState
                  .getAccountsToBeRepaired()
                  .contains(CompactEncoding.bytesToPath(account.getKey()))) {
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
            });
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
    if (!accounts.isEmpty() && !proofs.isEmpty()) {
      // very proof in order to check if the local flat database is valid or not
      isProofValid =
          worldStateProofProvider.isValidRangeProof(
              startKeyHash, endKeyHash, getRootHash(), proofs, accounts);
      this.accountsInDb = accounts;
    }
  }

  @Override
  protected int doPersist(
      final WorldStateStorage worldStateStorage,
      final WorldStateStorage.Updater updater,
      final SnapWorldDownloadState downloadState,
      final SnapSyncProcessState snapSyncState,
      final SnapSyncConfiguration syncConfig) {

    if (!isProofValid) { // If the proof is not valid, we must compare the trie with the flat
      // database to find what we need to fix.

      final BonsaiWorldStateKeyValueStorage.Updater bonsaiUpdater =
          (BonsaiWorldStateKeyValueStorage.Updater) updater;

      final TreeMap<Bytes32, Bytes> accountsInFlatDb = new TreeMap<>(accountsInDb);
      // put all flat accounts in the remove list, and gradually keep only those that are not in the
      // trie
      // to remove them.
      accountsToDelete = new TreeMap<>(accountsInDb);

      // retrieve the accounts from the trie in order to know what to fix in the flat database
      final MerkleTrie<Bytes, Bytes> accountTrie =
          new StoredMerklePatriciaTrie<>(
              worldStateStorage::getAccountStateTrieNode,
              getRootHash(),
              Function.identity(),
              Function.identity());
      final RangeStorageEntriesCollector collector =
          RangeStorageEntriesCollector.createCollector(
              startKeyHash,
              accountsInDb.isEmpty() ? endKeyHash : accountsInFlatDb.lastKey(),
              accountsInFlatDb.isEmpty()
                  ? syncConfig.getLocalFlatAccountCountToHealPerRequest()
                  : Integer.MAX_VALUE,
              Integer.MAX_VALUE);

      final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
      final TreeMap<Bytes32, Bytes> accountsInTrieDb =
          (TreeMap<Bytes32, Bytes>)
              accountTrie.entriesFrom(
                  root ->
                      RangeStorageEntriesCollector.collectEntries(
                          collector, visitor, root, startKeyHash));

      // Iterate over the accounts in the trie. For each account, check if it matches the
      // corresponding account in the flat database.
      // For each account in the trie if it does, remove it from the deletion list. If it doesn't,
      // mark it for repair and update its state in the flat db. all the account
      // missing will be deleted
      accountsInTrieDb.forEach(
          (key, trieValue) -> {
            final Bytes flatValue = accountsToDelete.get(key);
            if (flatValue != null) {
              accountsToDelete.remove(key);
            }
            if (!trieValue.equals(flatValue)) {
              final Hash accountHash = Hash.wrap(key);
              // if the account was invalid in the flat db we need to heal the storage
              downloadState.addAccountToHealingList(CompactEncoding.bytesToPath(accountHash));
              bonsaiUpdater.putAccountInfoState(accountHash, trieValue);
            }
          });

      // Iterate over the accounts marked for deletion. For each account, mark it for repair and
      // remove its state from the flat db.
      // This is done to ensure that the state of the account will be consistent with the trie.
      accountsToDelete.forEach(
          (key, value) -> {
            final Hash accountHash = Hash.wrap(key);
            // if the account was removed we will have to heal the storage
            downloadState.addAccountToHealingList(CompactEncoding.bytesToPath(accountHash));
            bonsaiUpdater.removeAccountInfoState(accountHash);
          });

      accountsInDb =
          accountsInTrieDb; // switch to use the trie representation as the flat db is not valid
    }
    return accountsInDb.size() + accountsToDelete.size();
  }
}
