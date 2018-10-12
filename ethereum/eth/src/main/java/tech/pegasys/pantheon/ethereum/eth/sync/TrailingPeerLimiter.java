package tech.pegasys.pantheon.ethereum.eth.sync;

import static org.apache.logging.log4j.LogManager.getLogger;

import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent.EventType;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeers;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;

public class TrailingPeerLimiter implements BlockAddedObserver {

  private static final Logger LOG = getLogger();

  private static final Comparator<EthPeer> BY_CHAIN_HEIGHT =
      Comparator.comparing(peer -> peer.chainState().getEstimatedHeight());
  // Note rechecking only on blocks that are a multiple of 100 is just a simple way of limiting
  // how often we rerun the check.
  private static final int RECHECK_PEERS_WHEN_BLOCK_NUMBER_MULTIPLE_OF = 100;
  private final EthPeers ethPeers;
  private final Blockchain blockchain;
  private final long trailingPeerBlocksBehindThreshold;
  private final int maxTrailingPeers;

  public TrailingPeerLimiter(
      final EthPeers ethPeers,
      final Blockchain blockchain,
      final long trailingPeerBlocksBehindThreshold,
      final int maxTrailingPeers) {
    this.ethPeers = ethPeers;
    this.blockchain = blockchain;
    this.trailingPeerBlocksBehindThreshold = trailingPeerBlocksBehindThreshold;
    this.maxTrailingPeers = maxTrailingPeers;
  }

  public void enforceTrailingPeerLimit() {
    final List<EthPeer> trailingPeers =
        ethPeers
            .availablePeers()
            .filter(peer -> peer.chainState().hasEstimatedHeight())
            .filter(
                peer ->
                    peer.chainState().getEstimatedHeight() + trailingPeerBlocksBehindThreshold
                        < blockchain.getChainHeadBlockNumber())
            .sorted(BY_CHAIN_HEIGHT)
            .collect(Collectors.toList());

    while (!trailingPeers.isEmpty() && trailingPeers.size() > maxTrailingPeers) {
      final EthPeer peerToDisconnect = trailingPeers.remove(0);
      LOG.info("Enforcing trailing peers limit by disconnecting {}", peerToDisconnect);
      peerToDisconnect.disconnect(DisconnectReason.TOO_MANY_PEERS);
    }
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event, final Blockchain blockchain) {
    if (event.getEventType() != EventType.FORK
        && event.getBlock().getHeader().getNumber() % RECHECK_PEERS_WHEN_BLOCK_NUMBER_MULTIPLE_OF
            == 0) {
      enforceTrailingPeerLimit();
    }
  }
}
