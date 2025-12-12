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
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
  public static final long ONE_SECOND = 1000L;

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final int headerRequestSize;
  private final long trustAnchorBlockNumber;

  /**
   * Creates a new DownloadBackwardHeadersStep.
   *
   * @param protocolSchedule the protocol schedule
   * @param ethContext the eth context
   * @param headerRequestSize the number of headers to request per batch
   * @param trustAnchorBlockNumber the lowest header that we want to download
   */
  public DownloadBackwardHeadersStep(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final int headerRequestSize,
      final long trustAnchorBlockNumber) {
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.headerRequestSize = headerRequestSize;
    this.trustAnchorBlockNumber = trustAnchorBlockNumber;
  }

  @Override
  public CompletableFuture<List<BlockHeader>> apply(final Long startBlockNumber) {
    final long remainingHeaders = startBlockNumber - trustAnchorBlockNumber;
    final int headersToRequest = (int) Math.min(headerRequestSize, remainingHeaders);
    if (headersToRequest < 1) {
      throw new IllegalStateException("Number of headers to request is less than 1:" + headersToRequest);
    }

    return ethContext
        .getScheduler()
        .scheduleServiceTask(() -> downloadAllHeaders(startBlockNumber, headersToRequest));
  }

  private CompletableFuture<List<BlockHeader>> downloadAllHeaders(
      final Long startBlockNumber, final int headersToRequest) {
    List<BlockHeader> headers = new ArrayList<>(headersToRequest);
    do {
      final GetHeadersFromPeerTask task =
          new GetHeadersFromPeerTask(
              startBlockNumber - headers.size(),
              headersToRequest - headers.size(),
              0,
              GetHeadersFromPeerTask.Direction.REVERSE,
              protocolSchedule);

      final PeerTaskExecutorResult<List<BlockHeader>> result =
          ethContext.getPeerTaskExecutor().execute(task);

      final PeerTaskExecutorResponseCode peerTaskExecutorResponseCode = result.responseCode();
      if (peerTaskExecutorResponseCode != PeerTaskExecutorResponseCode.SUCCESS) {
        if (peerTaskExecutorResponseCode == PeerTaskExecutorResponseCode.NO_PEER_AVAILABLE) {
          try {
            Thread.sleep(ONE_SECOND); // TODO: Stefan: replace with async scheduler: return ethContext.getScheduler().scheduleFutureTask(() -> downloadAllHeaders(...), Duration.ofSeconds(1));
          } catch (InterruptedException e) {
            // do nothing
          }
        } else {
          LOG.warn(
              "Failed to download {} headers from block {} (response: {})",
              headersToRequest,
              startBlockNumber,
              peerTaskExecutorResponseCode);
          return CompletableFuture.failedFuture(
              new RuntimeException("Failed to download headers starting from block " + startBlockNumber));
        }
      } else {
        headers.addAll(result.result().get());
      }
    } while (headers.size() < headersToRequest);
    LOG.debug(
        "Downloaded {} headers: blocks {} to {}",
        headers.size(),
        headers.getFirst().getNumber(),
        headers.getLast().getNumber());

    return CompletableFuture.completedFuture(headers);
  }
}
