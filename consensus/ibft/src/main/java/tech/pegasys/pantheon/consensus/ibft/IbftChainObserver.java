package tech.pegasys.pantheon.consensus.ibft;

import tech.pegasys.pantheon.consensus.ibft.ibftevent.NewChainHead;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;

/**
 * Blockchain observer that adds {@link NewChainHead} events to the event queue when a new block is
 * added to the chain head
 */
public class IbftChainObserver implements BlockAddedObserver {
  private final IbftEventQueue queue;

  public IbftChainObserver(final IbftEventQueue queue) {
    this.queue = queue;
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event, final Blockchain blockchain) {
    switch (event.getEventType()) {
      case HEAD_ADVANCED:
        queue.add(new NewChainHead(event.getBlock().getHeader()));
        break;

      default:
        throw new IllegalStateException(
            String.format("Unexpected BlockAddedEvent received: %s", event.getEventType()));
    }
  }
}
