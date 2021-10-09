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
package org.hyperledger.besu.consensus.common.bft;

import org.hyperledger.besu.consensus.common.bft.network.ValidatorMulticaster;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.Collection;
import java.util.Collections;

import com.google.common.annotations.VisibleForTesting;

public class UniqueMessageMulticaster implements ValidatorMulticaster {
  private final ValidatorMulticaster multicaster;
  private final MessageTracker gossipedMessageTracker;

  /**
   * Constructor that attaches gossip logic to a set of multicaster
   *
   * @param multicaster Network connections to the remote validators
   * @param gossipHistoryLimit Maximum messages to track as seen
   */
  public UniqueMessageMulticaster(
      final ValidatorMulticaster multicaster, final int gossipHistoryLimit) {
    this.multicaster = multicaster;
    this.gossipedMessageTracker = new MessageTracker(gossipHistoryLimit);
  }

  @VisibleForTesting
  public UniqueMessageMulticaster(
      final ValidatorMulticaster multicaster, final MessageTracker gossipedMessageTracker) {
    this.multicaster = multicaster;
    this.gossipedMessageTracker = gossipedMessageTracker;
  }

  @Override
  public void send(final MessageData message) {
    send(message, Collections.emptyList());
  }

  @Override
  public void send(final MessageData message, final Collection<Address> denylist) {
    if (gossipedMessageTracker.hasSeenMessage(message)) {
      return;
    }
    multicaster.send(message, denylist);
    gossipedMessageTracker.addSeenMessage(message);
  }
}
