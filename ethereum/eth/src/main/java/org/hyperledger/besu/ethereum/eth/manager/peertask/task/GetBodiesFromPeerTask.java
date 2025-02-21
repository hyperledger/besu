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

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.peertask.InvalidPeerTaskResponseException;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskValidationResponse;
import org.hyperledger.besu.ethereum.eth.messages.BlockBodiesMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockBodiesMessage;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements PeerTask for getting block bodies from peers, and matches headers to bodies to supply
 * full blocks
 */
public class GetBodiesFromPeerTask implements PeerTask<List<Block>> {

  private static final Logger LOG = LoggerFactory.getLogger(GetBodiesFromPeerTask.class);

  private static final int DEFAULT_RETRIES_AGAINST_OTHER_PEERS = 5;

  private final List<BlockHeader> blockHeaders;
  private final ProtocolSchedule protocolSchedule;
  private final int allowedRetriesAgainstOtherPeers;

  private final long requiredBlockchainHeight;
  private final List<Block> blocks = new ArrayList<>();
  private final boolean isPoS;

  public GetBodiesFromPeerTask(
      final List<BlockHeader> blockHeaders, final ProtocolSchedule protocolSchedule) {
    this(blockHeaders, protocolSchedule, DEFAULT_RETRIES_AGAINST_OTHER_PEERS);
  }

  public GetBodiesFromPeerTask(
      final List<BlockHeader> blockHeaders,
      final ProtocolSchedule protocolSchedule,
      final int allowedRetriesAgainstOtherPeers) {
    if (blockHeaders == null || blockHeaders.isEmpty()) {
      throw new IllegalArgumentException("Block headers must not be empty");
    }

    this.blockHeaders = blockHeaders;
    this.protocolSchedule = protocolSchedule;
    this.allowedRetriesAgainstOtherPeers = allowedRetriesAgainstOtherPeers;

    this.requiredBlockchainHeight =
        blockHeaders.stream()
            .mapToLong(BlockHeader::getNumber)
            .max()
            .orElse(BlockHeader.GENESIS_BLOCK_NUMBER);
    this.isPoS = protocolSchedule.getByBlockHeader(blockHeaders.getLast()).isPoS();
  }

  @Override
  public SubProtocol getSubProtocol() {
    return EthProtocol.get();
  }

  @Override
  public MessageData getRequestMessage() {
    return GetBlockBodiesMessage.create(
        blockHeaders.stream().map(BlockHeader::getBlockHash).toList());
  }

  @Override
  public List<Block> processResponse(final MessageData messageData)
      throws InvalidPeerTaskResponseException {
    // Blocks returned by this method are in the same order as the headers, but might not be
    // complete
    if (messageData == null) {
      throw new InvalidPeerTaskResponseException();
    }
    final BlockBodiesMessage blocksMessage = BlockBodiesMessage.readFrom(messageData);
    final List<BlockBody> blockBodies = blocksMessage.bodies(protocolSchedule);
    if (blockBodies.isEmpty() || blockBodies.size() > blockHeaders.size()) {
      throw new InvalidPeerTaskResponseException();
    }

    for (int i = 0; i < blockBodies.size(); i++) {
      final BlockBody blockBody = blockBodies.get(i);
      final BlockHeader blockHeader = blockHeaders.get(i);
      if (!blockBodyMatchesBlockHeader(blockBody, blockHeader)) {
        LOG.atDebug().setMessage("Received block body does not match block header").log();
        throw new InvalidPeerTaskResponseException();
      }

      blocks.add(new Block(blockHeader, blockBody));
    }
    return blocks;
  }

  @Override
  public int getRetriesWithOtherPeer() {
    return allowedRetriesAgainstOtherPeers;
  }

  private boolean blockBodyMatchesBlockHeader(
      final BlockBody blockBody, final BlockHeader blockHeader) {
    // this method validates that the block body matches the block header by calculating the roots
    // of the block body and comparing them to the roots in the block header
    if (!BodyValidation.transactionsRoot(blockBody.getTransactions())
        .equals(blockHeader.getTransactionsRoot())) {
      return false;
    }
    if (!BodyValidation.ommersHash(blockBody.getOmmers()).equals(blockHeader.getOmmersHash())) {
      return false;
    }
    if (!blockBody
        .getWithdrawals()
        .map(BodyValidation::withdrawalsRoot)
        .equals(blockHeader.getWithdrawalsRoot())) {
      return false;
    }

    return true;
  }

  @Override
  public Predicate<EthPeer> getPeerRequirementFilter() {
    return (ethPeer) ->
        isPoS || ethPeer.chainState().getEstimatedHeight() >= requiredBlockchainHeight;
  }

  @Override
  public PeerTaskValidationResponse validateResult(final List<Block> result) {
    if (result.isEmpty()) {
      return PeerTaskValidationResponse.NO_RESULTS_RETURNED;
    }
    return PeerTaskValidationResponse.RESULTS_VALID_AND_GOOD;
  }

  public List<BlockHeader> getBlockHeaders() {
    return blockHeaders;
  }
}
