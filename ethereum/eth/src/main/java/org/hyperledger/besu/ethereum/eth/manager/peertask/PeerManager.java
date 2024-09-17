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
import org.hyperledger.besu.ethereum.p2p.peers.PeerId;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** "Manages" the EthPeers for the PeerTaskExecutor */
public class PeerManager {
  private static final Logger LOG = LoggerFactory.getLogger(PeerManager.class);

  // use a synchronized map to ensure the map is never modified by multiple threads at once
  private final Map<PeerId, EthPeer> ethPeersByPeerId =
      Collections.synchronizedMap(new HashMap<>());

  /**
   * Gets the highest reputation peer matching the supplies filter
   *
   * @param filter a filter to match prospective peers with
   * @return the highest reputation peer matching the supplies filter
   * @throws NoAvailablePeerException If there are no suitable peers
   */
  public EthPeer getPeer(final Predicate<EthPeer> filter) throws NoAvailablePeerException {
    LOG.trace("Getting peer from pool of {} peers", ethPeersByPeerId.size());
    return ethPeersByPeerId.values().stream()
        .filter(filter)
        .max(Comparator.naturalOrder())
        .orElseThrow(NoAvailablePeerException::new);
  }

  public Optional<EthPeer> getPeerByPeerId(final PeerId peerId) {
    return Optional.ofNullable(ethPeersByPeerId.get(peerId));
  }

  public void addPeer(final EthPeer ethPeer) {
    ethPeersByPeerId.put(ethPeer.getConnection().getPeer(), ethPeer);
  }

  public void removePeer(final PeerId peerId) {
    ethPeersByPeerId.remove(peerId);
  }
}
