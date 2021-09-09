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
package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.ethereum.eth.messages.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.SnapV1;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

class SnapServer {
  private final ProtocolMessages protocolMessages;

  SnapServer(final ProtocolMessages protocolMessages) {
    this.protocolMessages = protocolMessages;
    this.registerResponseConstructors();
  }

  private void registerResponseConstructors() {
    protocolMessages.registerResponseConstructor(
        SnapV1.GET_ACCOUNT_RANGE, SnapServer::constructGetAccountRangeResponse);
  }

  static MessageData constructGetAccountRangeResponse(final MessageData message) {
    final GetAccountRangeMessage getAccountRangeMessage = GetAccountRangeMessage.readFrom(message);
    System.out.println(getAccountRangeMessage.range().worldStateRootHash());
    return getAccountRangeMessage;
  }
}
