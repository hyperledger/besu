/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.p2p.discovery.internal;

import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DiscoveryProtocolLogger {

  private static final Logger LOG = LogManager.getLogger();

  static void logSendingPacket(final Peer peer, final Packet packet) {
    LOG.trace(
        "<<< Sending  {} packet from peer {} ({}): {}",
        shortenPacketType(packet),
        peer.getId().slice(0, 16),
        peer.getEndpoint(),
        packet);
  }

  static void logReceivedPacket(final Peer peer, final Packet packet) {
    LOG.trace(
        ">>> Received {} packet from peer {} ({}): {}",
        shortenPacketType(packet),
        peer.getId().slice(0, 16),
        peer.getEndpoint(),
        packet);
  }

  private static String shortenPacketType(final Packet packet) {
    switch (packet.getType()) {
      case PING:
        return "PING ";
      case PONG:
        return "PONG ";
      case FIND_NEIGHBORS:
        return "FINDN";
      case NEIGHBORS:
        return "NEIGH";
    }
    return null;
  }
}
