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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskBehavior;
import org.hyperledger.besu.ethereum.eth.messages.GetReceiptsMessage;
import org.hyperledger.besu.ethereum.eth.messages.ReceiptsMessage;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetReceiptsFromPeerTask
    implements PeerTask<Map<BlockHeader, List<TransactionReceipt>>> {

  private final Collection<BlockHeader> blockHeaders;
  private final Map<Hash, List<BlockHeader>> headersByReceiptsRoot = new HashMap<>();

  public GetReceiptsFromPeerTask(final Collection<BlockHeader> blockHeaders) {
    this.blockHeaders = blockHeaders;
    blockHeaders.forEach(
        header ->
            headersByReceiptsRoot
                .computeIfAbsent(header.getReceiptsRoot(), key -> new ArrayList<>())
                .add(header));
  }

  @Override
  public String getSubProtocol() {
    return EthProtocol.NAME;
  }

  @Override
  public long getRequiredBlockNumber() {
    return blockHeaders.stream()
        .mapToLong(BlockHeader::getNumber)
        .max()
        .orElse(BlockHeader.GENESIS_BLOCK_NUMBER);
  }

  @Override
  public MessageData getRequestMessage() {
    // Since we have to match up the data by receipt root, we only need to request receipts
    // for one of the headers with each unique receipt root.
    final List<Hash> blockHashes =
        headersByReceiptsRoot.values().stream()
            .map(headers -> headers.getFirst().getHash())
            .toList();
    return GetReceiptsMessage.create(blockHashes);
  }

  @Override
  public Map<BlockHeader, List<TransactionReceipt>> parseResponse(final MessageData messageData)
      throws InvalidPeerTaskResponseException {
    if (messageData == null) {
      throw new InvalidPeerTaskResponseException();
    }
    final ReceiptsMessage receiptsMessage = ReceiptsMessage.readFrom(messageData);
    final List<List<TransactionReceipt>> receiptsByBlock = receiptsMessage.receipts();
    if (receiptsByBlock.isEmpty() || receiptsByBlock.size() > blockHeaders.size()) {
      throw new InvalidPeerTaskResponseException();
    }

    final Map<BlockHeader, List<TransactionReceipt>> receiptsByHeader = new HashMap<>();
    for (final List<TransactionReceipt> receiptsInBlock : receiptsByBlock) {
      final List<BlockHeader> blockHeaders =
          headersByReceiptsRoot.get(BodyValidation.receiptsRoot(receiptsInBlock));
      if (blockHeaders == null) {
        // Contains receipts that we didn't request, so mustn't be the response we're looking for.
        throw new InvalidPeerTaskResponseException();
      }
      blockHeaders.forEach(header -> receiptsByHeader.put(header, receiptsInBlock));
    }
    return receiptsByHeader;
  }

  @Override
  public Collection<PeerTaskBehavior> getPeerTaskBehaviors() {
    return List.of(PeerTaskBehavior.RETRY_WITH_OTHER_PEERS, PeerTaskBehavior.RETRY_WITH_SAME_PEER);
  }
}
