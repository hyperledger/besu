package tech.pegasys.pantheon.ethereum.eth.manager;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV62;
import tech.pegasys.pantheon.ethereum.eth.messages.NewBlockMessage;
import tech.pegasys.pantheon.ethereum.eth.messages.StatusMessage;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;
import tech.pegasys.pantheon.ethereum.p2p.api.ProtocolManager;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EthProtocolManager implements ProtocolManager, MinedBlockObserver {
  static final int DEFAULT_REQUEST_LIMIT = 200;
  private static final Logger LOG = LogManager.getLogger();
  private static final List<Capability> FAST_SYNC_CAPS =
      Collections.singletonList(EthProtocol.ETH63);
  private static final List<Capability> FULL_SYNC_CAPS =
      Arrays.asList(EthProtocol.ETH62, EthProtocol.ETH63);

  private final EthScheduler scheduler;
  private final CountDownLatch shutdown;
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private final Hash genesisHash;
  private final int networkId;
  private final EthPeers ethPeers;
  private final EthMessages ethMessages;
  private final EthContext ethContext;
  private final boolean fastSyncEnabled;
  private List<Capability> supportedCapabilities;
  private final Blockchain blockchain;

  EthProtocolManager(
      final Blockchain blockchain,
      final int networkId,
      final boolean fastSyncEnabled,
      final int requestLimit,
      final EthScheduler scheduler) {
    this.networkId = networkId;

    this.scheduler = scheduler;
    this.blockchain = blockchain;
    this.fastSyncEnabled = fastSyncEnabled;

    this.shutdown = new CountDownLatch(1);
    genesisHash = blockchain.getBlockHashByNumber(0L).get();

    ethPeers = new EthPeers(getSupportedProtocol());
    ethMessages = new EthMessages();
    ethContext = new EthContext(getSupportedProtocol(), ethPeers, ethMessages, scheduler);

    // Set up request handlers
    new EthServer(blockchain, ethMessages, requestLimit);
  }

  EthProtocolManager(
      final Blockchain blockchain,
      final int networkId,
      final boolean fastSyncEnabled,
      final int workers,
      final int requestLimit) {
    this(blockchain, networkId, fastSyncEnabled, requestLimit, new EthScheduler(workers));
  }

  public EthProtocolManager(
      final Blockchain blockchain,
      final int networkId,
      final boolean fastSyncEnabled,
      final int workers) {
    this(blockchain, networkId, fastSyncEnabled, workers, DEFAULT_REQUEST_LIMIT);
  }

  public EthContext ethContext() {
    return ethContext;
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
      LOG.error("Message received from unknown peer connection: " + message.getConnection());
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
    peer.dispatch(ethMessage);
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
      LOG.info("Sending status message to {}.", peer);
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
      LOG.info(
          "Peer requested to be disconnected ({}), {} peers left: {}",
          reason,
          ethPeers.peerCount(),
          ethPeers);
    } else {
      LOG.info(
          "Disconnecting from peer ({}), {} peers left: {}",
          reason,
          ethPeers.peerCount(),
          ethPeers);
    }
  }

  @Override
  public boolean hasSufficientPeers() {
    return ethPeers.availablePeerCount() > 0;
  }

  private void handleStatusMessage(final EthPeer peer, final MessageData data) {
    final StatusMessage status = StatusMessage.readFrom(data);
    try {
      if (status.networkId() != networkId) {
        LOG.info("Disconnecting from peer with mismatched network id: {}", status.networkId());
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      } else if (!status.genesisHash().equals(genesisHash)) {
        LOG.warn(
            "Disconnecting from peer with matching network id ({}), but non-matching genesis hash: {}",
            networkId,
            status.genesisHash());
        peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
      } else {
        LOG.info("Received status message from {}: {}", peer, status);
        peer.registerStatusReceived(status.bestHash(), status.totalDifficulty());
      }
    } catch (final RLPException e) {
      LOG.info("Unable to parse status message, disconnecting from peer.");
      // Parsing errors can happen when clients broadcast network ids outside of the int range,
      // So just disconnect with "subprotocol" error rather than "breach of protocol".
      peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
    } finally {
      status.release();
    }
  }

  @Override
  public void blockMined(final Block block) {
    // This assumes the block has already been included in the chain

    final Optional<UInt256> totalDifficulty = blockchain.getTotalDifficultyByHash(block.getHash());
    if (!totalDifficulty.isPresent()) {
      throw new IllegalStateException(
          "Unable to get total difficulty from blockchain for mined block.");
    }

    final NewBlockMessage newBlockMessage = NewBlockMessage.create(block, totalDifficulty.get());

    ethPeers
        .availablePeers()
        .forEach(
            peer -> {
              try {
                // Send(msg) will release the NewBlockMessage's internal buffer, thus it must be
                // retained
                // prior to transmission - then released on exit from function.
                newBlockMessage.retain();
                peer.send(newBlockMessage);
              } catch (final PeerNotConnected ex) {
                // Peers may disconnect while traversing the list, this is a normal occurrence.
              }
            });
    newBlockMessage.release();
  }
}
