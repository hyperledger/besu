package net.consensys.pantheon.ethereum.eth.sync.tasks;

import net.consensys.pantheon.ethereum.eth.manager.AbstractEthTask;
import net.consensys.pantheon.ethereum.eth.manager.EthContext;
import net.consensys.pantheon.ethereum.eth.manager.EthPeers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Waits for some number of peers to connect. */
public class WaitForPeersTask extends AbstractEthTask<Void> {
  private static final Logger LOG = LogManager.getLogger();

  private final int targetPeerCount;
  private final EthContext ethContext;
  private volatile Long peerListenerId;

  private WaitForPeersTask(final EthContext ethContext, final int targetPeerCount) {
    this.targetPeerCount = targetPeerCount;
    this.ethContext = ethContext;
  }

  public static WaitForPeersTask create(final EthContext ethContext, final int targetPeerCount) {
    return new WaitForPeersTask(ethContext, targetPeerCount);
  }

  @Override
  protected void executeTask() {
    final EthPeers ethPeers = ethContext.getEthPeers();
    if (ethPeers.peerCount() >= targetPeerCount) {
      // We already hit our target
      result.get().complete(null);
      return;
    }

    LOG.info("Waiting for {} peers to connect.", targetPeerCount);
    // Listen for peer connections and complete task when we hit our target
    peerListenerId =
        ethPeers.subscribeConnect(
            (peer) -> {
              final int peerCount = ethPeers.peerCount();
              if (peerCount >= targetPeerCount) {
                LOG.info("Finished waiting for peers to connect.", targetPeerCount);
                // We hit our target
                result.get().complete(null);
              } else {
                LOG.info("Waiting for {} peers to connect.", targetPeerCount - peerCount);
              }
            });
  }

  @Override
  protected void cleanup() {
    super.cleanup();
    final Long listenerId = peerListenerId;
    if (listenerId != null) {
      ethContext.getEthPeers().unsubscribeConnect(peerListenerId);
    }
  }
}
