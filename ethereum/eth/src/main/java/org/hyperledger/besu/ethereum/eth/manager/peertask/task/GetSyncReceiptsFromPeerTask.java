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
package org.hyperledger.besu.ethereum.eth.manager.peertask.task;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.core.encoding.receipt.SyncTransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthPeerImmutableAttributes;
import org.hyperledger.besu.ethereum.eth.manager.peertask.InvalidPeerTaskResponseException;
import org.hyperledger.besu.ethereum.eth.manager.peertask.MalformedRlpFromPeerException;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskValidationResponse;
import org.hyperledger.besu.ethereum.eth.messages.GetReceiptsMessage;
import org.hyperledger.besu.ethereum.eth.messages.ReceiptsMessage;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.List;
import java.util.function.Predicate;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;

public class GetSyncReceiptsFromPeerTask implements PeerTask<List<List<SyncTransactionReceipt>>> {

  private final List<SyncBlock> requestedBlocks;
  private final List<BlockHeader> requestedHeaders;
  private final long requiredBlockchainHeight;
  private final boolean isPoS;
  private final SyncTransactionReceiptEncoder syncTransactionReceiptEncoder;

  public GetSyncReceiptsFromPeerTask(
      final List<SyncBlock> blocks,
      final ProtocolSchedule protocolSchedule,
      final SyncTransactionReceiptEncoder syncTransactionReceiptEncoder) {
    checkArgument(!blocks.isEmpty(), "Requested block list must not be empty");
    this.requestedBlocks = blocks;
    this.requestedHeaders = blocks.stream().map(SyncBlock::getHeader).toList();

    // calculate the minimum required blockchain height a peer will need to be able to fulfil this
    // request
    requiredBlockchainHeight =
        requestedHeaders.stream()
            .mapToLong(BlockHeader::getNumber)
            .max()
            .orElse(BlockHeader.GENESIS_BLOCK_NUMBER);

    isPoS = protocolSchedule.anyMatch((ps) -> ps.spec().isPoS());
    this.syncTransactionReceiptEncoder = syncTransactionReceiptEncoder;
  }

  @Override
  public SubProtocol getSubProtocol() {
    return EthProtocol.get();
  }

  @Override
  public MessageData getRequestMessage() {
    return GetReceiptsMessage.create(requestedHeaders.stream().map(BlockHeader::getHash).toList());
  }

  @Override
  public List<List<SyncTransactionReceipt>> processResponse(final MessageData messageData)
      throws InvalidPeerTaskResponseException, MalformedRlpFromPeerException {
    if (messageData == null) {
      throw new InvalidPeerTaskResponseException("Null message data");
    }
    final ReceiptsMessage receiptsMessage = ReceiptsMessage.readFrom(messageData);
    try {
      return receiptsMessage.syncReceipts();
    } catch (RLPException e) {
      // indicates a malformed or unexpected RLP result from the peer
      throw new MalformedRlpFromPeerException(e, messageData.getData());
    }
  }

  @Override
  public Predicate<EthPeerImmutableAttributes> getPeerRequirementFilter() {
    return (ethPeer) -> isPoS || ethPeer.estimatedChainHeight() >= requiredBlockchainHeight;
  }

  @Override
  public PeerTaskValidationResponse validateResult(
      final List<List<SyncTransactionReceipt>> result) {
    if (result.isEmpty()) {
      return PeerTaskValidationResponse.NO_RESULTS_RETURNED;
    }

    if (result.size() > requestedBlocks.size()) {
      return PeerTaskValidationResponse.TOO_MANY_RESULTS_RETURNED;
    }

    for (int i = 0; i < result.size(); i++) {
      final var requestedReceipts = requestedBlocks.get(i);
      final var receivedReceiptsForBlock = result.get(i);

      // verify that the receipts count is within bounds for every received block
      if (receivedReceiptsForBlock.size() > requestedReceipts.getBody().getTransactionCount()) {
        return PeerTaskValidationResponse.TOO_MANY_RESULTS_RETURNED;
      }

      // ensure the calculated receipts root matches the one in the requested block header
      if (!receiptsRootMatches(requestedReceipts.getHeader(), receivedReceiptsForBlock)) {
        return PeerTaskValidationResponse.RESULTS_DO_NOT_MATCH_QUERY;
      }
    }

    return PeerTaskValidationResponse.RESULTS_VALID_AND_GOOD;
  }

  private boolean receiptsRootMatches(
      final BlockHeader blockHeader, final List<SyncTransactionReceipt> receipts) {
    final var calculatedReceiptsRoot =
        Util.getRootFromListOfBytes(
            receipts.stream()
                .map(
                    (r) -> {
                      Bytes rlp =
                          r.isFormattedForRootCalculation()
                              ? r.getRlpBytes()
                              : syncTransactionReceiptEncoder.encodeForRootCalculation(r);
                      r.clearSubVariables();
                      return rlp;
                    })
                .toList());

    return calculatedReceiptsRoot.getBytes().equals(blockHeader.getReceiptsRoot().getBytes());
  }

  @VisibleForTesting
  public List<SyncBlock> getRequestedBlocks() {
    return requestedBlocks;
  }
}
