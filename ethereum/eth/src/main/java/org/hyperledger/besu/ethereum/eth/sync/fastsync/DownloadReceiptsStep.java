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

import static java.util.Collections.emptyList;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetReceiptsFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DownloadReceiptsStep
    extends AbstractDownloadReceiptsStep<Block, TransactionReceipt, BlockWithReceipts> {
  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;

  public DownloadReceiptsStep(
      final ProtocolSchedule protocolSchedule, final EthContext ethContext) {
    super(ethContext.getScheduler());
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
  }

  @Override
  protected BlockHeader getBlockHeader(final Block block) {
    return block.getHeader();
  }

  @Override
  Map<BlockHeader, List<TransactionReceipt>> getReceipts(final List<BlockHeader> headers) {
    GetReceiptsFromPeerTask task = new GetReceiptsFromPeerTask(headers, protocolSchedule);
    PeerTaskExecutorResult<Map<BlockHeader, List<TransactionReceipt>>> getReceiptsResult =
        ethContext.getPeerTaskExecutor().execute(task);
    if (getReceiptsResult.responseCode() == PeerTaskExecutorResponseCode.SUCCESS
        && getReceiptsResult.result().isPresent()) {
      return getReceiptsResult.result().get();
    }
    return Collections.emptyMap();
  }

  @Override
  List<BlockWithReceipts> combineBlocksAndReceipts(
      final List<Block> blocks, final Map<BlockHeader, List<TransactionReceipt>> receiptsByHeader) {
    return blocks.stream()
        .map(
            block -> {
              final List<TransactionReceipt> receipts =
                  receiptsByHeader.getOrDefault(block.getHeader(), emptyList());
              if (block.getBody().getTransactions().size() != receipts.size()) {
                throw new IllegalStateException(
                    "PeerTask response code was success, but incorrect number of receipts returned. Block hash: "
                        + block.getHeader().getHash()
                        + ", transactions: "
                        + block.getBody().getTransactions().size()
                        + ", receipts: "
                        + receipts.size());
              }
              return new BlockWithReceipts(block, receipts);
            })
        .toList();
  }
}
