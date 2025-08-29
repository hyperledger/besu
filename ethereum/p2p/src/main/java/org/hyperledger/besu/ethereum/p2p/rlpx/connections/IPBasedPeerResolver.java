/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.p2p.rlpx.connections;

import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.Endpoint;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerTable;
import org.hyperledger.besu.ethereum.p2p.peers.MaintainedPeers;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

public class IPBasedPeerResolver {

  private final PeerTable peerTable;
  private final MaintainedPeers maintainedPeers;

  public IPBasedPeerResolver(final PeerTable peerTable, final MaintainedPeers maintainedPeers) {
    this.peerTable = peerTable;
    this.maintainedPeers = maintainedPeers;
  }

  public Optional<DiscoveryPeer> findPeerByEndpoint(final InetSocketAddress address) {
    Optional<DiscoveryPeer> maintainedPeer = findMaintainedPeerByEndpoint(address);
    if (maintainedPeer.isPresent()) {
      return maintainedPeer;
    }

    return findDiscoveredPeerByEndpoint(address); // checking discovery table
  }

  public boolean isPrivilegedIP(final InetSocketAddress address) {
    return findMaintainedPeerByEndpoint(address).isPresent();
  }

  private Optional<DiscoveryPeer> findMaintainedPeerByEndpoint(final InetSocketAddress address) {
    return maintainedPeers
        .streamPeers()
        .filter(peer -> matchesEndpoint(peer, address))
        .map(this::getDiscoveryPeer)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  private Optional<DiscoveryPeer> findDiscoveredPeerByEndpoint(final InetSocketAddress address) {
    return peerTable
        .streamAllPeers()
        .filter(peer -> matchesDiscoveryEndpoint(peer, address))
        .findFirst();
  }

  private boolean matchesEndpoint(final Peer peer, final InetSocketAddress address) {
    return Optional.ofNullable(peer)
        .map(Peer::getEnodeURL)
        .map(EnodeURL::getIp)
        .flatMap(
            enodeIp ->
                Optional.ofNullable(address)
                    .map(InetSocketAddress::getAddress)
                    .map(enodeIp::equals))
        .orElse(false);
  }

  private boolean matchesDiscoveryEndpoint(
      final DiscoveryPeer peer, final InetSocketAddress address) {
    return Optional.ofNullable(peer)
        .map(DiscoveryPeer::getEndpoint)
        .map(Endpoint::getHost)
        .flatMap(
            host ->
                Optional.ofNullable(address)
                    .map(InetSocketAddress::getAddress)
                    .map(InetAddress::getHostAddress)
                    .map(host::equals))
        .orElse(false);
  }

  private Optional<DiscoveryPeer> getDiscoveryPeer(final Peer peer) {
    return peerTable.get(peer);
  }
}
