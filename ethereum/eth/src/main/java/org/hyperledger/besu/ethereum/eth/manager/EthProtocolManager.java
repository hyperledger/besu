/*
 * Copyright contributors to Hyperledger Besu
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
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.forkid.ForkId;
import org.hyperledger.besu.ethereum.forkid.ForkIdManager;
import org.hyperledger.besu.ethereum.p2p.network.ProtocolManager;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;
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
  private final Optional<MergePeerFilter> mergePeerFilter;

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
      final Optional<MergePeerFilter> mergePeerFilter,
      final SynchronizerConfiguration synchronizerConfiguration,
      final EthScheduler scheduler,
      final ForkIdManager forkIdManager) {
    this.networkId = networkId;
    this.peerValidators = peerValidators;
    this.scheduler = scheduler;
    this.blockchain = blockchain;
    this.mergePeerFilter = mergePeerFilter;
    this.shutdown = new CountDownLatch(1);
    this.genesisHash = blockchain.getBlockHashByNumber(0L).orElse(Hash.ZERO);

    this.forkIdManager = forkIdManager;

    this.ethPeers = ethPeers;
    this.ethMessages = ethMessages;
    this.ethContext = ethContext;

    this.blockBroadcaster = new BlockBroadcaster(ethContext);

    supportedCapabilities =
        calculateCapabilities(synchronizerConfiguration, ethereumWireProtocolConfiguration);

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
      final Optional<MergePeerFilter> mergePeerFilter,
      final SynchronizerConfiguration synchronizerConfiguration,
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
        mergePeerFilter,
        synchronizerConfiguration,
        scheduler,
        new ForkIdManager(
            blockchain,
            Collections.emptyList(),
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
      final Optional<MergePeerFilter> mergePeerFilter,
      final SynchronizerConfiguration synchronizerConfiguration,
      final EthScheduler scheduler,
      final List<Long> blockNumberForks,
      final List<Long> timestampForks) {
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
        mergePeerFilter,
        synchronizerConfiguration,
        scheduler,
        new ForkIdManager(
            blockchain,
            blockNumberForks,
            timestampForks,
            ethereumWireProtocolConfiguration.isLegacyEth64ForkIdEnabled()));
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
      final SynchronizerConfiguration synchronizerConfiguration,
      final EthProtocolConfiguration ethProtocolConfiguration) {
    final List<Capability> capabilities = new ArrayList<>();

    if (SyncMode.isFullSync(synchronizerConfiguration.getSyncMode())) {
      capabilities.add(EthProtocol.ETH62);
    }
    capabilities.add(EthProtocol.ETH63);
    capabilities.add(EthProtocol.ETH64);
    capabilities.add(EthProtocol.ETH65);
    capabilities.add(EthProtocol.ETH66);

    // Version 67 removes the GetNodeData and NodeData
    // Fast sync depends on GetNodeData and NodeData
    // see https://eips.ethereum.org/EIPS/eip-4938
    if (!Objects.equals(SyncMode.FAST, synchronizerConfiguration.getSyncMode())) {
      capabilities.add(EthProtocol.ETH67);
      capabilities.add(EthProtocol.ETH68);
    }

    capabilities.removeIf(cap -> cap.getVersion() > ethProtocolConfiguration.getMaxEthCapability());
    capabilities.removeIf(cap -> cap.getVersion() < ethProtocolConfiguration.getMinEthCapability());

    return Collections.unmodifiableList(capabilities);
  }

  @Override
  public int getHighestProtocolVersion() {
    return getSupportedCapabilities().stream()
        .max(Comparator.comparing(Capability::getVersion))
        .map(Capability::getVersion)
        .orElse(0);
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
    EthProtocolLogger.logProcessMessage(cap, code);
    final EthPeer ethPeer = ethPeers.peer(message.getConnection());
    if (ethPeer == null) {
      LOG.debug(
          "Ignoring message received from unknown peer connection: {}", message.getConnection());
      return;
    }

    // Handle STATUS processing
    if (code == EthPV62.STATUS) {
      handleStatusMessage(ethPeer, message);
      return;
    } else if (!ethPeer.statusHasBeenReceived()) {
      // Peers are required to send status messages before any other message type
      LOG.debug(
          "{} requires a Status ({}) message to be sent first.  Instead, received message {} (BREACH_OF_PROTOCOL).  Disconnecting from {}.",
          this.getClass().getSimpleName(),
          EthPV62.STATUS,
          code,
          ethPeer);
      ethPeer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
      return;
    }

    if (this.mergePeerFilter.isPresent()) {
      if (this.mergePeerFilter.get().disconnectIfGossipingBlocks(message, ethPeer)) {
        LOG.debug("Post-merge disconnect: peer still gossiping blocks {}", ethPeer);
        handleDisconnect(ethPeer.getConnection(), DisconnectReason.SUBPROTOCOL_TRIGGERED, false);
        return;
      }
    }

    final EthMessage ethMessage = new EthMessage(ethPeer, messageData);

    if (!ethPeer.validateReceivedMessage(ethMessage, getSupportedProtocol())) {
      LOG.debug(
          "Unsolicited message received (BREACH_OF_PROTOCOL), disconnecting from EthPeer: {}",
          ethPeer);
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
      LOG.debug(
          "Received malformed message {} (BREACH_OF_PROTOCOL), disconnecting: {}",
          messageData.getData(),
          ethPeer,
          e);

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
    ethPeers.registerNewConnection(connection, peerValidators);
    final EthPeer peer = ethPeers.peer(connection);

    final Capability cap = connection.capability(getSupportedProtocol());
    final ForkId latestForkId =
        cap.getVersion() >= 64 ? forkIdManager.getForkIdForChainHead() : null;
    final StatusMessage status =
        StatusMessage.create(
            cap.getVersion(),
            networkId,
            blockchain.getChainHead().getTotalDifficulty(),
            blockchain.getChainHeadHash(),
            genesisHash,
            latestForkId);
    try {
      LOG.trace("Sending status message to {} for connection {}.", peer.getId(), connection);
      peer.send(status, getSupportedProtocol(), connection);
      peer.registerStatusSent(connection);
    } catch (final PeerNotConnected peerNotConnected) {
      // Nothing to do.
    }
    LOG.trace("{}", ethPeers);
  }

  @Override
  public boolean shouldConnect(final Peer peer, final boolean incoming) {
    if (peer.getForkId().map(forkId -> forkIdManager.peerCheck(forkId)).orElse(true)) {
      LOG.trace("ForkId OK or not available");
      if (ethPeers.shouldConnect(peer, incoming)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void handleDisconnect(
      final PeerConnection connection,
      final DisconnectReason reason,
      final boolean initiatedByPeer) {
    if (ethPeers.registerDisconnect(connection)) {
      LOG.info(
          "Disconnect - {} - {} - {}... - {} peers left",
          initiatedByPeer ? "Inbound" : "Outbound",
          reason,
          connection.getPeer().getId().slice(0, 8),
          ethPeers.peerCount());
      LOG.trace(ethPeers.toString());
    }
  }

  private void handleStatusMessage(final EthPeer peer, final Message message) {
    final StatusMessage status = StatusMessage.readFrom(message.getData());
    final ForkId forkId = status.forkId();
    peer.getConnection().getPeer().setForkId(forkId);
    try {
      if (!status.networkId().equals(networkId)) {
        LOG.debug("Mismatched network id: {}, EthPeer {}", status.networkId(), peer);
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      } else if (!forkIdManager.peerCheck(forkId) && status.protocolVersion() > 63) {
        LOG.debug(
            "{} has matching network id ({}), but non-matching fork id: {}",
            peer,
            networkId,
            forkId);
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      } else if (forkIdManager.peerCheck(status.genesisHash())) {
        LOG.debug(
            "{} has matching network id ({}), but non-matching genesis hash: {}",
            peer,
            networkId,
            status.genesisHash());
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      } else if (mergePeerFilter.isPresent()
          && mergePeerFilter.get().disconnectIfPoW(status, peer)) {
        LOG.debug("Post-merge disconnect: peer still PoW {}", peer);
        handleDisconnect(peer.getConnection(), DisconnectReason.SUBPROTOCOL_TRIGGERED, false);
      } else {
        LOG.debug(
            "Received status message from {}: {} with connection {}",
            peer,
            status,
            message.getConnection());
        peer.registerStatusReceived(
            status.bestHash(),
            status.totalDifficulty(),
            status.protocolVersion(),
            message.getConnection());
      }
    } catch (final RLPException e) {
      LOG.debug("Unable to parse status message from peer {}.", peer, e);
      // Parsing errors can happen when clients broadcast network ids outside the int range,
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
    final ForkId chainHeadForkId = forkIdManager.getForkIdForChainHead();
    return chainHeadForkId == null
        ? Collections.emptyList()
        : chainHeadForkId.getForkIdAsBytesList();
  }
}
