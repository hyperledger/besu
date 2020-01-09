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
package org.hyperledger.besu.crosschain.protocol;

import org.hyperledger.besu.consensus.common.network.PeerConnectionTracker;
import org.hyperledger.besu.consensus.common.network.ValidatorPeers;
import org.hyperledger.besu.crosschain.messagedata.CrosschainMessageCodes;
import org.hyperledger.besu.crosschain.messagedata.PingMessageData;
import org.hyperledger.besu.crosschain.messagedata.PongMessageData;
import org.hyperledger.besu.ethereum.p2p.network.ProtocolManager;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CrosschainProtocolManager implements ProtocolManager {
  private static final Logger LOG = LogManager.getLogger();

  private final PeerConnectionTracker peers;

  private final ScheduledExecutorService peerConnectionScheduler =
      Executors.newSingleThreadScheduledExecutor();
  /**
   * Constructor for the crosschain protocol manager
   *
   * @param peers Used to track all connected IBFT peers.
   */
  public CrosschainProtocolManager(final PeerConnectionTracker peers) {
    this.peers = peers;
    LOG.debug("Initial peers :{}", ((ValidatorPeers) peers).getLatestValidators());
    int initialDelay = (int) (Math.random() * 5.0) + 2;
    LOG.debug("Pinger runs in {} secs", initialDelay);
    peerConnectionScheduler.scheduleAtFixedRate(this::pinger, initialDelay, 10, TimeUnit.SECONDS);
    // peerConnectionScheduler.schedule(this::pinger, initialDelay, TimeUnit.SECONDS);

  }

  void pinger() {
    LOG.info("Sending PINGs");
    ValidatorPeers vp = (ValidatorPeers) peers;
    vp.send(PingMessageData.create(10));
  }

  @Override
  public String getSupportedProtocol() {
    return CrosschainSubProtocol.get().getName();
  }

  @Override
  public List<Capability> getSupportedCapabilities() {
    return Arrays.asList(CrosschainSubProtocol.CCH);
  }

  @Override
  public void stop() {}

  @Override
  public void awaitStop() throws InterruptedException {}

  /**
   * This function is called by the P2P framework when an "CCH" message has been received.
   *
   * @param cap The capability under which the message was transmitted.
   * @param message The message to be decoded.
   */
  @Override
  public void processMessage(final Capability cap, final Message message) {
    LOG.debug("Received CCH message: cap {}, msg {}", cap.toString(), message.getData().getData());
    int code = message.getData().getCode();
    switch (code) {
      case CrosschainMessageCodes.PING:
        try {
          LOG.info(
              "Received PING from {}, sending PONG", message.getConnection().getRemoteAddress());
          // Thread.sleep(1000);
          message.getConnection().send(CrosschainSubProtocol.CCH, PongMessageData.create(2));
        } catch (Exception e) {
          LOG.error("Exception", e);
        }
        break;
      case CrosschainMessageCodes.PONG:
        LOG.info("Received PONG!");
        break;
      default:
        LOG.error("Received CCH message with unexpected code {}", code);
    }
  }

  @Override
  public void handleNewConnection(final PeerConnection peerConnection) {
    peers.add(peerConnection);
    String ps = peerConnection.getPeerInfo().toString();
    LOG.debug("Added new peer: {}", ps);
    //    try {
    //      //Thread.sleep(3000);
    //      LOG.info("Pinging peer {}", ps);
    //      peerConnection.send(CrosschainSubProtocol.CCH, PingMessageData.create("pingggg"));
    //    } catch (Exception e) {
    //      LOG.error("Exception", e);
    //    }
  }

  @Override
  public void handleDisconnect(
      final PeerConnection peerConnection,
      final DisconnectReason disconnectReason,
      final boolean initiatedByPeer) {
    String ps = peerConnection.getPeerInfo().toString();
    LOG.debug(
        "Disconnected peer {}, reason {}, by peer? {}",
        ps,
        disconnectReason.toString(),
        initiatedByPeer);
    peers.remove(peerConnection);
  }
}
