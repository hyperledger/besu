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
package org.hyperledger.besu.ethereum.eth.sync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads block headers in batches until the requested count is reached. Supports both forward
 * and reverse directions.
 */
public class HeaderBatchDownloader {
  private static final Logger LOG = LoggerFactory.getLogger(HeaderBatchDownloader.class);

  /** Direction for header downloads. */
  public enum Direction {
    FORWARD,
    REVERSE
  }

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final int batchSize;

  public HeaderBatchDownloader(
      final ProtocolSchedule protocolSchedule, final EthContext ethContext, final int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be >= 1");
    }
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.batchSize = batchSize;
  }

  /**
   * Downloads headers in batches until the target count is reached.
   *
   * @param startBlockNumber the block number to start from
   * @param count the total number of headers to download
   * @param direction FORWARD or REVERSE
   * @return list of headers in request order
   */
  public List<BlockHeader> downloadHeaders(
      final long startBlockNumber, final int count, final Direction direction) {
    if (count < 1) {
      throw new IllegalArgumentException("count must be >= 1");
    }

    final List<BlockHeader> headers = new ArrayList<>(count);
    do {
      final int remaining = count - headers.size();
      final int headersToRequest = Math.min(batchSize, remaining);
      final long requestBlockNumber =
          calculateRequestBlockNumber(startBlockNumber, headers.size(), direction);

      final GetHeadersFromPeerTask task =
          new GetHeadersFromPeerTask(
              requestBlockNumber,
              headersToRequest,
              0,
              direction == Direction.REVERSE
                  ? GetHeadersFromPeerTask.Direction.REVERSE
                  : GetHeadersFromPeerTask.Direction.FORWARD,
              protocolSchedule);

      final PeerTaskExecutorResult<List<BlockHeader>> result =
          ethContext.getPeerTaskExecutor().execute(task);

      final PeerTaskExecutorResponseCode responseCode = result.responseCode();
      if (responseCode != PeerTaskExecutorResponseCode.SUCCESS) {
        if (responseCode == PeerTaskExecutorResponseCode.INTERNAL_SERVER_ERROR) {
          throw new RuntimeException(
              "Failed to download " + count + " headers starting from block " + startBlockNumber);
        }
        // wait for a peer to become available before retrying
        ethContext.getEthPeers().waitForPeer(__ -> true);
      } else {
        final Optional<List<BlockHeader>> optionalBlockHeaderList = result.result();
        final List<BlockHeader> resultBlockHeaders = optionalBlockHeaderList.orElseGet(List::of);
        if (!headers.isEmpty() && !resultBlockHeaders.isEmpty()) {
          validateContinuity(headers, resultBlockHeaders, direction);
        }
        headers.addAll(resultBlockHeaders);
        // For FORWARD direction only: if peer returned fewer headers than requested,
        // we've reached the chain head and should stop. For REVERSE direction, we
        // always continue until we have exactly the requested count.
        if (direction == Direction.FORWARD && resultBlockHeaders.size() < headersToRequest) {
          break;
        }
      }
    } while (headers.size() < count);

    if (!headers.isEmpty()) {
      LOG.atTrace()
          .setMessage("Downloaded {} headers: blocks {} to {}")
          .addArgument(headers.size())
          .addArgument(headers.getFirst().getNumber())
          .addArgument(headers.getLast().getNumber())
          .log();
    }

    return headers;
  }

  private long calculateRequestBlockNumber(
      final long startBlockNumber, final int headersAlreadyDownloaded, final Direction direction) {
    if (direction == Direction.REVERSE) {
      return startBlockNumber - headersAlreadyDownloaded;
    } else {
      return startBlockNumber + headersAlreadyDownloaded;
    }
  }

  private void validateContinuity(
      final List<BlockHeader> existing,
      final List<BlockHeader> newHeaders,
      final Direction direction) {
    if (direction == Direction.REVERSE) {
      // For reverse: new batch's first header's hash should equal existing last header's parent
      // hash
      if (!newHeaders.getFirst().getHash().equals(existing.getLast().getParentHash())) {
        throw new IllegalStateException(
            "Parent hash of last header does not match first header of new batch");
      }
    } else {
      // For forward: new batch's first header's parent hash should equal existing last header's
      // hash
      if (!newHeaders.getFirst().getParentHash().equals(existing.getLast().getHash())) {
        throw new IllegalStateException(
            "First header's parent hash does not match last header of existing batch");
      }
    }
  }
}
