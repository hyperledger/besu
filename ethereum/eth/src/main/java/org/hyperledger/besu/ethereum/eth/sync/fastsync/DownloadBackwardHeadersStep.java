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
import org.hyperledger.besu.ethereum.eth.sync.HeaderBatchDownloader;
import org.hyperledger.besu.ethereum.eth.sync.HeaderBatchDownloader.Direction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Downloads block headers in reverse direction (backward from pivot to genesis). Returns headers in
 * reverse order: [n, n-1, n-2, ...].
 */
public class DownloadBackwardHeadersStep
    implements Function<Long, CompletableFuture<List<BlockHeader>>> {

  private final EthContext ethContext;
  private final HeaderBatchDownloader headerBatchDownloader;
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
    if (headerRequestSize < 1) throw new IllegalArgumentException("headerRequestSize must be >= 1");
    this.ethContext = ethContext;
    this.headerBatchDownloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, headerRequestSize);
    this.headerRequestSize = headerRequestSize;
    this.trustAnchorBlockNumber = trustAnchorBlockNumber;
  }

  @Override
  public CompletableFuture<List<BlockHeader>> apply(final Long startBlockNumber) {
    final long remainingHeaders = startBlockNumber - trustAnchorBlockNumber;
    final int headersToRequest = (int) Math.min(headerRequestSize, remainingHeaders);
    if (headersToRequest < 1) {
      throw new IllegalStateException(
          "Number of headers to request is less than 1:" + headersToRequest);
    }

    return ethContext
        .getScheduler()
        .scheduleServiceTask(
            () ->
                CompletableFuture.completedFuture(
                    headerBatchDownloader.downloadHeaders(
                        startBlockNumber, headersToRequest, Direction.REVERSE)))
        .orTimeout(2L, TimeUnit.MINUTES);
  }
}
