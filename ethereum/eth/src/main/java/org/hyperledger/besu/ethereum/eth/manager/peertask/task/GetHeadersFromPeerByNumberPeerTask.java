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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskBehavior;
import org.hyperledger.besu.ethereum.eth.messages.BlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockHeadersMessage;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.Collection;
import java.util.List;

public class GetHeadersFromPeerByNumberPeerTask implements PeerTask<List<BlockHeader>> {
  private final long blockNumber;
  private final int count;
  private final int skip;
  private final Direction direction;
  private final ProtocolSchedule protocolSchedule;

  public GetHeadersFromPeerByNumberPeerTask(
      final long blockNumber,
      final int count,
      final int skip,
      final Direction direction,
      final ProtocolSchedule protocolSchedule) {
    this.blockNumber = blockNumber;
    this.count = count;
    this.skip = skip;
    this.direction = direction;
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public String getSubProtocol() {
    return EthProtocol.NAME;
  }

  @Override
  public long getRequiredBlockNumber() {
    return direction == Direction.FORWARD ? blockNumber + (long) count * (skip + 1) : blockNumber;
  }

  @Override
  public MessageData getRequestMessage() {
    return GetBlockHeadersMessage.create(blockNumber, count, skip, direction.isReverse());
  }

  @Override
  public List<BlockHeader> parseResponse(final MessageData messageData)
      throws InvalidPeerTaskResponseException {
    try {
      return BlockHeadersMessage.readFrom(messageData).getHeaders(protocolSchedule);
    } catch (IllegalArgumentException e) {
      throw new InvalidPeerTaskResponseException(e);
    }
  }

  @Override
  public Collection<PeerTaskBehavior> getPeerTaskBehaviors() {
    return List.of(PeerTaskBehavior.RETRY_WITH_OTHER_PEERS, PeerTaskBehavior.RETRY_WITH_SAME_PEER);
  }

  public enum Direction {
    FORWARD(false),
    BACKWARD(true);
    private final boolean reverse;

    Direction(final boolean reverse) {
      this.reverse = reverse;
    }

    public boolean isReverse() {
      return reverse;
    }
  }

  public long getBlockNumber() {
    return blockNumber;
  }

  public int getCount() {
    return count;
  }

  public int getSkip() {
    return skip;
  }

  public Direction getDirection() {
    return direction;
  }
}
