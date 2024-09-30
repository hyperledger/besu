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
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

import java.util.Collection;
import java.util.Optional;

/** Selects the EthPeers for the PeerTaskExecutor */
public interface PeerSelector {

  /**
   * Gets a peer with the requiredPeerHeight (if not PoS), and with the requiredSubProtocol, and
   * which is not in the supplied collection of usedEthPeers
   *
   * @param usedEthPeers a collection of EthPeers to be excluded from selection because they have
   *     already been used
   * @param requiredPeerHeight the minimum peer height required of the selected peer
   * @param requiredSubProtocol the SubProtocol required of the peer
   * @return a peer matching the supplied conditions
   * @throws NoAvailablePeerException If there are no suitable peers
   */
  EthPeer getPeer(
      Collection<EthPeer> usedEthPeers, long requiredPeerHeight, SubProtocol requiredSubProtocol)
      throws NoAvailablePeerException;

  /**
   * Attempts to get the EthPeer identified by peerId
   *
   * @param peerId the peerId of the desired EthPeer
   * @return An Optional\<EthPeer\> containing the EthPeer identified by peerId if present in the
   *     PeerSelector, or empty otherwise
   */
  Optional<EthPeer> getPeerByPeerId(PeerId peerId);

  /**
   * Add the supplied EthPeer to the PeerSelector
   *
   * @param ethPeer the EthPeer to be added to the PeerSelector
   */
  void addPeer(EthPeer ethPeer);

  /**
   * Remove the EthPeer identified by peerId from the PeerSelector
   *
   * @param peerId the PeerId of the EthPeer to be removed from the PeerSelector
   */
  void removePeer(PeerId peerId);
}