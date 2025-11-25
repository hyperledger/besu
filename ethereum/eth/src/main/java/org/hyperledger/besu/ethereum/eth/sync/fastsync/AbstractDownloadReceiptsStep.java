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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractDownloadReceiptsStep<B, R, BWR>
    implements Function<List<B>, CompletableFuture<List<BWR>>> {
  // B is the type of block being processed (Block, SyncBlock),
  // R is the receipt type
  // BWR is the type of block with receipts (BlockWithReceipts, SyncBlockWithReceipts)

  private static final Logger LOG = LoggerFactory.getLogger(AbstractDownloadReceiptsStep.class);

  private final EthScheduler ethScheduler;

  public AbstractDownloadReceiptsStep(final EthScheduler ethScheduler) {
    this.ethScheduler = ethScheduler;
  }

  @Override
  public CompletableFuture<List<BWR>> apply(final List<B> blocks) {
    final List<BlockHeader> headers = blocks.stream().map(this::getBlockHeader).collect(toList());
    final List<BlockHeader> originalBlockHeaders =
        LOG.isTraceEnabled() ? List.copyOf(headers) : null;
    return ethScheduler
        .scheduleServiceTask(
            () -> {
              Map<BlockHeader, List<R>> receiptsByBlockHeader = new HashMap<>();
              while (!headers.isEmpty()) {
                Map<BlockHeader, List<R>> receipts = getReceipts(headers);
                headers.removeAll(receipts.keySet());
                for (BlockHeader blockHeader : receipts.keySet()) {
                  receiptsByBlockHeader.put(blockHeader, receipts.get(blockHeader));
                }
              }
              if (LOG.isTraceEnabled()) {
                for (BlockHeader blockHeader : originalBlockHeaders) {
                  final List<R> transactionReceipts = receiptsByBlockHeader.get(blockHeader);
                  LOG.atTrace()
                      .setMessage("{} receipts received for header {}")
                      .addArgument(transactionReceipts == null ? 0 : transactionReceipts.size())
                      .addArgument(blockHeader.getBlockHash())
                      .log();
                }
              }
              return CompletableFuture.completedFuture(receiptsByBlockHeader);
            })
        .thenApply((receipts) -> combineBlocksAndReceipts(blocks, receipts));
  }

  /**
   * Retrieves the BlockHeader from the supplied block b
   *
   * @param b a block
   * @return the BlockHeader from the supplied block b
   */
  abstract BlockHeader getBlockHeader(final B b);

  /**
   * Retrieves transaction receipts for as many of the supplied headers as possible. Repeat calls
   * may be made after removing headers no longer needing transaction receipts.
   *
   * @param headers A list of headers to retrieve transaction receipts for
   * @return transaction receipts for as many of the supplied headers as possible
   */
  abstract Map<BlockHeader, List<R>> getReceipts(final List<BlockHeader> headers);

  /**
   * Transfrom the supplied blocks and receiptsByHeader into a combined block with receipts type
   *
   * @param blocks The blocks to be combined
   * @param receiptsByHeader The receipts to be combined, organised by block header
   * @return The supplied blocks and receiptsByHeader transformed into a combined block with
   *     receipts type
   */
  abstract List<BWR> combineBlocksAndReceipts(
      final List<B> blocks, final Map<BlockHeader, List<R>> receiptsByHeader);
}
