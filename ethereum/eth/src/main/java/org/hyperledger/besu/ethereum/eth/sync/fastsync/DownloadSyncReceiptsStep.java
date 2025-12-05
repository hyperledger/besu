/*
 * Copyright contributors to Hyperledger Besu.
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

import static java.util.Collections.emptyList;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;
import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetSyncReceiptsFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadSyncReceiptsStep
    extends AbstractDownloadReceiptsStep<SyncBlock, SyncTransactionReceipt, SyncBlockWithReceipts> {
  private static final Logger LOG = LoggerFactory.getLogger(DownloadSyncReceiptsStep.class);

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;

  public DownloadSyncReceiptsStep(
      final ProtocolSchedule protocolSchedule, final EthContext ethContext) {
    super(ethContext.getScheduler());
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
  }

  @Override
  BlockHeader getBlockHeader(final SyncBlock syncBlock) {
    return syncBlock.getHeader();
  }

  @Override
  Map<BlockHeader, List<SyncTransactionReceipt>> getReceipts(final List<BlockHeader> headers) {
    GetSyncReceiptsFromPeerTask task = new GetSyncReceiptsFromPeerTask(headers, protocolSchedule);
    PeerTaskExecutorResult<Map<BlockHeader, List<SyncTransactionReceipt>>> getReceiptsResult =
        ethContext.getPeerTaskExecutor().execute(task);
    if (getReceiptsResult.responseCode() == PeerTaskExecutorResponseCode.SUCCESS
        && getReceiptsResult.result().isPresent()) {
      return getReceiptsResult.result().get();
    }
    return Collections.emptyMap();
  }

  @Override
  List<SyncBlockWithReceipts> combineBlocksAndReceipts(
      final List<SyncBlock> blocks,
      final Map<BlockHeader, List<SyncTransactionReceipt>> receiptsByHeader) {
    return blocks.stream()
        .map(
            block -> {
              final List<SyncTransactionReceipt> receipts =
                  receiptsByHeader.getOrDefault(block.getHeader(), emptyList());
              if (block.getBody().getTransactionCount() != receipts.size()) {
                final BytesValueRLPOutput headerRlpOutput = new BytesValueRLPOutput();
                block.getHeader().writeTo(headerRlpOutput);
                LOG.atTrace()
                    .setMessage("Header RLP: {}")
                    .addArgument(headerRlpOutput.encoded())
                    .log();
                LOG.atTrace().setMessage("Body: {}").addArgument(block.getBody().getRlp()).log();
                throw new IllegalStateException(
                    "PeerTask response code was success, but incorrect number of receipts returned. Block hash: "
                        + block.getHeader().getHash()
                        + ", transactions: "
                        + block.getBody().getTransactionCount()
                        + ", receipts: "
                        + receipts.size());
              }
              return new SyncBlockWithReceipts(block, receipts);
            })
        .toList();
  }
}
