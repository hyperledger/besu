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

import org.hyperledger.besu.ethereum.bonsai.BonsaiPersistedWorldState;
import org.hyperledger.besu.ethereum.eth.messages.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.SnapV1;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Iterator;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

class SnapServer {
  private final EthMessages snapMessages;
  private final WorldStateArchive worldStateArchive;

  SnapServer(final EthMessages snapMessages, final WorldStateArchive worldStateArchive) {
    this.snapMessages = snapMessages;
    this.worldStateArchive = worldStateArchive;
    this.registerResponseConstructors();
  }

  private void registerResponseConstructors() {
    snapMessages.registerResponseConstructor(
        SnapV1.GET_ACCOUNT_RANGE,
        messageData -> constructGetAccountRangeResponse(worldStateArchive, messageData));
  }

  static MessageData constructGetAccountRangeResponse(
      final WorldStateArchive worldStateArchive, final MessageData message) {
    @SuppressWarnings("unused")
    final GetAccountRangeMessage getAccountRangeMessage = GetAccountRangeMessage.readFrom(message);
    final BonsaiPersistedWorldState worldState =
        (BonsaiPersistedWorldState) worldStateArchive.getMutable();
    final GetAccountRangeMessage.Range range = getAccountRangeMessage.range();
    Map<Bytes32, Bytes> bytes32BytesMap =
        worldState.streamAccounts(
            range.worldStateRootHash(), range.startKeyHash(), range.endKeyHash());
    Iterator<Map.Entry<Bytes32, Bytes>> iterator = bytes32BytesMap.entrySet().iterator();
    while (iterator.hasNext()) {
      System.out.println(
          "for " + range.worldStateRootHash().toHexString() + " Entry " + iterator.next().getKey());
    }
    // TODO RETRIEVE ACCOUNT RANGE
    return AccountRangeMessage.create();
  }
}
