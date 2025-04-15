/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.sync;

import org.hyperledger.besu.consensus.merge.ForkchoiceEvent;
import org.hyperledger.besu.consensus.merge.UnverifiedForkchoiceListener;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessage;
import org.hyperledger.besu.ethereum.eth.messages.BlockRangeUpdateMessage;
import org.hyperledger.besu.ethereum.eth.messages.EthProtocolMessages;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockRangeBroadcaster implements UnverifiedForkchoiceListener {
  private static final Logger LOG = LoggerFactory.getLogger(BlockRangeBroadcaster.class);

  // range update block interval
  static final int BLOCK_RANGE_UPDATE_INTERVAL = 32; // one slot

  private final EthContext ethContext;
  private final Blockchain blockchain;

  public BlockRangeBroadcaster(final EthContext ethContext, final Blockchain blockchain) {
    this.ethContext = ethContext;
    this.blockchain = blockchain;
    ethContext
        .getEthMessages()
        .subscribe(EthProtocolMessages.BLOCK_RANGE_UPDATE, this::handleBlockRangeUpdateMessage);
  }

  @Override
  public void onNewUnverifiedForkchoice(final ForkchoiceEvent event) {
    blockchain
        .getBlockHeader(event.getHeadBlockHash())
        .ifPresent(
            header -> {
              if (header.getNumber() % BLOCK_RANGE_UPDATE_INTERVAL == 0) {
                broadcastBlockRange(0L, header.getNumber(), header.getHash());
              }
            });
  }

  private void handleBlockRangeUpdateMessage(final EthMessage message) {
    try {
      final BlockRangeUpdateMessage blockRangeUpdateMessage =
          BlockRangeUpdateMessage.readFrom(message.getData());
      final long earliestBlockNumber = blockRangeUpdateMessage.getEarliestBlockNumber();
      final long latestBlockNumber = blockRangeUpdateMessage.getLatestBlockNumber();
      final Hash blockHash = blockRangeUpdateMessage.getBlockHash();
      LOG.debug(
          "Received blockRange=[{}, {}, {}] from peer={}",
          earliestBlockNumber,
          latestBlockNumber,
          blockHash,
          message.getPeer().getLoggableId());

      message.getPeer().registerKnownBlock(blockHash);
      message.getPeer().registerBlockRange(blockHash, latestBlockNumber, earliestBlockNumber);
    } catch (final RLPException e) {
      LOG.atDebug()
          .setMessage("Unable to parse BlockRangeUpdateMessage from peer {} {}")
          .addArgument(message.getPeer()::getLoggableId)
          .addArgument(e)
          .log();
      message
          .getPeer()
          .disconnect(DisconnectMessage.DisconnectReason.SUBPROTOCOL_TRIGGERED_UNPARSABLE_STATUS);
    }
  }

  @VisibleForTesting
  void broadcastBlockRange(
      final long earliestBlockNumber, final long latestBlockNumber, final Hash blockHash) {
    final BlockRangeUpdateMessage blockRangeUpdateMessage =
        BlockRangeUpdateMessage.create(earliestBlockNumber, latestBlockNumber, blockHash);
    LOG.debug(
        "Sending blockRange=[{}, {}, {}]",
        blockRangeUpdateMessage.getEarliestBlockNumber(),
        blockRangeUpdateMessage.getLatestBlockNumber(),
        blockRangeUpdateMessage.getBlockHash());
    ethContext
        .getEthPeers()
        .streamAvailablePeers()
        .filter(peer -> peer.hasSupportForMessage(EthProtocolMessages.BLOCK_RANGE_UPDATE))
        .forEach(
            ethPeer -> {
              try {
                ethPeer.send(blockRangeUpdateMessage);
              } catch (final PeerConnection.PeerNotConnected e) {
                LOG.trace("Failed to broadcast blockRange to peer", e);
              }
            });
  }
}
