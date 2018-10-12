package net.consensys.pantheon.consensus.ibft.protocol;

import net.consensys.pantheon.consensus.ibft.IbftEvent;
import net.consensys.pantheon.consensus.ibft.IbftEventQueue;
import net.consensys.pantheon.consensus.ibft.IbftEvents;
import net.consensys.pantheon.ethereum.p2p.api.Message;
import net.consensys.pantheon.ethereum.p2p.api.PeerConnection;
import net.consensys.pantheon.ethereum.p2p.api.ProtocolManager;
import net.consensys.pantheon.ethereum.p2p.wire.Capability;
import net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;

import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IbftProtocolManager implements ProtocolManager {
  private final IbftEventQueue ibftEventQueue;

  private final Logger LOG = LogManager.getLogger();

  /**
   * Constructor for the ibft protocol manager
   *
   * @param ibftEventQueue Entry point into the ibft event processor
   */
  public IbftProtocolManager(final IbftEventQueue ibftEventQueue) {
    this.ibftEventQueue = ibftEventQueue;
  }

  @Override
  public String getSupportedProtocol() {
    return IbftSubProtocol.get().getName();
  }

  @Override
  public List<Capability> getSupportedCapabilities() {
    return Arrays.asList(IbftSubProtocol.IBFV1);
  }

  @Override
  public void stop() {}

  @Override
  public void awaitStop() throws InterruptedException {}

  /**
   * This function is called by the P2P framework when an "IBF" message has been received. This
   * function is responsible for:
   *
   * <ul>
   *   <li>Determining if the message was from a current validator (discard if not)
   *   <li>Determining if the message received was for the 'current round', discarding if old and
   *       buffering for the future if ahead of current state.
   *   <li>If the received message is otherwise valid, it is sent to the state machine which is
   *       responsible for determining how to handle the message given its internal state.
   * </ul>
   *
   * @param cap The capability under which the message was transmitted.
   * @param message The message to be decoded.
   */
  @Override
  public void processMessage(final Capability cap, final Message message) {
    final IbftEvent messageEvent = IbftEvents.fromMessage(message);
    ibftEventQueue.add(messageEvent);
  }

  @Override
  public void handleNewConnection(final PeerConnection peerConnection) {}

  @Override
  public void handleDisconnect(
      final PeerConnection peerConnection,
      final DisconnectReason disconnectReason,
      final boolean initiatedByPeer) {}

  @Override
  public boolean hasSufficientPeers() {
    return true;
  }
}
