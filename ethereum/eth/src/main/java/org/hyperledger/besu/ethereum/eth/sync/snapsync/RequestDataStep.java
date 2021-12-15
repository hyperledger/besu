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
import org.hyperledger.besu.ethereum.eth.manager.exceptions.EthTaskException;
import org.hyperledger.besu.ethereum.eth.manager.snap.RetryingGetAccountRangeFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.snap.RetryingGetBytecodeFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.snap.RetryingGetStorageRangeFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.snap.RetryingGetTrieNodeFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractSnapMessageData;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.tasks.Task;
import org.hyperledger.besu.util.ExceptionUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

import com.google.common.collect.Iterables;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.immutables.value.Value;

public class RequestDataStep {
  private static final Logger LOG = LogManager.getLogger();
  private final BiFunction<SnapDataRequest, BlockHeader, EthTask<? extends AbstractSnapMessageData>>
      requestTaskFactory;

  private EthContext ethContext;
  private final WorldStateProofProvider worldStateProofProvider;

  public RequestDataStep(
      final EthContext ethContext,
      final WorldStateStorage worldStateStorage,
      final MetricsSystem metricsSystem) {
    this(
        (request, blockHeader) -> {
          request.clear();
          switch (request.getRequestType()) {
            case ACCOUNT_RANGE:
            default:
              return RetryingGetAccountRangeFromPeerTask.forAccountRange(
                  ethContext, request, blockHeader, metricsSystem);
            case STORAGE_RANGE:
              return RetryingGetStorageRangeFromPeerTask.forStorageRange(
                  ethContext, request, blockHeader, metricsSystem);
            case BYTECODES:
              return RetryingGetBytecodeFromPeerTask.forStorageRange(
                  ethContext, request, blockHeader, metricsSystem);
            case TRIE_NODE:
              return RetryingGetTrieNodeFromPeerTask.forTrieNodes(
                  ethContext, request, blockHeader, metricsSystem);
          }
        },
        worldStateStorage);
    this.ethContext = ethContext;
  }

  RequestDataStep(
      final BiFunction<SnapDataRequest, BlockHeader, EthTask<? extends AbstractSnapMessageData>>
          requestTaskFactory,
      final WorldStateStorage worldStateStorage) {
    this.requestTaskFactory = requestTaskFactory;
    this.worldStateProofProvider = new WorldStateProofProvider(worldStateStorage);
  }

  public CompletableFuture<List<Task<SnapDataRequest>>> requestTrieNodeData(
      final List<Task<SnapDataRequest>> requestTasks,
      final SnapSyncState fastSyncState,
      final WorldDownloadState<SnapDataRequest> downloadState) {
    return sendRequest(
            TrieNodeDataRequest.compactPaths(requestTasks),
            fastSyncState.getPivotBlockHeader().get(),
            downloadState)
        .thenApply(
            maybeResponse -> {
              maybeResponse.ifPresent(
                  response -> {
                    final List<Bytes> datas =
                        TrieNodeDataRequest.formatTrieNodes(
                            requestTasks, response.abstractSnapMessageData().getData());
                    if (datas.isEmpty()) {
                      downloadState.requestComplete(false, 0);
                    }
                    for (int i = 0; i < requestTasks.size(); i++) {
                      final SnapDataRequest req = requestTasks.get(i).getData();
                      req.setData(Iterables.get(datas, i, null));
                      if (!req.isValidResponse(
                          fastSyncState, ethContext.getEthPeers(), worldStateProofProvider)) {
                        req.setData(Optional.empty()); // invalid response mark Failed
                      } else {
                        downloadState.requestComplete(true);
                      }
                    }
                  });
              return requestTasks;
            });
  }

  @SuppressWarnings("rawtypes")
  public CompletableFuture<List<Task<SnapDataRequest>>> requestData(
      final List<Task<SnapDataRequest>> requestTasks,
      final SnapSyncState fastSyncState,
      final WorldDownloadState<SnapDataRequest> downloadState) {
    return sendRequest(
            requestTasks.get(0).getData(), fastSyncState.getPivotBlockHeader().get(), downloadState)
        .thenApply(
            maybeResponse -> {
              maybeResponse.ifPresent(
                  response -> {
                    final SnapDataRequest req = response.snapDataRequest();
                    req.setData(response.abstractSnapMessageData().getData());
                    if (!req.isValidResponse(
                        fastSyncState, ethContext.getEthPeers(), worldStateProofProvider)) {
                      req.setData(Optional.empty()); // invalid response mark Failed
                      downloadState.requestComplete(false, 0);
                    } else {
                      downloadState.requestComplete(true);
                    }
                  });
              return requestTasks;
            });
  }

  private CompletableFuture<Optional<SendRequestResult>> sendRequest(
      final SnapDataRequest request,
      final BlockHeader blockHeader,
      final WorldDownloadState<SnapDataRequest> downloadState) {
    final EthTask<? extends AbstractSnapMessageData> task =
        requestTaskFactory.apply(request, blockHeader);
    downloadState.addOutstandingTask(task);
    return task.run()
        .handle(
            (result, error) -> {
              downloadState.removeOutstandingTask(task);
              if (error != null) {
                final Throwable rootCause = ExceptionUtils.rootCause(error);
                if (!(rootCause instanceof TimeoutException
                    || rootCause instanceof InterruptedException
                    || rootCause instanceof CancellationException
                    || rootCause instanceof EthTaskException)) {
                  LOG.debug("Send request failed", error);
                }
                return Optional.empty();
              }
              return Optional.of(
                  ImmutableSendRequestResult.builder()
                      .abstractSnapMessageData(result)
                      .snapDataRequest(request)
                      .build());
            });
  }

  @Value.Immutable
  public interface SendRequestResult {
    SnapDataRequest snapDataRequest();

    AbstractSnapMessageData abstractSnapMessageData();
  }
}
