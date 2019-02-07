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
package tech.pegasys.pantheon.consensus.ibft.support;

import tech.pegasys.pantheon.consensus.ibft.network.ValidatorMulticaster;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;

public class StubValidatorMulticaster implements ValidatorMulticaster {

  private final List<ValidatorPeer> validatorNodes = Lists.newArrayList();

  public StubValidatorMulticaster() {}

  public void addNetworkPeers(final Collection<ValidatorPeer> nodes) {
    validatorNodes.addAll(nodes);
  }

  @Override
  public void send(final MessageData message) {
    validatorNodes.forEach(peer -> peer.handleReceivedMessage(message));
  }

  @Override
  public void send(final MessageData message, final Collection<Address> blackList) {
    validatorNodes.stream()
        .filter(peer -> !blackList.contains(peer.getNodeAddress()))
        .forEach(peer -> peer.handleReceivedMessage(message));
  }
}
