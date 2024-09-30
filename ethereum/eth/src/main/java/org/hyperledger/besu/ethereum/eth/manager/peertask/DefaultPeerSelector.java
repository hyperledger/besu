/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.manager.peertask;

import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.p2p.peers.PeerId;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a simple PeerSelector implementation that can be used the default implementation in most
 * situations
 */
public class DefaultPeerSelector implements PeerSelector {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultPeerSelector.class);

  private final Supplier<ProtocolSpec> protocolSpecSupplier;
  private final Map<PeerId, EthPeer> ethPeersByPeerId = new ConcurrentHashMap<>();

  public DefaultPeerSelector(final Supplier<ProtocolSpec> protocolSpecSupplier) {
    this.protocolSpecSupplier = protocolSpecSupplier;
  }

  /**
   * Gets the highest reputation peer matching the supplied filter
   *
   * @param filter a filter to match prospective peers with
   * @return the highest reputation peer matching the supplies filter
   * @throws NoAvailablePeerException If there are no suitable peers
   */
  private EthPeer getPeer(final Predicate<EthPeer> filter) throws NoAvailablePeerException {
    LOG.trace("Finding peer from pool of {} peers", ethPeersByPeerId.size());
    return ethPeersByPeerId.values().stream()
        .filter(filter)
        .max(Comparator.naturalOrder())
        .orElseThrow(NoAvailablePeerException::new);
  }

  @Override
  public EthPeer getPeer(
      final Collection<EthPeer> usedEthPeers,
      final long requiredPeerHeight,
      final SubProtocol requiredSubProtocol)
      throws NoAvailablePeerException {
    return getPeer(
        (candidatePeer) ->
            isPeerUnused(candidatePeer, usedEthPeers)
                && (protocolSpecSupplier.get().isPoS()
                    || isPeerHeightHighEnough(candidatePeer, requiredPeerHeight))
                && isPeerProtocolSuitable(candidatePeer, requiredSubProtocol));
  }

  @Override
  public Optional<EthPeer> getPeerByPeerId(final PeerId peerId) {
    return Optional.ofNullable(ethPeersByPeerId.get(peerId));
  }

  @Override
  public void addPeer(final EthPeer ethPeer) {
    ethPeersByPeerId.put(ethPeer.getConnection().getPeer(), ethPeer);
  }

  @Override
  public void removePeer(final PeerId peerId) {
    ethPeersByPeerId.remove(peerId);
  }

  private boolean isPeerUnused(final EthPeer ethPeer, final Collection<EthPeer> usedEthPeers) {
    return !usedEthPeers.contains(ethPeer);
  }

  private boolean isPeerHeightHighEnough(final EthPeer ethPeer, final long requiredHeight) {
    return ethPeer.chainState().getEstimatedHeight() >= requiredHeight;
  }

  private boolean isPeerProtocolSuitable(final EthPeer ethPeer, final SubProtocol protocol) {
    return ethPeer.getProtocolName().equals(protocol.getName());
  }
}
