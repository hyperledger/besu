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
package org.hyperledger.besu.consensus.ibft.support;

import static java.util.function.Function.identity;

import org.hyperledger.besu.consensus.ibft.SynchronizerUpdater;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class StubbedSynchronizerUpdater implements SynchronizerUpdater {

  private Map<PeerConnection, ValidatorPeer> validatorNodes = new HashMap<>();

  @Override
  public void updatePeerChainState(
      final long knownBlockNumber, final PeerConnection peerConnection) {
    validatorNodes.get(peerConnection).updateEstimatedChainHeight(knownBlockNumber);
  }

  public void addNetworkPeers(final Collection<ValidatorPeer> nodes) {
    validatorNodes.putAll(
        nodes.stream().collect(Collectors.toMap(ValidatorPeer::getPeerConnection, identity())));
  }
}
