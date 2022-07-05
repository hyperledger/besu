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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Difficulty;
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
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EthProtocolManager implements ProtocolManager, MinedBlockObserver {
  private static final Logger LOG = LoggerFactory.getLogger(EthProtocolManager.class);

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
  // The max size of messages (in bytes)
  private final int maxMessageSize;

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
    this.maxMessageSize = ethereumWireProtocolConfiguration.getMaxMessageSize();

    this.shutdown = new CountDownLatch(1);
    genesisHash = blockchain.getBlockHashByNumber(0L).get();

    this.forkIdManager = forkIdManager;

    this.ethPeers = ethPeers;
    this.ethMessages = ethMessages;
    this.ethContext = ethContext;

    this.blockBroadcaster = new BlockBroadcaster(ethContext);

    supportedCapabilities = calculateCapabilities(fastSyncEnabled);

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

  private List<Capability> calculateCapabilities(final boolean fastSyncEnabled) {
    final ImmutableList.Builder<Capability> capabilities = ImmutableList.builder();
    if (!fastSyncEnabled) {
      capabilities.add(EthProtocol.ETH62);
    }
    capabilities.add(EthProtocol.ETH63);
    capabilities.add(EthProtocol.ETH64);
    capabilities.add(EthProtocol.ETH65);
    capabilities.add(EthProtocol.ETH66);

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
    final MessageData messageData = message.getData();
    final int code = messageData.getCode();
    LOG.info("Process message {}, {}", cap, code);

    // Handle STATUS processing
    if (code == EthPV62.STATUS) {
      handleStatusMessage(message.getConnection(), messageData);
      return;
    }

    final EthPeer ethPeer = ethPeers.peer(message.getConnection());
    if (ethPeer == null) {
      LOG.debug(
          "Ignoring message received from unknown peer connection: " + message.getConnection());
      return;
    }

    if (messageData.getSize() > maxMessageSize) {
      LOG.warn(
          "Received message exceeding size limit of {} bytes: {} bytes. Disconnecting from {}",
          maxMessageSize,
          messageData.getSize(),
          ethPeer);
      ethPeer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      return;
    }

    if (!message.getConnection().statusHasBeenReceived()) {
      // Peers are required to send status messages before any other message type
      LOG.info(
          "{} requires a Status ({}) message to be sent first.  Instead, received message {}.  Disconnecting from {}.",
          this.getClass().getSimpleName(),
          EthPV62.STATUS,
          code,
          ethPeer);
      ethPeer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
      return;
    }

    final EthMessage ethMessage = new EthMessage(ethPeer, messageData);

    if (!ethPeer.validateReceivedMessage(ethMessage, getSupportedProtocol())) {
      LOG.info(
          "Unsolicited message received from peer {}, disconnecting connection: {}",
          message.getConnection().getPeer(),
          System.identityHashCode(message.getConnection()));
      ethPeer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
      return;
    }

    // This will handle responses
    ethPeers.dispatchMessage(ethPeer, ethMessage, getSupportedProtocol());

    // This will handle requests
    Optional<MessageData> maybeResponseData = Optional.empty();
    try {
      if (EthProtocol.isEth66Compatible(cap) && EthProtocol.requestIdCompatible(code)) {
        final Map.Entry<BigInteger, MessageData> requestIdAndEthMessage =
            ethMessage.getData().unwrapMessageData();
        maybeResponseData =
            ethMessages
                .dispatch(new EthMessage(ethPeer, requestIdAndEthMessage.getValue()))
                .map(responseData -> responseData.wrapMessageData(requestIdAndEthMessage.getKey()));
      } else {
        maybeResponseData = ethMessages.dispatch(ethMessage);
      }
    } catch (final RLPException e) {
      LOG.info(
          "Received malformed message {} , disconnecting: {}", messageData.getData(), ethPeer, e);

      ethPeer.disconnect(DisconnectMessage.DisconnectReason.BREACH_OF_PROTOCOL);
    }
    maybeResponseData.ifPresent(
        responseData -> {
          try {
            ethPeer.send(responseData, getSupportedProtocol());
          } catch (final PeerNotConnected __) {
            // Peer disconnected before we could respond - nothing to do
          }
        });
  }

  @Override
  public void handleNewConnection(final PeerConnection connection) {
    LOG.info(
        "Handling new connection ({}) with peer {}. Sending status message",
        System.identityHashCode(connection),
        connection.getPeer().getId());
    ethPeers.registerConnection(connection, peerValidators);
    final EthPeer peer = ethPeers.peer(connection);

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
      LOG.info(
          "Sending status message to {}, connection {}",
          peer.getConnection().getPeer().getId(),
          System.identityHashCode(connection));
      if (peer.isDisconnected()) {
        // although it isn't yet confirmed that the new connection is ready,
        // if the peer's existing connection is disconnected, optimistically replace it
        peer.setConnection(connection);
      }
      peer.send(status, getSupportedProtocol(), connection);
      if (connection.registerStatusSentAndCheckIfReady()) {
        ethPeers.maybeUseReadyConnection(connection);
      }
    } catch (final PeerNotConnected peerNotConnected) {
      // Nothing to do.
    }
    LOG.trace("{}", ethPeers);
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
    LOG.trace("{}", ethPeers);
  }

  private void handleStatusMessage(final PeerConnection peerConnection, final MessageData data) {
    LOG.info("Received a Status Message connection {}", System.identityHashCode(peerConnection));
    final StatusMessage status = StatusMessage.readFrom(data);
    try {
      if (!status.networkId().equals(networkId)) {
        LOG.info("{} has mismatched network id: {}", peerConnection, status.networkId());
        peerConnection.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      } else if (!forkIdManager.peerCheck(status.forkId()) && status.protocolVersion() > 63) {
        LOG.info(
            "{} has matching network id ({}), but non-matching fork id: {}",
            System.identityHashCode(peerConnection),
            networkId,
            status.forkId());
        peerConnection.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      } else if (forkIdManager.peerCheck(status.genesisHash())) {
        LOG.info(
            "{} has matching network id ({}), but non-matching genesis hash: {}",
            peerConnection,
            networkId,
            status.genesisHash());
        peerConnection.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      } else {
        LOG.info(
            "Received status message from {}, connection {}: {}",
            peerConnection.getPeer().getId(),
            System.identityHashCode(peerConnection),
            status);
        if (peerConnection.registerStatusReceivedAndCheckIfReady()) {
          final EthPeer peer = ethPeers.peer(peerConnection);
          peer.chainState().statusReceived(status.bestHash(), status.totalDifficulty());
          peer.setProtocolVersion(status.protocolVersion());
          ethPeers.maybeUseReadyConnection(peerConnection);
        }
      }
    } catch (final RLPException e) {
      LOG.info("Unable to parse status message.", e);
      // Parsing errors can happen when clients broadcast network ids outside the int range,
      // So just disconnect with "subprotocol" error rather than "breach of protocol".
      peerConnection.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
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
    final ForkId chainHeadForkId = forkIdManager.getForkIdForChainHead();
    return chainHeadForkId == null
        ? Collections.emptyList()
        : chainHeadForkId.getForkIdAsBytesList();
  }
}
