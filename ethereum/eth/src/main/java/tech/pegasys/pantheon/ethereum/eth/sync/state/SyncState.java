package tech.pegasys.pantheon.ethereum.eth.sync.state;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.SyncStatus;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Optional;

public class SyncState {
  private final Blockchain blockchain;
  private final EthContext ethContext;

  private final long startingBlock;
  private final PendingBlocks pendingBlocks;
  private Optional<SyncTarget> syncTarget = Optional.empty();

  public SyncState(
      final Blockchain blockchain, final EthContext ethContext, final PendingBlocks pendingBlocks) {
    this.blockchain = blockchain;
    this.ethContext = ethContext;
    this.startingBlock = chainHeadNumber();
    this.pendingBlocks = pendingBlocks;
  }

  public SyncStatus syncStatus() {
    return new SyncStatus(startingBlock(), chainHeadNumber(), bestChainHeight());
  }

  public long startingBlock() {
    return startingBlock;
  }

  public long chainHeadNumber() {
    return blockchain.getChainHeadBlockNumber();
  }

  public UInt256 chainHeadTotalDifficulty() {
    return blockchain.getChainHead().getTotalDifficulty();
  }

  public PendingBlocks pendingBlocks() {
    return pendingBlocks;
  }

  public Optional<SyncTarget> syncTarget() {
    return syncTarget;
  }

  public SyncTarget setSyncTarget(final EthPeer peer, final BlockHeader commonAncestor) {
    final SyncTarget target = new SyncTarget(peer, commonAncestor);
    this.syncTarget = Optional.of(target);
    return target;
  }

  public void clearSyncTarget() {
    this.syncTarget = Optional.empty();
  }

  public long bestChainHeight() {
    final long localChainHeight = blockchain.getChainHeadBlockNumber();
    return bestChainHeight(localChainHeight);
  }

  public long bestChainHeight(final long localChainHeight) {
    return Math.max(
        localChainHeight,
        ethContext
            .getEthPeers()
            .bestPeer()
            .map(p -> p.chainState().getEstimatedHeight())
            .orElse(localChainHeight));
  }
}
