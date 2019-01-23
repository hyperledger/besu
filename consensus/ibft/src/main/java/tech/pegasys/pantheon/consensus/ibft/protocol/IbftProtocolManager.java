/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibft.protocol;

import tech.pegasys.pantheon.consensus.ibft.IbftEventQueue;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.IbftEvent;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.IbftEvents;
import tech.pegasys.pantheon.consensus.ibft.network.PeerConnectionTracker;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.api.ProtocolManager;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;

import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IbftProtocolManager implements ProtocolManager {
  private final IbftEventQueue ibftEventQueue;

  private final Logger LOG = LogManager.getLogger();
  private final PeerConnectionTracker peers;

  /**
   * Constructor for the ibft protocol manager
   *
   * @param ibftEventQueue Entry point into the ibft event processor
   * @param peers Used to track all connected IBFT peers.
   */
  public IbftProtocolManager(
      final IbftEventQueue ibftEventQueue, final PeerConnectionTracker peers) {
    this.ibftEventQueue = ibftEventQueue;
    this.peers = peers;
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
  public void handleNewConnection(final PeerConnection peerConnection) {
    peers.add(peerConnection);
  }

  @Override
  public void handleDisconnect(
      final PeerConnection peerConnection,
      final DisconnectReason disconnectReason,
      final boolean initiatedByPeer) {
    peers.remove(peerConnection);
  }
}
