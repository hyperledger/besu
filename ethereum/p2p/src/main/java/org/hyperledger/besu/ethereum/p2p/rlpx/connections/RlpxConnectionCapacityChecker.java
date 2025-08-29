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

import org.hyperledger.besu.ethereum.p2p.peers.PeerPrivileges;
import org.hyperledger.besu.ethereum.p2p.rlpx.RlpxAgent;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class RlpxConnectionCapacityChecker implements ConnectionCapacityChecker {

  private final RlpxAgent rlpxAgent;
  private final PeerPrivileges peerPrivileges;

  public RlpxConnectionCapacityChecker(
      final RlpxAgent rlpxAgent, final PeerPrivileges peerPrivileges) {
    this.rlpxAgent = rlpxAgent;
    this.peerPrivileges = peerPrivileges;
  }

  @Override
  public boolean hasCapacityforNewConnection() {
    return rlpxAgent.getConnectionCount() < rlpxAgent.getMaxPeers();
  }

  @Override
  public boolean canExceedLimits(Optional<Bytes> peerId) {
    return peerId.map(peerPrivileges::canExceedConnectionLimits).orElse(false);
  }
}
