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

import org.hyperledger.besu.consensus.qbft.core.types.QbftMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

/** Adaptor class to convert {@link Message} to {@link QbftMessage}. */
public class QbftMessageAdaptor implements QbftMessage {

  private final MessageData messageData;

  /**
   * Create a new instance of the adaptor.
   *
   * @param message The {@link Message} to adapt.
   */
  public QbftMessageAdaptor(final Message message) {
    this.messageData = message.getData();
  }

  @Override
  public MessageData getMessageData() {
    return messageData;
  }
}
