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

import org.hyperledger.besu.datatypes.Hash;
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
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;

public class GetSyncReceiptsFromPeerTask implements PeerTask<GetSyncReceiptsFromPeerTask.Response> {
  private final Request request;
  protected final ProtocolSchedule protocolSchedule;
  private final List<BlockHeader> requestedHeaders;
  private final long requiredBlockchainHeight;
  private final boolean isPoS;
  private final SyncTransactionReceiptEncoder syncTransactionReceiptEncoder;

  public GetSyncReceiptsFromPeerTask(
      final Request request,
      final ProtocolSchedule protocolSchedule,
      final SyncTransactionReceiptEncoder syncTransactionReceiptEncoder) {
    checkArgument(!request.blocks.isEmpty(), "Requested block list must not be empty");
    this.request = request;
    this.protocolSchedule = protocolSchedule;
    this.requestedHeaders = request.blocks.stream().map(SyncBlock::getHeader).toList();

    requiredBlockchainHeight =
        this.requestedHeaders.stream()
            .mapToLong(BlockHeader::getNumber)
            .max()
            .orElse(BlockHeader.GENESIS_BLOCK_NUMBER);

    isPoS = protocolSchedule.anyMatch(ps -> ps.spec().isPoS());
    this.syncTransactionReceiptEncoder = syncTransactionReceiptEncoder;
  }

  @Override
  public SubProtocol getSubProtocol() {
    return EthProtocol.get();
  }

  @Override
  public MessageData getRequestMessage(final Set<Capability> agreedCapabilities) {
    final List<Hash> blockHashes = requestedHeaders.stream().map(BlockHeader::getHash).toList();
    return agreedCapabilities.stream().anyMatch(EthProtocol::isEth70Compatible)
        ? GetReceiptsMessage.create(blockHashes, request.firstBlockPartialReceipts.size())
        : GetReceiptsMessage.create(blockHashes);
  }

  @Override
  public Response processResponse(
      final MessageData messageData, final Set<Capability> agreedCapabilities)
      throws InvalidPeerTaskResponseException, MalformedRlpFromPeerException {
    if (messageData == null) {
      throw new InvalidPeerTaskResponseException("Null message data");
    }
    final ReceiptsMessage receiptsMessage = ReceiptsMessage.readFrom(messageData);
    try {
      final boolean isEth70Response = receiptsMessage.lastBlockIncomplete().isPresent();

      final List<List<SyncTransactionReceipt>> receivedReceipts =
          isEth70Response
              ? completeFirstBlock(receiptsMessage.syncReceipts())
              : receiptsMessage.syncReceipts();

      if (receivedReceipts.isEmpty()) {
        throw new InvalidPeerTaskResponseException("No result returned");
      }

      if (receivedReceipts.size() > request.size()) {
        throw new InvalidPeerTaskResponseException("Too many result returned");
      }

      final int endIndex;
      final List<SyncTransactionReceipt> lastBlockPartialReceipts;
      if (isEth70Response && receiptsMessage.lastBlockIncomplete().get()) {
        endIndex = receivedReceipts.size() - 1;
        lastBlockPartialReceipts = receivedReceipts.getLast();
      } else {
        endIndex = receivedReceipts.size();
        lastBlockPartialReceipts = List.of();
      }

      final Map<SyncBlock, List<SyncTransactionReceipt>> receiptsByBlock =
          HashMap.newHashMap(receivedReceipts.size());

      for (int i = 0; i < endIndex; i++) {
        receiptsByBlock.put(request.blocks.get(i), receivedReceipts.get(i));
      }

      return new Response(receiptsByBlock, lastBlockPartialReceipts);
    } catch (RLPException e) {
      // indicates a malformed or unexpected RLP result from the peer
      throw new MalformedRlpFromPeerException(e, messageData.getData());
    }
  }

  private List<List<SyncTransactionReceipt>> completeFirstBlock(
      final List<List<SyncTransactionReceipt>> receivedReceipts)
      throws InvalidPeerTaskResponseException {
    if (request.firstBlockPartialReceipts.isEmpty()) {
      // nothing to integrate returning as is
      return receivedReceipts;
    }

    if (receivedReceipts.isEmpty()) {
      throw new InvalidPeerTaskResponseException("No result returned");
    }

    // add new receipts to the already present ones
    final List<SyncTransactionReceipt> cumulativeReceiptsForFirstBlock =
        new ArrayList<>(
            request.firstBlockPartialReceipts.size() + receivedReceipts.getFirst().size());
    cumulativeReceiptsForFirstBlock.addAll(request.firstBlockPartialReceipts);
    cumulativeReceiptsForFirstBlock.addAll(receivedReceipts.getFirst());

    // replace first list of receipts with the new cumulative list
    final List<List<SyncTransactionReceipt>> cumulativeReceipts =
        new ArrayList<>(receivedReceipts.size());
    cumulativeReceipts.add(cumulativeReceiptsForFirstBlock);
    cumulativeReceipts.addAll(receivedReceipts.subList(1, receivedReceipts.size()));
    return cumulativeReceipts;
  }

  @Override
  public Predicate<EthPeerImmutableAttributes> getPeerRequirementFilter() {
    return (ethPeer) -> isPoS || ethPeer.estimatedChainHeight() >= requiredBlockchainHeight;
  }

  @Override
  public PeerTaskValidationResponse validateResult(final Response result) {
    if (result.isEmpty()) {
      return PeerTaskValidationResponse.NO_RESULTS_RETURNED;
    }

    for (final Map.Entry<SyncBlock, List<SyncTransactionReceipt>> entry :
        result.completeReceiptsByBlock.entrySet()) {
      final SyncBlock requestedBlock = entry.getKey();
      final List<SyncTransactionReceipt> receivedReceiptsForBlock = entry.getValue();

      // verify that the receipts count is within bounds for every received block
      if (receivedReceiptsForBlock.size() > requestedBlock.getBody().getTransactionCount()) {
        return PeerTaskValidationResponse.TOO_MANY_RESULTS_RETURNED;
      }

      // ensure the calculated receipts root matches the one in the requested block header
      if (!receiptsRootMatches(requestedBlock.getHeader(), receivedReceiptsForBlock)) {
        return PeerTaskValidationResponse.RESULTS_DO_NOT_MATCH_QUERY;
      }
    }

    if (!result.lastBlockPartialReceipts().isEmpty()) {
      final SyncBlock lastBlockReceived =
          request.blocks.get(result.completeReceiptsByBlock().size());
      if (result.lastBlockPartialReceipts().size()
          > lastBlockReceived.getBody().getTransactionCount()) {
        return PeerTaskValidationResponse.TOO_MANY_RESULTS_RETURNED;
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

    return calculatedReceiptsRoot.equals(blockHeader.getReceiptsRoot());
  }

  @VisibleForTesting
  public List<SyncBlock> getRequestedBlocks() {
    return request.blocks();
  }

  public record Request(
      List<SyncBlock> blocks, List<SyncTransactionReceipt> firstBlockPartialReceipts) {
    public boolean isEmpty() {
      return blocks.isEmpty();
    }

    public int size() {
      return blocks.size();
    }
  }

  public record Response(
      Map<SyncBlock, List<SyncTransactionReceipt>> completeReceiptsByBlock,
      List<SyncTransactionReceipt> lastBlockPartialReceipts) {
    public boolean isEmpty() {
      return completeReceiptsByBlock.isEmpty() && lastBlockPartialReceipts.isEmpty();
    }

    public int size() {
      return completeReceiptsByBlock.size();
    }
  }
}
