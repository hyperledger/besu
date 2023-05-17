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
import java.util.List;
import java.util.Map;
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
  private TreeMap<Bytes32, Bytes> accounts;

  private boolean isProofValid;

  public AccountFlatDatabaseHealingRangeRequest(
      final Hash originalRootHash, final Bytes32 startKeyHash, final Bytes32 endKeyHash) {
    super(RequestType.ACCOUNT_RANGE, originalRootHash);
    this.startKeyHash = startKeyHash;
    this.endKeyHash = endKeyHash;
    this.accounts = new TreeMap<>();
    this.isProofValid = false;
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(
      final SnapWorldDownloadState downloadState,
      final WorldStateStorage worldStateStorage,
      final SnapSyncProcessState snapSyncState) {
    final List<SnapDataRequest> childRequests = new ArrayList<>();
    if (!accounts.isEmpty()) {
      // new request is added if the response does not match all the requested range
      RangeManager.generateRanges(
              accounts.lastKey().toUnsignedBigInteger().add(BigInteger.ONE),
              endKeyHash.toUnsignedBigInteger(),
              1)
          .forEach(
              (key, value) -> {
                System.out.println(
                    "Generate account range "
                        + " "
                        + " "
                        + key
                        + " "
                        + value
                        + " "
                        + ((accounts.isEmpty()) ? "empty" : accounts.firstKey())
                        + " "
                        + ((accounts.isEmpty()) ? "empty" : accounts.lastKey())
                        + " // "
                        + isProofValid);
                downloadState.getMetricsManager().notifyRangeProgress(HEAL_FLAT, key, endKeyHash);
                final AccountFlatDatabaseHealingRangeRequest storageRangeDataRequest =
                    createAccountFlatHealingRangeRequest(getRootHash(), key, value);
                childRequests.add(storageRangeDataRequest);
              });
    } else {
      downloadState.getMetricsManager().notifyRangeProgress(HEAL_FLAT, endKeyHash, endKeyHash);
    }

    // find storages to heal
    for (Map.Entry<Bytes32, Bytes> account : accounts.entrySet()) {
      // we maintain a list of storages that need to be healed. no need to try to heal everything
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
    if (!accounts.isEmpty() && !proofs.isEmpty()) {
      // very proof in order to check if the local flat database is valid or not
      isProofValid =
          worldStateProofProvider.isValidRangeProof(
              startKeyHash, endKeyHash, getRootHash(), proofs, accounts);
      this.accounts = accounts;
    }
  }

  @Override
  protected int doPersist(
      final WorldStateStorage worldStateStorage,
      final WorldStateStorage.Updater updater,
      final SnapWorldDownloadState downloadState,
      final SnapSyncProcessState snapSyncState) {

    if (!isProofValid) { // if proof is not valid we need to fix the flat database

      final BonsaiWorldStateKeyValueStorage.Updater bonsaiUpdater =
          (BonsaiWorldStateKeyValueStorage.Updater) updater;

      final MerkleTrie<Bytes, Bytes> accountTrie =
          new StoredMerklePatriciaTrie<>(
              worldStateStorage::getAccountStateTrieNode,
              getRootHash(),
              Function.identity(),
              Function.identity());

      // retrieve the data from the trie in order to know what to fix in the flat database
      final RangeStorageEntriesCollector collector =
          RangeStorageEntriesCollector.createCollector(
              startKeyHash,
              accounts.isEmpty() ? endKeyHash : accounts.lastKey(),
              accounts.isEmpty() ? 128 : Integer.MAX_VALUE,
              Integer.MAX_VALUE);

      Map<Bytes32, Bytes> remainingKeys = new TreeMap<>(accounts);

      final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
      accounts =
          (TreeMap<Bytes32, Bytes>)
              accountTrie.entriesFrom(
                  root ->
                      RangeStorageEntriesCollector.collectEntries(
                          collector, visitor, root, startKeyHash));

      // doing the fix
      accounts.forEach(
          (key, value) -> {
            if (remainingKeys.containsKey(key)) {
              remainingKeys.remove(key);
            } else {
              final Hash accountHash = Hash.wrap(key);
              // if the account was missing in the flat db we need to heal the storage
              downloadState.addAccountsToBeRepaired(CompactEncoding.bytesToPath(accountHash));
              bonsaiUpdater.putAccountInfoState(accountHash, value);
            }
          });

      remainingKeys.forEach(
          (key, value) -> {
            final Hash accountHash = Hash.wrap(key);
            // if the account was removed we will have to heal the storage
            downloadState.addAccountsToBeRepaired(CompactEncoding.bytesToPath(accountHash));
            bonsaiUpdater.removeAccountInfoState(accountHash);
          });
    }
    return 0;
  }
}
