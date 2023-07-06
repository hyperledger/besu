/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.manager.snap;

import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.ByteCodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.eth.messages.snap.StorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.TrieNodesMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.HashMap;

import kotlin.collections.ArrayDeque;

@SuppressWarnings("unused")
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
    snapMessages.registerResponseConstructor(
        SnapV1.GET_STORAGE_RANGE,
        messageData -> constructGetStorageRangeResponse(worldStateArchive, messageData));
    snapMessages.registerResponseConstructor(
        SnapV1.GET_BYTECODES,
        messageData -> constructGetBytecodesResponse(worldStateArchive, messageData));
    snapMessages.registerResponseConstructor(
        SnapV1.GET_TRIE_NODES,
        messageData -> constructGetTrieNodesResponse(worldStateArchive, messageData));
  }

  private MessageData constructGetAccountRangeResponse(
      final WorldStateArchive worldStateArchive, final MessageData message) {
    // TODO implement
    return AccountRangeMessage.create(new HashMap<>(), new ArrayDeque<>());
  }

  private MessageData constructGetStorageRangeResponse(
      final WorldStateArchive worldStateArchive, final MessageData message) {
    // TODO implement
    return StorageRangeMessage.create(new ArrayDeque<>(), new ArrayDeque<>());
  }

  private MessageData constructGetBytecodesResponse(
      final WorldStateArchive worldStateArchive, final MessageData message) {
    // TODO implement
    return ByteCodesMessage.create(new ArrayDeque<>());
  }

  private MessageData constructGetTrieNodesResponse(
      final WorldStateArchive worldStateArchive, final MessageData message) {
    return TrieNodesMessage.create(new ArrayDeque<>());
  }
}
