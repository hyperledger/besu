package net.consensys.pantheon.ethereum.eth.manager;

import net.consensys.pantheon.ethereum.eth.manager.AbstractPeerTask.PeerTaskResult;
import net.consensys.pantheon.ethereum.eth.manager.exceptions.NoAvailablePeersException;
import net.consensys.pantheon.ethereum.eth.manager.exceptions.PeerDisconnectedException;
import net.consensys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;

import java.util.Optional;

public abstract class AbstractPeerTask<R> extends AbstractEthTask<PeerTaskResult<R>> {
  protected Optional<EthPeer> assignedPeer = Optional.empty();
  protected final EthContext ethContext;

  protected AbstractPeerTask(final EthContext ethContext) {
    this.ethContext = ethContext;
  }

  @Override
  protected void executeTask() {
    EthPeer peer;
    if (assignedPeer.isPresent()) {
      peer = assignedPeer.get();
    } else {
      // Try to find a peer
      final Optional<EthPeer> maybePeer = findSuitablePeer();
      if (!maybePeer.isPresent()) {
        result.get().completeExceptionally(new NoAvailablePeersException());
        return;
      }
      peer = maybePeer.get();
    }

    try {
      executeTaskWithPeer(peer);
    } catch (final PeerNotConnected e) {
      result.get().completeExceptionally(new PeerDisconnectedException());
    }
  }

  protected Optional<EthPeer> findSuitablePeer() {
    return this.ethContext.getEthPeers().idlePeer();
  }

  protected abstract void executeTaskWithPeer(EthPeer peer) throws PeerNotConnected;

  public AbstractPeerTask<R> assignPeer(final EthPeer peer) {
    assignedPeer = Optional.of(peer);
    return this;
  }

  public static class PeerTaskResult<T> {
    private final EthPeer peer;
    private final T result;

    public PeerTaskResult(final EthPeer peer, final T result) {
      this.peer = peer;
      this.result = result;
    }

    public EthPeer getPeer() {
      return peer;
    }

    public T getResult() {
      return result;
    }
  }
}
