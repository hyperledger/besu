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
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.AccountRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.TrieNodeDataRequest;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.RangeStorageEntriesCollector;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.Task;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PersistDataStep {

  private final SnapSyncState snapSyncState;
  private final WorldStateStorage worldStateStorage;
  private final SnapWorldDownloadState downloadState;

  public PersistDataStep(
      final SnapSyncState snapSyncState,
      final WorldStateStorage worldStateStorage,
      final SnapWorldDownloadState downloadState) {
    this.snapSyncState = snapSyncState;
    this.worldStateStorage = worldStateStorage;
    this.downloadState = downloadState;
  }

  public List<Task<SnapDataRequest>> persist(final List<Task<SnapDataRequest>> tasks) {
    final WorldStateStorage.Updater updater = worldStateStorage.updater();
    for (Task<SnapDataRequest> task : tasks) {
      if (task.getData().isResponseReceived()) {
        // enqueue child requests
        final Stream<SnapDataRequest> childRequests =
            task.getData().getChildRequests(downloadState, worldStateStorage, snapSyncState);
        if (!(task.getData() instanceof TrieNodeDataRequest)) {
          enqueueChildren(childRequests);
        } else {
          if (!task.getData().isExpired(snapSyncState)) {
            enqueueChildren(childRequests);
          } else {
            continue;
          }
        }

        // persist nodes
        final int persistedNodes =
            task.getData().persist(worldStateStorage, updater, downloadState, snapSyncState);
        if (persistedNodes > 0) {
          if (task.getData() instanceof TrieNodeDataRequest) {
            downloadState.getMetricsManager().notifyNodesHealed(persistedNodes);
          } else {
            downloadState.getMetricsManager().notifyNodesGenerated(persistedNodes);
          }
        }
      }
    }
    updater.commit();
    return tasks;
  }

  public List<Task<SnapDataRequest>> healFlatDatabase(final List<Task<SnapDataRequest>> tasks) {
    final BonsaiWorldStateKeyValueStorage.Updater updater =
        (BonsaiWorldStateKeyValueStorage.Updater) worldStateStorage.updater();
    for (Task<SnapDataRequest> task : tasks) {

      if (task.getData().isResponseReceived()) {
        // enqueue child requests
        final Stream<SnapDataRequest> childRequests =
            task.getData().getChildRequests(downloadState, worldStateStorage, snapSyncState);
        enqueueChildren(childRequests);

        AccountRangeDataRequest accountFlatDataRangeRequest = (AccountRangeDataRequest) task;
        System.out.println(
            "Range from "
                + accountFlatDataRangeRequest.getAccounts().firstKey()
                + " to "
                + accountFlatDataRangeRequest.getAccounts().lastKey()
                + " "
                + task.getData().isResponseReceived());

      } else {
        final MerkleTrie<Bytes, Bytes> accountTrie =
            new StoredMerklePatriciaTrie<>(
                worldStateStorage::getAccountTrieNodeData,
                task.getData().getRootHash(),
                Function.identity(),
                Function.identity());

        AccountRangeDataRequest accountFlatDataRangeRequest = (AccountRangeDataRequest) task;

        final RangeStorageEntriesCollector collector =
            RangeStorageEntriesCollector.createCollector(
                accountFlatDataRangeRequest.getAccounts().firstKey(),
                accountFlatDataRangeRequest.getAccounts().lastKey(),
                Integer.MAX_VALUE,
                Integer.MAX_VALUE);
        final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
        final TreeMap<Bytes32, Bytes> accounts =
            (TreeMap<Bytes32, Bytes>)
                accountTrie.entriesFrom(
                    root ->
                        RangeStorageEntriesCollector.collectEntries(
                            collector,
                            visitor,
                            root,
                            accountFlatDataRangeRequest.getAccounts().firstKey()));

        Map<Bytes32, Bytes> keysAdd = new TreeMap<>();
        Map<Bytes32, Bytes> keysToDelete = new TreeMap<>(accountFlatDataRangeRequest.getAccounts());
        accounts.forEach(
            (key, value) -> {
              if (keysToDelete.containsKey(key)) {
                keysToDelete.remove(key);
              } else {
                keysAdd.put(key, value);
                updater.putAccountInfoState(Hash.wrap(key), value);
              }
            });
        System.out.println(
            "Range from "
                + accountFlatDataRangeRequest.getAccounts().firstKey()
                + " to "
                + accountFlatDataRangeRequest.getAccounts().lastKey()
                + " "
                + task.getData().isResponseReceived()
                + " fixed "
                + keysAdd.size()
                + " "
                + keysToDelete.size());

        keysToDelete.forEach((key, value) -> updater.removeAccountInfoState(Hash.wrap(key)));
      }
    }
    updater.commit();
    return tasks;
  }

  public Task<SnapDataRequest> persist(final Task<SnapDataRequest> task) {
    return persist(List.of(task)).get(0);
  }

  public Task<SnapDataRequest> healFlatDatabase(final Task<SnapDataRequest> task) {
    return healFlatDatabase(List.of(task)).get(0);
  }

  private void enqueueChildren(final Stream<SnapDataRequest> childRequests) {
    downloadState.enqueueRequests(childRequests);
  }
}
