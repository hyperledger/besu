/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.possync;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoadHeadersStep
    implements Function<SyncTargetNumberRange, CompletableFuture<List<BlockHeader>>> {
  private static final Logger LOG = LoggerFactory.getLogger(LoadHeadersStep.class);
  private final Blockchain blockchain;
  private final DownloadPosHeadersStep downloadPosHeadersStep;

  public LoadHeadersStep(
      final Blockchain blockchain, final DownloadPosHeadersStep downloadPosHeadersStep) {
    this.blockchain = blockchain;
    this.downloadPosHeadersStep = downloadPosHeadersStep;
  }

  @Override
  public CompletableFuture<List<BlockHeader>> apply(final SyncTargetNumberRange checkpointRange) {
    return loadHeaders(checkpointRange);
  }

  private CompletableFuture<List<BlockHeader>> loadHeaders(final SyncTargetNumberRange range) {
    try {
      LOG.info(
          "Loading headers for range {} to {}", range.lowerBlockNumber(), range.upperBlockNumber());
      if (range.getSegmentLengthExclusive() == 0) {
        // There are no extra headers to download.
        return completedFuture(emptyList());
      }
      long startBlockNumber = range.lowerBlockNumber();
      long endBlockNumber = range.upperBlockNumber();

      List<BlockHeader> headers =
          Stream.iterate(startBlockNumber, n -> n + 1)
              .limit(endBlockNumber - startBlockNumber + 1)
              .map(blockchain::getBlockHeader)
              .flatMap(Optional::stream)
              .collect(Collectors.toList());
      if (headers.size() != range.getSegmentLengthExclusive()) {
        // Fallback to download headers if we don't have all headers
        return downloadPosHeadersStep.apply(range);
      }
      return CompletableFuture.completedFuture(headers);
    } catch (final Exception e) {
      LOG.error(
          "Error loading headers for range {} to {}",
          range.lowerBlockNumber(),
          range.upperBlockNumber(),
          e);
      return CompletableFuture.completedFuture(emptyList());
    }
  }
}
