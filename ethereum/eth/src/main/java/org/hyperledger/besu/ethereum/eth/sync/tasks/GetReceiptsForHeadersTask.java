/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.sync.tasks;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractRetryingPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetReceiptsFromPeerTask;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Given a set of headers, repeatedly requests the receipts for those blocks. */
public class GetReceiptsForHeadersTask
    extends AbstractRetryingPeerTask<Map<BlockHeader, List<TransactionReceipt>>> {
  private static final Logger LOG = LoggerFactory.getLogger(GetReceiptsForHeadersTask.class);
  private static final int DEFAULT_RETRIES = 3;

  private final EthContext ethContext;

  private final List<BlockHeader> headers;
  private final Map<BlockHeader, List<TransactionReceipt>> receipts;
  private final MetricsSystem metricsSystem;

  private GetReceiptsForHeadersTask(
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final int maxRetries,
      final MetricsSystem metricsSystem) {
    super(ethContext, maxRetries, Map::isEmpty, metricsSystem);
    checkArgument(headers.size() > 0, "Must supply a non-empty headers list");
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;

    this.headers = headers;
    this.receipts = new HashMap<>();
    completeEmptyReceipts(headers);
  }

  public static GetReceiptsForHeadersTask forHeaders(
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final int maxRetries,
      final MetricsSystem metricsSystem) {
    return new GetReceiptsForHeadersTask(ethContext, headers, maxRetries, metricsSystem);
  }

  public static GetReceiptsForHeadersTask forHeaders(
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final MetricsSystem metricsSystem) {
    return new GetReceiptsForHeadersTask(ethContext, headers, DEFAULT_RETRIES, metricsSystem);
  }

  private void completeEmptyReceipts(final List<BlockHeader> headers) {
    headers.stream()
        .filter(header -> header.getReceiptsRoot().equals(Hash.EMPTY_TRIE_HASH))
        .forEach(header -> receipts.put(header, emptyList()));
  }

  @Override
  protected CompletableFuture<Map<BlockHeader, List<TransactionReceipt>>> executePeerTask(
      final Optional<EthPeer> assignedPeer) {
    return requestReceipts(assignedPeer).thenCompose(this::processResponse);
  }

  private CompletableFuture<Map<BlockHeader, List<TransactionReceipt>>> requestReceipts(
      final Optional<EthPeer> assignedPeer) {
    final List<BlockHeader> incompleteHeaders = incompleteHeaders();
    if (incompleteHeaders.isEmpty()) {
      return CompletableFuture.completedFuture(emptyMap());
    }
    LOG.debug(
        "Requesting bodies to complete {} blocks, starting with {}.",
        incompleteHeaders.size(),
        incompleteHeaders.get(0).getNumber());
    return executeSubTask(
        () -> {
          final GetReceiptsFromPeerTask task =
              GetReceiptsFromPeerTask.forHeaders(ethContext, incompleteHeaders, metricsSystem);
          assignedPeer.ifPresent(task::assignPeer);
          return task.run().thenApply(PeerTaskResult::getResult);
        });
  }

  private CompletableFuture<Map<BlockHeader, List<TransactionReceipt>>> processResponse(
      final Map<BlockHeader, List<TransactionReceipt>> responseData) {
    receipts.putAll(responseData);

    if (isComplete()) {
      result.complete(receipts);
    }

    return CompletableFuture.completedFuture(responseData);
  }

  private List<BlockHeader> incompleteHeaders() {
    return headers.stream().filter(h -> receipts.get(h) == null).collect(Collectors.toList());
  }

  private boolean isComplete() {
    return headers.stream().allMatch(header -> receipts.get(header) != null);
  }
}
