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
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.peertask.InvalidPeerTaskResponseException;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTask;
import org.hyperledger.besu.ethereum.eth.messages.BlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockHeadersMessage;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

import java.util.List;
import java.util.function.Predicate;

public class GetHeadersFromPeerTask implements PeerTask<List<BlockHeader>> {
  private final Long blockNumber;
  private final Hash blockHash;
  private final int maxHeaders;
  private final int skip;
  private final Direction direction;
  private final ProtocolSchedule protocolSchedule;
  private final long requiredBlockchainHeight;

  public GetHeadersFromPeerTask(
      final Long blockNumber,
      final int maxHeaders,
      final int skip,
      final Direction direction,
      final ProtocolSchedule protocolSchedule) {
    this(null, blockNumber, maxHeaders, skip, direction, protocolSchedule);
  }

  public GetHeadersFromPeerTask(
      final Hash blockHash,
      final long blockNumber,
      final int maxHeaders,
      final int skip,
      final Direction direction,
      final ProtocolSchedule protocolSchedule) {
    this.blockHash = blockHash;
    this.blockNumber = blockNumber;
    this.maxHeaders = maxHeaders;
    this.skip = skip;
    this.direction = direction;
    this.protocolSchedule = protocolSchedule;

    requiredBlockchainHeight =
        direction == Direction.FORWARD
            ? blockNumber + (long) (maxHeaders - 1) * skip + 1
            : blockNumber;
  }

  @Override
  public SubProtocol getSubProtocol() {
    return EthProtocol.get();
  }

  @Override
  public MessageData getRequestMessage() {
    if (blockNumber != null) {
      return GetBlockHeadersMessage.create(
          blockNumber, maxHeaders, skip, direction == Direction.REVERSE);
    } else if (blockHash != null) {
      return GetBlockHeadersMessage.create(
          blockHash, maxHeaders, skip, direction == Direction.REVERSE);
    } else {
      throw new IllegalStateException(
          "Block number or block hash is required for GetHeadersFromPeerTask");
    }
  }

  @Override
  public List<BlockHeader> processResponse(final MessageData messageData)
      throws InvalidPeerTaskResponseException {
    if (messageData == null) {
      throw new InvalidPeerTaskResponseException();
    }
    return BlockHeadersMessage.readFrom(messageData).getHeaders(protocolSchedule);
  }

  @Override
  public Predicate<EthPeer> getPeerRequirementFilter() {
    return (ethPeer) ->
        ethPeer.getProtocolName().equals(getSubProtocol().getName())
            && (protocolSchedule.anyMatch((ps) -> ps.spec().isPoS())
                || ethPeer.chainState().getEstimatedHeight() >= requiredBlockchainHeight);
  }

  @Override
  public boolean isSuccess(final List<BlockHeader> result) {
    return !result.isEmpty();
  }

  public Long getBlockNumber() {
    return blockNumber;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public int getMaxHeaders() {
    return maxHeaders;
  }

  public int getSkip() {
    return skip;
  }

  public Direction getDirection() {
    return direction;
  }

  public enum Direction {
    FORWARD,
    REVERSE
  }
}
