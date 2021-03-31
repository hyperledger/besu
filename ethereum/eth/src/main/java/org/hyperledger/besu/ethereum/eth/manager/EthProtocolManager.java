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
package org.hyperledger.besu.ethereum.eth.manager;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.StatusMessage;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidatorRunner;
import org.hyperledger.besu.ethereum.eth.sync.BlockBroadcaster;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.p2p.network.ProtocolManager;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class EthProtocolManager implements ProtocolManager, MinedBlockObserver {
  private static final Logger LOG = LogManager.getLogger();

  private final EthScheduler scheduler;
  private final CountDownLatch shutdown;
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private final Hash genesisHash;
  private final ForkIdManager forkIdManager;
  private final BigInteger networkId;
  private final EthPeers ethPeers;
  private final EthMessages ethMessages;
  private final EthContext ethContext;
  private final List<Capability> supportedCapabilities;
  private final Blockchain blockchain;
  private final BlockBroadcaster blockBroadcaster;
  private final List<PeerValidator> peerValidators;

  public EthProtocolManager(
      final Blockchain blockchain,
      final BigInteger networkId,
      final WorldStateArchive worldStateArchive,
      final TransactionPool transactionPool,
      final EthProtocolConfiguration ethereumWireProtocolConfiguration,
      final EthPeers ethPeers,
      final EthMessages ethMessages,
      final EthContext ethContext,
      final List<PeerValidator> peerValidators,
      final boolean fastSyncEnabled,
      final EthScheduler scheduler,
      final ForkIdManager forkIdManager) {
    this.networkId = networkId;
    this.peerValidators = peerValidators;
    this.scheduler = scheduler;
    this.blockchain = blockchain;

    this.shutdown = new CountDownLatch(1);
    genesisHash = blockchain.getBlockHashByNumber(0L).get();

    this.forkIdManager = forkIdManager;

    this.ethPeers = ethPeers;
    this.ethMessages = ethMessages;
    this.ethContext = ethContext;

    this.blockBroadcaster = new BlockBroadcaster(ethContext);

    supportedCapabilities =
        calculateCapabilities(fastSyncEnabled, ethereumWireProtocolConfiguration.isEth65Enabled());

    // Run validators
    for (final PeerValidator peerValidator : this.peerValidators) {
      PeerValidatorRunner.runValidator(ethContext, peerValidator);
    }

    // Set up request handlers
    new EthServer(
        blockchain,
        worldStateArchive,
        transactionPool,
        ethMessages,
        ethereumWireProtocolConfiguration);
  }

  @VisibleForTesting
  public EthProtocolManager(
      final Blockchain blockchain,
      final BigInteger networkId,
      final WorldStateArchive worldStateArchive,
      final TransactionPool transactionPool,
      final EthProtocolConfiguration ethereumWireProtocolConfiguration,
      final EthPeers ethPeers,
      final EthMessages ethMessages,
      final EthContext ethContext,
      final List<PeerValidator> peerValidators,
      final boolean fastSyncEnabled,
      final EthScheduler scheduler) {
    this(
        blockchain,
        networkId,
        worldStateArchive,
        transactionPool,
        ethereumWireProtocolConfiguration,
        ethPeers,
        ethMessages,
        ethContext,
        peerValidators,
        fastSyncEnabled,
        scheduler,
        new ForkIdManager(
            blockchain,
            Collections.emptyList(),
            ethereumWireProtocolConfiguration.isLegacyEth64ForkIdEnabled()));
  }

  public EthProtocolManager(
      final Blockchain blockchain,
      final BigInteger networkId,
      final WorldStateArchive worldStateArchive,
      final TransactionPool transactionPool,
      final EthProtocolConfiguration ethereumWireProtocolConfiguration,
      final EthPeers ethPeers,
      final EthMessages ethMessages,
      final EthContext ethContext,
      final List<PeerValidator> peerValidators,
      final boolean fastSyncEnabled,
      final EthScheduler scheduler,
      final List<Long> forks) {
    this(
        blockchain,
        networkId,
        worldStateArchive,
        transactionPool,
        ethereumWireProtocolConfiguration,
        ethPeers,
        ethMessages,
        ethContext,
        peerValidators,
        fastSyncEnabled,
        scheduler,
        new ForkIdManager(
            blockchain, forks, ethereumWireProtocolConfiguration.isLegacyEth64ForkIdEnabled()));
  }

  public EthContext ethContext() {
    return ethContext;
  }

  public BlockBroadcaster getBlockBroadcaster() {
    return blockBroadcaster;
  }

  @Override
  public String getSupportedProtocol() {
    return EthProtocol.NAME;
  }

  private List<Capability> calculateCapabilities(
      final boolean fastSyncEnabled, final boolean eth65Enabled) {
    final ImmutableList.Builder<Capability> capabilities = ImmutableList.builder();
    if (!fastSyncEnabled) {
      capabilities.add(EthProtocol.ETH62);
    }
    capabilities.add(EthProtocol.ETH63);
    capabilities.add(EthProtocol.ETH64);
    if (eth65Enabled) {
      capabilities.add(EthProtocol.ETH65);
    }

    return capabilities.build();
  }

  @Override
  public List<Capability> getSupportedCapabilities() {
    return supportedCapabilities;
  }

  @Override
  public void stop() {
    if (stopped.compareAndSet(false, true)) {
      LOG.info("Stopping {} Subprotocol.", getSupportedProtocol());
      scheduler.stop();
      shutdown.countDown();
    } else {
      LOG.error("Attempted to stop already stopped {} Subprotocol.", getSupportedProtocol());
    }
  }

  @Override
  public void awaitStop() throws InterruptedException {
    shutdown.await();
    scheduler.awaitStop();
    LOG.info("{} Subprotocol stopped.", getSupportedProtocol());
  }

  @Override
  public void processMessage(final Capability cap, final Message message) {
    checkArgument(
        getSupportedCapabilities().contains(cap),
        "Unsupported capability passed to processMessage(): " + cap);
    LOG.trace("Process message {}, {}", cap, message.getData().getCode());
    final EthPeer peer = ethPeers.peer(message.getConnection());
    if (peer == null) {
      LOG.debug(
          "Ignoring message received from unknown peer connection: " + message.getConnection());
      return;
    }

    // Handle STATUS processing
    if (message.getData().getCode() == EthPV62.STATUS) {
      handleStatusMessage(peer, message.getData());
      return;
    } else if (!peer.statusHasBeenReceived()) {
      // Peers are required to send status messages before any other message type
      LOG.debug(
          "{} requires a Status ({}) message to be sent first.  Instead, received message {}.  Disconnecting from {}.",
          this.getClass().getSimpleName(),
          EthPV62.STATUS,
          message.getData().getCode(),
          peer);
      peer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
      return;
    }

    // Dispatch eth message
    final EthMessage ethMessage = new EthMessage(peer, message.getData());
    if (!peer.validateReceivedMessage(ethMessage)) {
      LOG.debug("Unsolicited message received from, disconnecting: {}", peer);
      peer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
      return;
    }
    ethPeers.dispatchMessage(peer, ethMessage);
    ethMessages.dispatch(ethMessage);
  }

  @Override
  public void handleNewConnection(final PeerConnection connection) {
    ethPeers.registerConnection(connection, peerValidators);
    final EthPeer peer = ethPeers.peer(connection);
    if (peer.statusHasBeenSentToPeer()) {
      return;
    }

    final Capability cap = connection.capability(getSupportedProtocol());
    final ForkId latestForkId =
        cap.getVersion() >= 64 ? forkIdManager.getForkIdForChainHead() : null;
    // TODO: look to consolidate code below if possible
    // making status non-final and implementing it above would be one way.
    final StatusMessage status =
        StatusMessage.create(
            cap.getVersion(),
            networkId,
            blockchain.getChainHead().getTotalDifficulty(),
            blockchain.getChainHeadHash(),
            genesisHash,
            latestForkId);
    try {
      LOG.debug("Sending status message to {}.", peer);
      peer.send(status);
      peer.registerStatusSent();
    } catch (final PeerNotConnected peerNotConnected) {
      // Nothing to do.
    }
  }

  @Override
  public void handleDisconnect(
      final PeerConnection connection,
      final DisconnectReason reason,
      final boolean initiatedByPeer) {
    ethPeers.registerDisconnect(connection);
    LOG.debug(
        "Disconnect - {} - {} - {} - {} peers left",
        initiatedByPeer ? "Inbound" : "Outbound",
        reason,
        connection.getPeerInfo(),
        ethPeers.peerCount());
  }

  private void handleStatusMessage(final EthPeer peer, final MessageData data) {
    final StatusMessage status = StatusMessage.readFrom(data);
    try {
      if (!status.networkId().equals(networkId)) {
        LOG.debug("{} has mismatched network id: {}", peer, status.networkId());
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      } else if (!forkIdManager.peerCheck(status.forkId()) && status.protocolVersion() > 63) {
        LOG.debug(
            "{} has matching network id ({}), but non-matching fork id: {}",
            peer,
            networkId,
            status.forkId());
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      } else if (forkIdManager.peerCheck(status.genesisHash())) {
        LOG.debug(
            "{} has matching network id ({}), but non-matching genesis hash: {}",
            peer,
            networkId,
            status.genesisHash());
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      } else {
        LOG.debug("Received status message from {}: {}", peer, status);
        peer.registerStatusReceived(
            status.bestHash(), status.totalDifficulty(), status.protocolVersion());
      }
    } catch (final RLPException e) {
      LOG.debug("Unable to parse status message.", e);
      // Parsing errors can happen when clients broadcast network ids outside of the int range,
      // So just disconnect with "subprotocol" error rather than "breach of protocol".
      peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
    }
  }

  @Override
  public void blockMined(final Block block) {
    // This assumes the block has already been included in the chain
    final Difficulty totalDifficulty =
        blockchain
            .getTotalDifficultyByHash(block.getHash())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unable to get total difficulty from blockchain for mined block."));
    blockBroadcaster.propagate(block, totalDifficulty);
  }

  public List<Bytes> getForkIdAsBytesList() {
    return forkIdManager.getForkIdForChainHead().getForkIdAsBytesList();
  }
}
