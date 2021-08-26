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

import org.hyperledger.besu.consensus.common.bft.network.ValidatorMulticaster;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;

public class StubValidatorMulticaster implements ValidatorMulticaster {

  private final List<DefaultValidatorPeer> validatorNodes = Lists.newArrayList();

  public StubValidatorMulticaster() {}

  public void addNetworkPeers(final Collection<DefaultValidatorPeer> nodes) {
    validatorNodes.addAll(nodes);
  }

  @Override
  public void send(final MessageData message) {
    validatorNodes.forEach(peer -> peer.handleReceivedMessage(message));
  }

  @Override
  public void send(final MessageData message, final Collection<Address> denylist) {
    validatorNodes.stream()
        .filter(peer -> !denylist.contains(peer.getNodeAddress()))
        .forEach(peer -> peer.handleReceivedMessage(message));
  }
}
