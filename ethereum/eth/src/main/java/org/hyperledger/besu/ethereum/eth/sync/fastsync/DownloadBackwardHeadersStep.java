/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads block headers in reverse direction (backward from pivot to genesis). Returns headers in
 * reverse order: [n, n-1, n-2, ...].
 */
public class DownloadBackwardHeadersStep
    implements Function<Long, CompletableFuture<List<BlockHeader>>> {
  private static final Logger LOG = LoggerFactory.getLogger(DownloadBackwardHeadersStep.class);
  private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
  private static final AtomicInteger taskId = new AtomicInteger(0);

  private final ProtocolSchedule protocolSchedule;
  private final EthScheduler ethScheduler;
  private final PeerTaskExecutor peerTaskExecutor;
  private final int headerRequestSize;
  private final long trustAnchorBlockNumber;
  private final Duration timeoutDuration;

  /**
   * Creates a new DownloadBackwardHeadersStep.
   *
   * @param protocolSchedule the protocol schedule
   * @param ethContext the eth context
   * @param headerRequestSize the number of headers to request per batch
   * @param trustAnchorBlockNumber the lowest header that we want to download
   * @param timeoutDuration the maximum time to wait including all retries
   */
  public DownloadBackwardHeadersStep(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final int headerRequestSize,
      final long trustAnchorBlockNumber,
      final Duration timeoutDuration) {
    if (headerRequestSize < 1) throw new IllegalArgumentException("headerRequestSize must be >= 1");
    this.protocolSchedule = protocolSchedule;
    this.ethScheduler = ethContext.getScheduler();
    this.peerTaskExecutor = ethContext.getPeerTaskExecutor();
    this.headerRequestSize = headerRequestSize;
    this.trustAnchorBlockNumber = trustAnchorBlockNumber;
    this.timeoutDuration = timeoutDuration;
  }

  @Override
  public CompletableFuture<List<BlockHeader>> apply(final Long startBlockNumber) {
    final long remainingHeaders = startBlockNumber - trustAnchorBlockNumber;
    final int headersToRequest = (int) Math.min(headerRequestSize, remainingHeaders);
    if (headersToRequest < 1) {
      throw new IllegalStateException(
          "Number of headers to request is less than 1:" + headersToRequest);
    }

    final int currentTaskId = taskId.getAndIncrement();
    final List<BlockHeader> downloadedHeaders = new ArrayList<>(headersToRequest);
    return ethScheduler
        .scheduleServiceTask(
            () ->
                downloadAllHeaders(
                    currentTaskId, 0, startBlockNumber, headersToRequest, downloadedHeaders))
        .orTimeout(timeoutDuration.toMillis(), TimeUnit.MILLISECONDS)
        .exceptionally(
            throwable -> {
              if (throwable instanceof TimeoutException) {
                LOG.trace(
                    "[{}] Timed out after {} ms while downloading {} backward headers from block {}",
                    currentTaskId,
                    timeoutDuration.toMillis(),
                    headersToRequest,
                    startBlockNumber);
              }
              throw new CompletionException(throwable);
            });
  }

  private CompletableFuture<List<BlockHeader>> downloadAllHeaders(
      final int currTaskId,
      final int prevIterations,
      final Long startBlockNumber,
      final int headersToRequest,
      final List<BlockHeader> downloadedHeaders) {

    int iteration = prevIterations;
    do {
      ++iteration;

      final long requestStartBlockNumber = startBlockNumber - downloadedHeaders.size();
      final int requestMaxHeaders = headersToRequest - downloadedHeaders.size();

      LOG.trace(
          "[{}:{}] Backward downloading {} headers starting from block {}",
          currTaskId,
          iteration,
          requestMaxHeaders,
          requestStartBlockNumber);

      final GetHeadersFromPeerTask task =
          new GetHeadersFromPeerTask(
              requestStartBlockNumber,
              requestMaxHeaders,
              0,
              GetHeadersFromPeerTask.Direction.REVERSE,
              protocolSchedule);

      final PeerTaskExecutorResult<List<BlockHeader>> result = peerTaskExecutor.execute(task);

      final PeerTaskExecutorResponseCode responseCode = result.responseCode();

      if (responseCode == PeerTaskExecutorResponseCode.SUCCESS) {
        final List<BlockHeader> resultBlockHeaders = result.result().orElseGet(List::of);
        if (!downloadedHeaders.isEmpty() // check the parent hash and block hash match
            && !resultBlockHeaders.isEmpty()
            && !resultBlockHeaders
                .getFirst()
                .getHash()
                .getBytes()
                .equals(downloadedHeaders.getLast().getParentHash().getBytes())) {
          throw new IllegalStateException("Parent hash of last header does not match first header");
        }
        downloadedHeaders.addAll(resultBlockHeaders);
        LOG.trace(
            "[{}:{}] Successfully received {} headers starting from block {}",
            currTaskId,
            iteration,
            requestMaxHeaders,
            requestStartBlockNumber);
      } else {
        LOG.trace(
            "[{}:{}] Failed with {} to retrieve {} headers starting from block {}",
            currTaskId,
            iteration,
            responseCode,
            requestMaxHeaders,
            requestStartBlockNumber);
        if (responseCode == PeerTaskExecutorResponseCode.INTERNAL_SERVER_ERROR) {
          return CompletableFuture.failedFuture(
              new RuntimeException(
                  "Failed to download "
                      + headersToRequest
                      + " headers starting from block "
                      + startBlockNumber));
        } else {

          LOG.trace("[{}:{}] Waiting for {} before retrying", currTaskId, iteration, RETRY_DELAY);
          final int passIterations = iteration;
          return ethScheduler.scheduleFutureTask(
              () ->
                  ethScheduler.scheduleServiceTask(
                      () ->
                          downloadAllHeaders(
                              currTaskId,
                              passIterations,
                              startBlockNumber,
                              headersToRequest,
                              downloadedHeaders)),
              RETRY_DELAY);
        }
      }
    } while (downloadedHeaders.size() < headersToRequest);
    LOG.atTrace()
        .setMessage("[{}:{}] Downloaded {} headers: blocks {} to {}")
        .addArgument(currTaskId)
        .addArgument(iteration)
        .addArgument(downloadedHeaders::size)
        .addArgument(downloadedHeaders.getFirst()::getNumber)
        .addArgument(downloadedHeaders.getLast()::getNumber)
        .log();

    return CompletableFuture.completedFuture(downloadedHeaders);
  }
}
