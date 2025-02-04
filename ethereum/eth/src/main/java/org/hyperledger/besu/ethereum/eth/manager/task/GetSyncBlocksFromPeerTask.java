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
package org.hyperledger.besu.ethereum.eth.manager.task;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockBody;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.PendingPeerRequest;
import org.hyperledger.besu.ethereum.eth.messages.BlockBodiesMessage;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Requests bodies from a peer by header, matches up headers to bodies, and returns blocks. */
public class GetSyncBlocksFromPeerTask extends AbstractPeerRequestTask<List<SyncBlock>> {
  private static final Logger LOG = LoggerFactory.getLogger(GetSyncBlocksFromPeerTask.class);

  private final List<BlockHeader> headers;
  private final Map<BodyIdentifier, List<BlockHeader>> bodyToHeaders = new HashMap<>();
  private final ProtocolSchedule protocolSchedule;

  private GetSyncBlocksFromPeerTask(
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final MetricsSystem metricsSystem,
      final ProtocolSchedule protocolSchedule) {
    super(ethContext, EthPV62.GET_BLOCK_BODIES, metricsSystem);
    checkArgument(!headers.isEmpty());

    this.protocolSchedule = protocolSchedule;
    this.headers = headers;
    headers.forEach(
        (header) -> {
          final BodyIdentifier bodyId = new BodyIdentifier(header);
          bodyToHeaders.putIfAbsent(bodyId, new ArrayList<>(headers.size()));
          bodyToHeaders.get(bodyId).add(header);
        });
  }

  public static GetSyncBlocksFromPeerTask forHeaders(
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final MetricsSystem metricsSystem,
      final ProtocolSchedule protocolSchedule) {
    return new GetSyncBlocksFromPeerTask(ethContext, headers, metricsSystem, protocolSchedule);
  }

  @Override
  protected PendingPeerRequest sendRequest() {
    final List<Hash> blockHashes =
        headers.stream().map(BlockHeader::getHash).collect(Collectors.toList());
    LOG.atTrace()
        .setMessage("Requesting {} bodies with hashes {}.")
        .addArgument(blockHashes.size())
        .addArgument(blockHashes)
        .log();
    final long minimumRequiredBlockNumber = headers.getLast().getNumber();

    return sendRequestToPeer(
        peer -> {
          LOG.atTrace()
              .setMessage("Requesting {} bodies from peer {}.")
              .addArgument(blockHashes.size())
              .addArgument(peer)
              .log();
          return peer.getBodies(blockHashes);
        },
        minimumRequiredBlockNumber);
  }

  @Override
  protected Optional<List<SyncBlock>> processResponse(
      final boolean streamClosed, final MessageData message, final EthPeer peer) {
    if (streamClosed) {
      // All outstanding requests have been responded to, and we still haven't found the response
      // we wanted. It must have been empty or contain data that didn't match.
      peer.recordUselessResponse("bodies");
      return Optional.of(Collections.emptyList());
    }

    final BlockBodiesMessage bodiesMessage = BlockBodiesMessage.readFrom(message);
    final List<SyncBlockBody> bodies = bodiesMessage.syncBodies(protocolSchedule);
    if (bodies.isEmpty()) {
      // Message contains no data - nothing to do
      LOG.debug("Message contains no data. Peer: {}", peer);
      return Optional.empty();
    } else if (bodies.size() > headers.size()) {
      // Message doesn't match our request - nothing to do
      LOG.debug("Message doesn't match our request. Peer: {}", peer);
      return Optional.empty();
    }

    final List<SyncBlock> syncBlocks = new ArrayList<>(headers.size());
    for (final SyncBlockBody body : bodies) {
      final BodyIdentifier currentBodyId = new BodyIdentifier(body);
      final List<BlockHeader> headers = bodyToHeaders.get(currentBodyId);
      if (headers == null) {
        // This message contains unrelated bodies - exit
        LOG.debug(
            "This message contains unrelated bodies. Peer: {}. Current body id: {}",
            peer,
            currentBodyId);
        return Optional.empty();
      }
      headers.forEach(h -> syncBlocks.add(new SyncBlock(h, body)));
      // Clear processed headers
      headers.clear();
    }

    LOG.atTrace()
        .setMessage(
            "Associated {} bodies with {} headers to get {} syncBlocks with these hashes: {}")
        .addArgument(bodies.size())
        .addArgument(headers.size())
        .addArgument(syncBlocks.size())
        .addArgument(
            () ->
                syncBlocks.stream()
                    .map(SyncBlock::getHeader)
                    .map(BlockHeader::getBlockHash)
                    .toList())
        .log();
    return Optional.of(syncBlocks);
  }
}
