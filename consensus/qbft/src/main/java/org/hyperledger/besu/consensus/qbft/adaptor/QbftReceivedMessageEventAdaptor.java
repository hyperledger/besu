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
package org.hyperledger.besu.consensus.qbft.adaptor;

import org.hyperledger.besu.consensus.common.bft.events.BftEvents;
import org.hyperledger.besu.consensus.common.bft.events.BftReceivedMessageEvent;
import org.hyperledger.besu.consensus.qbft.core.types.QbftReceivedMessageEvent;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

/** Adaptor class to convert {@link BftReceivedMessageEvent} to {@link QbftReceivedMessageEvent}. */
public class QbftReceivedMessageEventAdaptor implements QbftReceivedMessageEvent {

  private final MessageData messageData;
  private final Address sender;

  /**
   * Create a new instance of the adaptor.
   *
   * @param bftReceivedMessageEvent The {@link BftReceivedMessageEvent} to adapt.
   */
  public QbftReceivedMessageEventAdaptor(final BftReceivedMessageEvent bftReceivedMessageEvent) {
    this.messageData = bftReceivedMessageEvent.getMessage().getData();
    this.sender = bftReceivedMessageEvent.getMessage().getConnection().getPeerInfo().getAddress();
  }

  @Override
  public MessageData getMessage() {
    return messageData;
  }

  @Override
  public Address getSender() {
    return sender;
  }

  @Override
  public BftEvents.Type getType() {
    return BftEvents.Type.MESSAGE;
  }
}
