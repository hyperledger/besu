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
package org.hyperledger.besu.ethereum.eth.manager.task;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static org.hyperledger.besu.ethereum.mainnet.BodyValidation.receiptsRoot;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.PendingPeerRequest;
import org.hyperledger.besu.ethereum.eth.messages.EthPV63;
import org.hyperledger.besu.ethereum.eth.messages.ReceiptsMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetReceiptsFromPeerTask
    extends AbstractPeerRequestTask<Map<BlockHeader, List<TransactionReceipt>>> {
  private static final Logger LOG = LoggerFactory.getLogger(GetReceiptsFromPeerTask.class);

  private final Collection<BlockHeader> blockHeaders;
  private final Map<Hash, List<BlockHeader>> headersByReceiptsRoot = new HashMap<>();

  private GetReceiptsFromPeerTask(
      final EthContext ethContext,
      final Collection<BlockHeader> blockHeaders,
      final MetricsSystem metricsSystem) {
    super(ethContext, EthPV63.GET_RECEIPTS, metricsSystem);
    this.blockHeaders = blockHeaders;
    blockHeaders.forEach(
        header ->
            headersByReceiptsRoot
                .computeIfAbsent(header.getReceiptsRoot(), key -> new ArrayList<>())
                .add(header));
  }

  public static GetReceiptsFromPeerTask forHeaders(
      final EthContext ethContext,
      final Collection<BlockHeader> blockHeaders,
      final MetricsSystem metricsSystem) {

    return new GetReceiptsFromPeerTask(ethContext, blockHeaders, metricsSystem);
  }

  @Override
  protected PendingPeerRequest sendRequest() {
    final long maximumRequiredBlockNumber =
        blockHeaders.stream()
            .mapToLong(BlockHeader::getNumber)
            .max()
            .orElse(BlockHeader.GENESIS_BLOCK_NUMBER);

    // Since we have to match up the data by receipt root, we only need to request receipts
    // for one of the headers with each unique receipt root.
    final List<Hash> blockHashes =
        headersByReceiptsRoot.values().stream()
            .map(headers -> headers.get(0).getHash())
            .collect(toList());
    return sendRequestToPeer(
        peer -> {
          LOG.atTrace()
              .setMessage("Requesting {} receipts from peer {}")
              .addArgument(blockHeaders::size)
              .addArgument(peer::getLoggableId)
              .log();
          return peer.getReceipts(blockHashes);
        },
        maximumRequiredBlockNumber);
  }

  @Override
  protected Optional<Map<BlockHeader, List<TransactionReceipt>>> processResponse(
      final boolean streamClosed, final MessageData message, final EthPeer peer) {
    if (streamClosed) {
      // All outstanding requests have been responded to, and we still haven't found the response
      // we wanted. It must have been empty or contain data that didn't match.
      peer.recordUselessResponse("receipts");
      return Optional.of(emptyMap());
    }

    final ReceiptsMessage receiptsMessage = ReceiptsMessage.readFrom(message);
    final List<List<TransactionReceipt>> receiptsByBlock = receiptsMessage.receipts();
    if (receiptsByBlock.isEmpty()) {
      return Optional.empty();
    } else if (receiptsByBlock.size() > blockHeaders.size()) {
      return Optional.empty();
    }

    final Map<BlockHeader, List<TransactionReceipt>> receiptsByHeader = new HashMap<>();
    for (final List<TransactionReceipt> receiptsInBlock : receiptsByBlock) {
      final List<BlockHeader> blockHeaders =
          headersByReceiptsRoot.get(receiptsRoot(receiptsInBlock));
      if (blockHeaders == null) {
        // Contains receipts that we didn't request, so mustn't be the response we're looking for.
        return Optional.empty();
      }
      blockHeaders.forEach(header -> receiptsByHeader.put(header, receiptsInBlock));
    }
    return Optional.of(receiptsByHeader);
  }
}
