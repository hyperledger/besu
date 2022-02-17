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
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.snap.GetTrieNodeFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.snap.RetryingGetAccountRangeFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.snap.RetryingGetBytecodeFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.snap.RetryingGetStorageRangeFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.StorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.AccountRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.BytecodeRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.StorageRangeDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.TrieNodeDataHealRequest;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractSnapMessageData;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.tasks.Task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.immutables.value.Value;

public class RequestDataStep {

  private final MetricsSystem metricsSystem;
  private final EthContext ethContext;
  private final WorldStateProofProvider worldStateProofProvider;

  public RequestDataStep(
      final EthContext ethContext,
      final WorldStateStorage worldStateStorage,
      final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    this.ethContext = ethContext;
    this.worldStateProofProvider = new WorldStateProofProvider(worldStateStorage);
  }

  public CompletableFuture<List<Task<SnapDataRequest>>> requestStorage(
      final List<Task<SnapDataRequest>> requestTasks,
      final SnapSyncState fastSyncState,
      final WorldDownloadState<SnapDataRequest> downloadState) {
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
    final EthTask<StorageRangeMessage.SlotRangeData> getStorageRangeTask =
        RetryingGetStorageRangeFromPeerTask.forStorageRange(
            ethContext,
            accountHashes,
            minRange,
            RangeManager.MAX_RANGE,
            blockHeader,
            metricsSystem);
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
                  request.setSlots(response.slots().get(i));
                  request.setProofs(
                      i < response.slots().size() - 1 ? new ArrayDeque<>() : response.proofs());
                  request.checkProof(downloadState, worldStateProofProvider);
                }
              }
              return requestTasks;
            });
  }

  public CompletableFuture<List<Task<SnapDataRequest>>> requestCode(
      final List<Task<SnapDataRequest>> requestTasks,
      final SnapSyncState fastSyncState,
      final WorldDownloadState<SnapDataRequest> downloadState) {
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
                for (int i = 0; i < requestTasks.size(); i++) {
                  final BytecodeRequest request = (BytecodeRequest) requestTasks.get(i).getData();
                  request.setRootHash(blockHeader.getStateRoot());
                  if (response.containsKey(request.getCodeHash())) {
                    request.setCode(response.get(request.getCodeHash()));
                  }
                }
              }
              return requestTasks;
            });
  }

  public CompletableFuture<Task<SnapDataRequest>> requestAccountData(
      final Task<SnapDataRequest> requestTask,
      final SnapSyncState fastSyncState,
      final WorldDownloadState<SnapDataRequest> downloadState) {

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
                accountDataRequest.setAccounts(response.accounts());
                accountDataRequest.setProofs(response.proofs());
                accountDataRequest.checkProof(downloadState, worldStateProofProvider);
              }
              return requestTask;
            });
  }

  public CompletableFuture<List<Task<SnapDataRequest>>> requestTrieNodeByPath(
      final List<Task<SnapDataRequest>> requestTasks,
      final SnapSyncState fastSyncState,
      final WorldDownloadState<SnapDataRequest> downloadState) {

    final BlockHeader blockHeader = fastSyncState.getPivotBlockHeader().get();
    final Map<Bytes, List<Bytes>> message = new HashMap<>();
    requestTasks.stream()
        .map(Task::getData)
        .map(TrieNodeDataHealRequest.class::cast)
        .map(TrieNodeDataHealRequest::getTrieNodePath)
        .forEach(
            path -> {
              List<Bytes> bytes = message.computeIfAbsent(path.get(0), k -> Lists.newArrayList());
              if (path.size() > 1) {
                bytes.add(path.get(1));
              }
            });

    System.out.println("message " + message);

    final GetTrieNodeFromPeerTask getTrieNodeFromPeerTask =
        GetTrieNodeFromPeerTask.forTrieNodes(ethContext, message, blockHeader, metricsSystem);
    downloadState.addOutstandingTask(getTrieNodeFromPeerTask);
    return getTrieNodeFromPeerTask
        .run()
        .handle(
            (response, error) -> {
              if (response != null) {
                int counter = 0;
                downloadState.removeOutstandingTask(getTrieNodeFromPeerTask);
                for (final Task<SnapDataRequest> task : requestTasks) {
                  final TrieNodeDataHealRequest request = (TrieNodeDataHealRequest) task.getData();
                  final Bytes matchingData = response.getResult().get(request.getPathId());
                  if (matchingData != null
                      && Hash.hash(matchingData).equals(request.getNodeHash())) {
                    request.setData(matchingData);
                    counter++;
                  }
                }
                System.out.println(
                    "Found "
                        + requestTasks.size()
                        + " "
                        + counter
                        + " "
                        + response.getResult().size());
              }
              return requestTasks;
            });
  }

  @Value.Immutable
  public interface SendRequestResult {
    SnapDataRequest snapDataRequest();

    AbstractSnapMessageData abstractSnapMessageData();
  }
}
