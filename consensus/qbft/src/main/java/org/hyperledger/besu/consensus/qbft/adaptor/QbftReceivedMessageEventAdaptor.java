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
import org.hyperledger.besu.consensus.qbft.core.types.QbftMessage;
import org.hyperledger.besu.consensus.qbft.core.types.QbftReceivedMessageEvent;

/** Adaptor class to convert {@link BftReceivedMessageEvent} to {@link QbftReceivedMessageEvent}. */
public class QbftReceivedMessageEventAdaptor implements QbftReceivedMessageEvent {

  private final QbftMessage qbftMessage;

  /**
   * Create a new instance of the adaptor.
   *
   * @param bftReceivedMessageEvent The {@link BftReceivedMessageEvent} to adapt.
   */
  public QbftReceivedMessageEventAdaptor(final BftReceivedMessageEvent bftReceivedMessageEvent) {
    this.qbftMessage = new QbftMessageAdaptor(bftReceivedMessageEvent.getMessage());
  }

  @Override
  public QbftMessage getMessage() {
    return qbftMessage;
  }

  @Override
  public BftEvents.Type getType() {
    return BftEvents.Type.MESSAGE;
  }
}
