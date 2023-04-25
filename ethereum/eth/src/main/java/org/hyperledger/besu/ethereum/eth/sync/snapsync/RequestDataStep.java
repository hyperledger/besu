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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.snap.RetryingGetAccountRangeFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.snap.RetryingGetBytecodeFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.snap.RetryingGetStorageRangeFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.snap.RetryingGetTrieNodeFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.StorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.AccountRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.BytecodeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.StorageRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.heal.AccountFlatDatabaseHealingRangeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.heal.StorageFlatDatabaseHealingRangeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.heal.TrieNodeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.tasks.Task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class RequestDataStep {

  private final WorldStateStorage worldStateStorage;
  private final SnapSyncState fastSyncState;
  private final WorldDownloadState<SnapDataRequest> downloadState;
  private final MetricsSystem metricsSystem;
  private final EthContext ethContext;
  private final WorldStateProofProvider worldStateProofProvider;

  private long cpt = 0;

  public RequestDataStep(
      final EthContext ethContext,
      final WorldStateStorage worldStateStorage,
      final SnapSyncState fastSyncState,
      final WorldDownloadState<SnapDataRequest> downloadState,
      final MetricsSystem metricsSystem) {
    this.worldStateStorage = worldStateStorage;
    this.fastSyncState = fastSyncState;
    this.downloadState = downloadState;
    this.metricsSystem = metricsSystem;
    this.ethContext = ethContext;
    this.worldStateProofProvider = new WorldStateProofProvider(worldStateStorage);
  }

  public CompletableFuture<Task<SnapDataRequest>> requestAccount(
      final Task<SnapDataRequest> requestTask) {

    final BlockHeader blockHeader = fastSyncState.getPivotBlockHeader().get();
    final AccountRangeDataRequest accountDataRequest =
        (AccountRangeDataRequest) requestTask.getData();
    final EthTask<AccountRangeMessage.AccountRangeData> getAccountTask =
        RetryingGetAccountRangeFromPeerTask.forAccountRange(
            ethContext,
            accountDataRequest.getStartKeyHash(),
            accountDataRequest.getEndKeyHash(),
            blockHeader,
            metricsSystem);
    downloadState.addOutstandingTask(getAccountTask);
    return getAccountTask
        .run()
        .handle(
            (response, error) -> {
              if (response != null) {
                downloadState.removeOutstandingTask(getAccountTask);
                accountDataRequest.setRootHash(blockHeader.getStateRoot());
                accountDataRequest.addResponse(
                    worldStateProofProvider, response.accounts(), response.proofs());
              }
              return requestTask;
            });
  }

  public CompletableFuture<List<Task<SnapDataRequest>>> requestStorage(
      final List<Task<SnapDataRequest>> requestTasks) {
    final List<Bytes32> accountHashes =
        requestTasks.stream()
            .map(Task::getData)
            .map(StorageRangeDataRequest.class::cast)
            .map(StorageRangeDataRequest::getAccountHash)
            .collect(Collectors.toList());
    final BlockHeader blockHeader = fastSyncState.getPivotBlockHeader().get();
    final Bytes32 minRange =
        requestTasks.size() == 1
            ? ((StorageRangeDataRequest) requestTasks.get(0).getData()).getStartKeyHash()
            : RangeManager.MIN_RANGE;
    final Bytes32 maxRange =
        requestTasks.size() == 1
            ? ((StorageRangeDataRequest) requestTasks.get(0).getData()).getEndKeyHash()
            : RangeManager.MAX_RANGE;
    final EthTask<StorageRangeMessage.SlotRangeData> getStorageRangeTask =
        RetryingGetStorageRangeFromPeerTask.forStorageRange(
            ethContext, accountHashes, minRange, maxRange, blockHeader, metricsSystem);
    downloadState.addOutstandingTask(getStorageRangeTask);
    return getStorageRangeTask
        .run()
        .handle(
            (response, error) -> {
              if (response != null) {
                downloadState.removeOutstandingTask(getStorageRangeTask);
                for (int i = 0; i < response.slots().size(); i++) {
                  final StorageRangeDataRequest request =
                      (StorageRangeDataRequest) requestTasks.get(i).getData();
                  request.setRootHash(blockHeader.getStateRoot());
                  request.addResponse(
                      downloadState,
                      worldStateProofProvider,
                      response.slots().get(i),
                      i < response.slots().size() - 1 ? new ArrayDeque<>() : response.proofs());
                }
              }
              return requestTasks;
            });
  }

  public CompletableFuture<List<Task<SnapDataRequest>>> requestCode(
      final List<Task<SnapDataRequest>> requestTasks) {
    final List<Bytes32> codeHashes =
        requestTasks.stream()
            .map(Task::getData)
            .map(BytecodeRequest.class::cast)
            .map(BytecodeRequest::getCodeHash)
            .distinct()
            .collect(Collectors.toList());
    final BlockHeader blockHeader = fastSyncState.getPivotBlockHeader().get();
    final EthTask<Map<Bytes32, Bytes>> getByteCodeTask =
        RetryingGetBytecodeFromPeerTask.forByteCode(
            ethContext, codeHashes, blockHeader, metricsSystem);
    downloadState.addOutstandingTask(getByteCodeTask);
    return getByteCodeTask
        .run()
        .handle(
            (response, error) -> {
              if (response != null) {
                downloadState.removeOutstandingTask(getByteCodeTask);
                for (Task<SnapDataRequest> requestTask : requestTasks) {
                  final BytecodeRequest request = (BytecodeRequest) requestTask.getData();
                  request.setRootHash(blockHeader.getStateRoot());
                  if (response.containsKey(request.getCodeHash())) {
                    request.setCode(response.get(request.getCodeHash()));
                  }
                }
              }
              return requestTasks;
            });
  }

  public CompletableFuture<List<Task<SnapDataRequest>>> requestTrieNodeByPath(
      final List<Task<SnapDataRequest>> requestTasks) {

    final BlockHeader blockHeader = fastSyncState.getPivotBlockHeader().get();
    final Map<Bytes, List<Bytes>> message = new HashMap<>();
    requestTasks.stream()
        .map(Task::getData)
        .map(TrieNodeDataRequest.class::cast)
        .map(TrieNodeDataRequest::getTrieNodePath)
        .forEach(
            path -> {
              final List<Bytes> bytes =
                  message.computeIfAbsent(path.get(0), k -> Lists.newArrayList());
              if (path.size() > 1) {
                bytes.add(path.get(1));
              }
            });
    final EthTask<Map<Bytes, Bytes>> getTrieNodeFromPeerTask =
        RetryingGetTrieNodeFromPeerTask.forTrieNodes(
            ethContext, message, blockHeader, metricsSystem);
    downloadState.addOutstandingTask(getTrieNodeFromPeerTask);
    return getTrieNodeFromPeerTask
        .run()
        .handle(
            (response, error) -> {
              if (response != null) {
                downloadState.removeOutstandingTask(getTrieNodeFromPeerTask);
                for (final Task<SnapDataRequest> task : requestTasks) {
                  final TrieNodeDataRequest request = (TrieNodeDataRequest) task.getData();
                  final Bytes matchingData = response.get(request.getPathId());
                  if (matchingData != null) {
                    request.setData(matchingData);
                  }
                }
              }
              return requestTasks;
            });
  }

  public CompletableFuture<Task<SnapDataRequest>> requestLocalAccount(
      final Task<SnapDataRequest> requestTask) {

    final AccountFlatDatabaseHealingRangeRequest accountDataRequest =
        (AccountFlatDatabaseHealingRangeRequest) requestTask.getData();
    final BlockHeader blockHeader = fastSyncState.getPivotBlockHeader().get();

    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            worldStateStorage.streamAccountFlatDatabase(accountDataRequest.getStartKeyHash(), 128);
    final List<Bytes> leftAccountProofRelatedNodes =
        worldStateProofProvider.getAccountProofRelatedNodes(
            blockHeader.getStateRoot(), accounts.firstKey());
    final List<Bytes> rightAccountProofRelatedNodes =
        worldStateProofProvider.getAccountProofRelatedNodes(
            blockHeader.getStateRoot(), accounts.lastKey());

    if (cpt == 10000) {
      cpt = 0;
      System.out.println(
          "Found keys "
              + accounts.size()
              + " from "
              + accounts.firstKey()
              + " "
              + accounts.lastKey());
    }
    cpt++;

    accountDataRequest.setRootHash(blockHeader.getStateRoot());
    accountDataRequest.addLocalData(
        worldStateProofProvider,
        accounts,
        new ArrayDeque<>(
            Stream.concat(
                    leftAccountProofRelatedNodes.stream(), rightAccountProofRelatedNodes.stream())
                .collect(Collectors.toList())));

    return CompletableFuture.completedFuture(requestTask);
  }

  public CompletableFuture<Task<SnapDataRequest>> requestLocalStorage(
      final Task<SnapDataRequest> requestTask) {

    final StorageFlatDatabaseHealingRangeRequest storageDataRequest =
        (StorageFlatDatabaseHealingRangeRequest) requestTask.getData();
    final BlockHeader blockHeader = fastSyncState.getPivotBlockHeader().get();

    storageDataRequest.setRootHash(blockHeader.getStateRoot());

    final TreeMap<Bytes32, Bytes> slots =
        (TreeMap<Bytes32, Bytes>)
            worldStateStorage.streamStorageFlatDatabase(
                storageDataRequest.getAccountHash(), storageDataRequest.getStartKeyHash(), 128);
    if (!slots.isEmpty()) {
      final List<Bytes> leftStorageProofRelatedNodes =
          worldStateProofProvider.getStorageProofRelatedNodes(
              storageDataRequest.getStorageRoot(),
              storageDataRequest.getAccountHash(),
              slots.firstKey());
      final List<Bytes> rightStorageProofRelatedNodes =
          worldStateProofProvider.getStorageProofRelatedNodes(
              storageDataRequest.getStorageRoot(),
              storageDataRequest.getAccountHash(),
              slots.lastKey());
      storageDataRequest.addLocalData(
          worldStateProofProvider,
          slots,
          new ArrayDeque<>(
              Stream.concat(
                      leftStorageProofRelatedNodes.stream(), rightStorageProofRelatedNodes.stream())
                  .collect(Collectors.toList())));

      if (cpt == 10000) {
        cpt = 0;
        System.out.println(
            "Found storage keys "
                + slots.size()
                + " from "
                + slots.firstKey()
                + " "
                + slots.lastKey());
      }
      cpt++;
    } else {
      storageDataRequest.addLocalData(worldStateProofProvider, slots, new ArrayDeque<>());
    }

    return CompletableFuture.completedFuture(requestTask);
  }
}
