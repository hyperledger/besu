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
package tech.pegasys.pantheon.consensus.ibft;

import static java.util.Collections.newSetFromMap;

import tech.pegasys.pantheon.consensus.ibft.network.ValidatorMulticaster;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class UniqueMessageMulticaster implements ValidatorMulticaster {
  private final ValidatorMulticaster multicaster;
  private final Set<Hash> seenMessages;

  /**
   * Constructor that attaches gossip logic to a set of multicaster
   *
   * @param multicaster Network connections to the remote validators
   * @param gossipHistoryLimit Maximum messages to track as seen
   */
  public UniqueMessageMulticaster(
      final ValidatorMulticaster multicaster, final int gossipHistoryLimit) {
    this.multicaster = multicaster;
    // Set that starts evicting members when it hits capacity
    this.seenMessages = newSetFromMap(new SizeLimitedMap<>(gossipHistoryLimit));
  }

  @Override
  public void send(final MessageData message) {
    send(message, Collections.emptyList());
  }

  @Override
  public void send(final MessageData message, final Collection<Address> blackList) {
    final Hash uniqueID = Hash.hash(message.getData());
    if (seenMessages.contains(uniqueID)) {
      return;
    }
    multicaster.send(message, blackList);
    seenMessages.add(uniqueID);
  }
}
