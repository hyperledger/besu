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
package tech.pegasys.pantheon.ethereum.eth.sync.worldstate;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.EthTaskException;
import tech.pegasys.pantheon.ethereum.eth.manager.task.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.task.RetryingGetNodeDataFromPeerTask;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.services.tasks.Task;
import tech.pegasys.pantheon.util.ExceptionUtils;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RequestDataStep {
  private static final Logger LOG = LogManager.getLogger();
  private final BiFunction<List<Hash>, Long, EthTask<Map<Hash, BytesValue>>> getNodeDataTaskFactory;

  public RequestDataStep(final EthContext ethContext, final MetricsSystem metricsSystem) {
    this(
        (hashes, pivotBlockNumber) ->
            RetryingGetNodeDataFromPeerTask.forHashes(
                ethContext, hashes, pivotBlockNumber, metricsSystem));
  }

  RequestDataStep(
      final BiFunction<List<Hash>, Long, EthTask<Map<Hash, BytesValue>>> getNodeDataTaskFactory) {
    this.getNodeDataTaskFactory = getNodeDataTaskFactory;
  }

  public CompletableFuture<List<Task<NodeDataRequest>>> requestData(
      final List<Task<NodeDataRequest>> requestTasks,
      final BlockHeader blockHeader,
      final WorldDownloadState downloadState) {
    final List<Hash> hashes =
        requestTasks.stream()
            .map(Task::getData)
            .map(NodeDataRequest::getHash)
            .distinct()
            .collect(Collectors.toList());
    return sendRequest(blockHeader, hashes, downloadState)
        .thenApply(
            data -> {
              for (final Task<NodeDataRequest> task : requestTasks) {
                final NodeDataRequest request = task.getData();
                final BytesValue matchingData = data.get(request.getHash());
                if (matchingData != null) {
                  request.setData(matchingData);
                }
              }
              return requestTasks;
            });
  }

  private CompletableFuture<Map<Hash, BytesValue>> sendRequest(
      final BlockHeader blockHeader,
      final List<Hash> hashes,
      final WorldDownloadState downloadState) {
    final EthTask<Map<Hash, BytesValue>> task =
        getNodeDataTaskFactory.apply(hashes, blockHeader.getNumber());
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
                  LOG.debug("GetNodeDataRequest failed", error);
                }
                return Collections.emptyMap();
              }
              downloadState.requestComplete(!result.isEmpty());
              return result;
            });
  }
}
