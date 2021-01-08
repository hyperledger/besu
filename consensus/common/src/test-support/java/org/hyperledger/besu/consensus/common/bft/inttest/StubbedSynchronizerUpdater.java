/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.consensus.common.bft.inttest;

import static java.util.function.Function.identity;

import org.hyperledger.besu.consensus.common.bft.SynchronizerUpdater;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class StubbedSynchronizerUpdater implements SynchronizerUpdater {

  private final Map<PeerConnection, DefaultValidatorPeer> validatorNodes = new HashMap<>();

  @Override
  public void updatePeerChainState(
      final long knownBlockNumber, final PeerConnection peerConnection) {
    validatorNodes.get(peerConnection).updateEstimatedChainHeight(knownBlockNumber);
  }

  public void addNetworkPeers(final Collection<DefaultValidatorPeer> nodes) {
    validatorNodes.putAll(
        nodes.stream()
            .collect(Collectors.toMap(DefaultValidatorPeer::getPeerConnection, identity())));
  }
}
