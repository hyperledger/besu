package net.consensys.pantheon.ethereum.eth.sync.tasks;

import net.consensys.pantheon.ethereum.eth.manager.AbstractEthTask;
import net.consensys.pantheon.ethereum.eth.manager.EthContext;
import net.consensys.pantheon.ethereum.eth.manager.EthPeers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Wait for a single new peer to connect. */
public class WaitForPeerTask extends AbstractEthTask<Void> {
  private static final Logger LOG = LogManager.getLogger();

  private final EthContext ethContext;
  private volatile Long peerListenerId;

  private WaitForPeerTask(final EthContext ethContext) {
    this.ethContext = ethContext;
  }

  public static WaitForPeerTask create(final EthContext ethContext) {
    return new WaitForPeerTask(ethContext);
  }

  @Override
  protected void executeTask() {
    final EthPeers ethPeers = ethContext.getEthPeers();
    LOG.info(
        "Waiting for new peer connection. {} peers currently connected, {} idle.",
        ethPeers.peerCount(),
        ethPeers.idlePeer().isPresent() ? "Some peers" : "No peers");
    // Listen for peer connections and complete task when we hit our target
    peerListenerId =
        ethPeers.subscribeConnect(
            (peer) -> {
              LOG.info("Finished waiting for peer connection.");
              // We hit our target
              result.get().complete(null);
            });
  }

  @Override
  protected void cleanup() {
    super.cleanup();
    final Long listenerId = peerListenerId;
    if (listenerId != null) {
      ethContext.getEthPeers().unsubscribeConnect(listenerId);
    }
  }
}
