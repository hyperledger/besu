package tech.pegasys.pantheon.ethereum.eth.sync.state;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.ChainState;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;

import com.google.common.base.MoreObjects;

public class SyncTarget {

  private final EthPeer peer;
  private BlockHeader commonAncestor;

  public SyncTarget(final EthPeer peer, final BlockHeader commonAncestor) {
    this.peer = peer;
    this.commonAncestor = commonAncestor;
  }

  public EthPeer peer() {
    return peer;
  }

  public BlockHeader commonAncestor() {
    return commonAncestor;
  }

  public void setCommonAncestor(final BlockHeader commonAncestor) {
    this.commonAncestor = commonAncestor;
  }

  @Override
  public String toString() {
    final ChainState chainState = peer.chainState();
    return MoreObjects.toStringHelper(this)
        .add(
            "height",
            (chainState.getEstimatedHeight() == 0 ? "?" : chainState.getEstimatedHeight()))
        .add("td", chainState.getBestBlock().getTotalDifficulty())
        .add("peer", peer)
        .toString();
  }
}
