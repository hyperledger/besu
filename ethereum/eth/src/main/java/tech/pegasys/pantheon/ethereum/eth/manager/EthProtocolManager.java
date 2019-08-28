/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.manager;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.EthProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV62;
import tech.pegasys.pantheon.ethereum.eth.messages.StatusMessage;
import tech.pegasys.pantheon.ethereum.eth.sync.BlockBroadcaster;
import tech.pegasys.pantheon.ethereum.p2p.network.ProtocolManager;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Message;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.math.BigInteger;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EthProtocolManager implements ProtocolManager, MinedBlockObserver {
  private static final Logger LOG = LogManager.getLogger();
  private static final List<Capability> FAST_SYNC_CAPS =
      Collections.singletonList(EthProtocol.ETH63);
  private static final List<Capability> FULL_SYNC_CAPS =
      Arrays.asList(EthProtocol.ETH62, EthProtocol.ETH63);

  private final EthScheduler scheduler;
  private final CountDownLatch shutdown;
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private final Hash genesisHash;
  private final BigInteger networkId;
  private final EthPeers ethPeers;
  private final EthMessages ethMessages;
  private final EthContext ethContext;
  private final boolean fastSyncEnabled;
  private List<Capability> supportedCapabilities;
  private final Blockchain blockchain;
  private final BlockBroadcaster blockBroadcaster;

  public EthProtocolManager(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final BigInteger networkId,
      final boolean fastSyncEnabled,
      final EthScheduler scheduler,
      final EthProtocolConfiguration ethereumWireProtocolConfiguration,
      final Clock clock,
      final MetricsSystem metricsSystem) {
    this.networkId = networkId;
    this.scheduler = scheduler;
    this.blockchain = blockchain;
    this.fastSyncEnabled = fastSyncEnabled;

    this.shutdown = new CountDownLatch(1);
    genesisHash = blockchain.getBlockHashByNumber(0L).get();

    ethPeers = new EthPeers(getSupportedProtocol(), clock, metricsSystem);
    ethMessages = new EthMessages();
    ethContext = new EthContext(ethPeers, ethMessages, scheduler);

    this.blockBroadcaster = new BlockBroadcaster(ethContext);

    // Set up request handlers
    new EthServer(blockchain, worldStateArchive, ethMessages, ethereumWireProtocolConfiguration);
  }

  public EthProtocolManager(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final BigInteger networkId,
      final boolean fastSyncEnabled,
      final int syncWorkers,
      final int txWorkers,
      final int computationWorkers,
      final Clock clock,
      final MetricsSystem metricsSystem) {
    this(
        blockchain,
        worldStateArchive,
        networkId,
        fastSyncEnabled,
        new EthScheduler(syncWorkers, txWorkers, computationWorkers, metricsSystem),
        EthProtocolConfiguration.defaultConfig(),
        clock,
        metricsSystem);
  }

  public EthProtocolManager(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final BigInteger networkId,
      final boolean fastSyncEnabled,
      final int syncWorkers,
      final int txWorkers,
      final int computationWorkers,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final EthProtocolConfiguration ethereumWireProtocolConfiguration) {
    this(
        blockchain,
        worldStateArchive,
        networkId,
        fastSyncEnabled,
        new EthScheduler(syncWorkers, txWorkers, computationWorkers, metricsSystem),
        ethereumWireProtocolConfiguration,
        clock,
        metricsSystem);
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

  @Override
  public List<Capability> getSupportedCapabilities() {
    if (supportedCapabilities == null) {
      supportedCapabilities = fastSyncEnabled ? FAST_SYNC_CAPS : FULL_SYNC_CAPS;
    }
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
      peer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
      return;
    }

    // Dispatch eth message
    final EthMessage ethMessage = new EthMessage(peer, message.getData());
    if (!peer.validateReceivedMessage(ethMessage)) {
      LOG.warn("Unsolicited message received from {}, disconnecting", peer);
      peer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
      return;
    }
    ethPeers.dispatchMessage(peer, ethMessage);
    ethMessages.dispatch(ethMessage);
  }

  @Override
  public void handleNewConnection(final PeerConnection connection) {
    ethPeers.registerConnection(connection);
    final EthPeer peer = ethPeers.peer(connection);
    if (peer.statusHasBeenSentToPeer()) {
      return;
    }

    final Capability cap = connection.capability(getSupportedProtocol());
    final StatusMessage status =
        StatusMessage.create(
            cap.getVersion(),
            networkId,
            blockchain.getChainHead().getTotalDifficulty(),
            blockchain.getChainHeadHash(),
            genesisHash);
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
    if (initiatedByPeer) {
      LOG.debug(
          "Peer requested to be disconnected ({}), {} peers left: {}",
          reason,
          ethPeers.peerCount(),
          ethPeers);
    } else {
      LOG.debug(
          "Disconnecting from peer ({}), {} peers left: {}",
          reason,
          ethPeers.peerCount(),
          ethPeers);
    }
  }

  private void handleStatusMessage(final EthPeer peer, final MessageData data) {
    final StatusMessage status = StatusMessage.readFrom(data);
    try {
      if (!status.networkId().equals(networkId)) {
        LOG.debug("Disconnecting from peer with mismatched network id: {}", status.networkId());
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      } else if (!status.genesisHash().equals(genesisHash)) {
        LOG.debug(
            "Disconnecting from peer with matching network id ({}), but non-matching genesis hash: {}",
            networkId,
            status.genesisHash());
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      } else {
        LOG.debug("Received status message from {}: {}", peer, status);
        peer.registerStatusReceived(status.bestHash(), status.totalDifficulty());
      }
    } catch (final RLPException e) {
      LOG.debug("Unable to parse status message, disconnecting from peer.", e);
      // Parsing errors can happen when clients broadcast network ids outside of the int range,
      // So just disconnect with "subprotocol" error rather than "breach of protocol".
      peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
    }
  }

  @Override
  public void blockMined(final Block block) {
    // This assumes the block has already been included in the chain
    final UInt256 totalDifficulty =
        blockchain
            .getTotalDifficultyByHash(block.getHash())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unable to get total difficulty from blockchain for mined block."));
    blockBroadcaster.propagate(block, totalDifficulty);
  }
}
