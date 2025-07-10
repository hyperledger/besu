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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.PendingPeerRequest;
import org.hyperledger.besu.ethereum.eth.messages.BlockBodiesMessage;
import org.hyperledger.besu.ethereum.eth.messages.EthProtocolMessages;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Requests bodies from a peer by header, matches up headers to bodies, and returns blocks. */
public abstract class AbstractGetBodiesFromPeerTask<T, B> extends AbstractPeerRequestTask<List<T>> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractGetBodiesFromPeerTask.class);

  private final ProtocolSchedule protocolSchedule;
  private final List<BlockHeader> headers;

  AbstractGetBodiesFromPeerTask(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final MetricsSystem metricsSystem) {
    super(ethContext, EthProtocol.NAME, EthProtocolMessages.GET_BLOCK_BODIES, metricsSystem);
    checkArgument(!headers.isEmpty());
    this.protocolSchedule = protocolSchedule;
    this.headers = headers;
  }

  abstract T getBlock(BlockHeader header, B body);

  abstract List<B> getBodies(BlockBodiesMessage message, ProtocolSchedule protocolSchedule);

  abstract boolean bodyMatchesHeader(B body, BlockHeader header);

  @Override
  protected PendingPeerRequest sendRequest() {
    final List<Hash> blockHashes =
        headers.stream().map(BlockHeader::getHash).collect(Collectors.toList());
    final long minimumRequiredBlockNumber = headers.getLast().getNumber();

    return sendRequestToPeer(peer -> peer.getBodies(blockHashes), minimumRequiredBlockNumber);
  }

  @Override
  protected Optional<List<T>> processResponse(
      final boolean streamClosed, final MessageData message, final EthPeer peer) {
    if (streamClosed) {
      // All outstanding requests have been responded to, and we still haven't found the response
      // we wanted. It must have been empty or contain data that didn't match.
      peer.recordUselessResponse("bodies");
      return Optional.of(Collections.emptyList());
    }

    final BlockBodiesMessage bodiesMessage = BlockBodiesMessage.readFrom(message);
    final List<B> bodies = getBodies(bodiesMessage, protocolSchedule);
    if (bodies.isEmpty()) {
      // Message contains no data - nothing to do
      LOG.atDebug().setMessage("Message contains no data. Peer: {}").addArgument(peer).log();
      return Optional.empty();
    } else if (bodies.size() > headers.size()) {
      // Message doesn't match our request - nothing to do
      LOG.atDebug()
          .setMessage("Message doesn't match our request. Peer: {}")
          .addArgument(peer)
          .log();
      return Optional.empty();
    }

    final List<T> blocks = new ArrayList<>(headers.size());
    for (int i = 0; i < bodies.size(); i++) {
      final B body = bodies.get(i);
      final BlockHeader blockHeader = headers.get(i);
      if (!bodyMatchesHeader(body, blockHeader)) {
        // This message contains unrelated bodies - exit
        LOG.atDebug()
            .setMessage("This message contains unrelated bodies. Peer: {}")
            .addArgument(peer)
            .log();
        return Optional.empty();
      }
      blocks.add(getBlock(blockHeader, body));
    }
    return Optional.of(blocks);
  }
}
