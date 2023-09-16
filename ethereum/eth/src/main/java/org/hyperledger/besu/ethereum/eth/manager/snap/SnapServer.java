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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.storage.flat.FlatDbReaderStrategy;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.ByteCodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.eth.messages.snap.StorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.TrieNodesMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.HashMap;
import java.util.function.Function;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** TODO: See https://github.com/ethereum/devp2p/blob/master/caps/snap.md */
@SuppressWarnings("unused")
class SnapServer {
  private static final Logger LOGGER = LoggerFactory.getLogger(SnapServer.class);
  private static final int MAX_ENTRIES_PER_REQUEST = 100000;

  private static final int MAX_RESPONSE_SIZE = 2 * 1024 * 1024;
  private final EthMessages snapMessages;
  private final FlatDbReaderStrategy flatDbStrategy;
  private final Function<Hash, WorldStateStorage> worldStateStorageProvider;

  SnapServer(final EthMessages snapMessages, final WorldStateArchive archive) {
    this.snapMessages = snapMessages;
    // TODO get these from worldstate archive:
    this.flatDbStrategy = null;
    this.worldStateStorageProvider = __ -> null;
  }

  private void registerResponseConstructors() {
    snapMessages.registerResponseConstructor(
        SnapV1.GET_ACCOUNT_RANGE, messageData -> constructGetAccountRangeResponse(messageData));
    snapMessages.registerResponseConstructor(
        SnapV1.GET_STORAGE_RANGE, messageData -> constructGetStorageRangeResponse(messageData));
    snapMessages.registerResponseConstructor(
        SnapV1.GET_BYTECODES, messageData -> constructGetBytecodesResponse(messageData));
    snapMessages.registerResponseConstructor(
        SnapV1.GET_TRIE_NODES, messageData -> constructGetTrieNodesResponse(messageData));
  }

  private MessageData constructGetAccountRangeResponse(final MessageData message) {
    if (flatDbStrategy == null) {
      // TODO, remove this empty fallback
      return AccountRangeMessage.create(new HashMap<>(), new ArrayDeque<>());
    }

    final GetAccountRangeMessage getAccountRangeMessage = GetAccountRangeMessage.readFrom(message);
    final GetAccountRangeMessage.Range range = getAccountRangeMessage.range(true);

    final int maxResponseBytes = Math.min(range.responseBytes().intValue(), MAX_RESPONSE_SIZE);

    LOGGER.info(
        "Receive get account range message from {} to {}",
        range.startKeyHash().toHexString(),
        range.endKeyHash().toHexString());

    final var storage =
        worldStateStorageProvider.apply(getAccountRangeMessage.getRootHash().orElseThrow());

    final var accounts =
        storage.streamFlatAccounts(
            range.startKeyHash(), range.endKeyHash(), MAX_ENTRIES_PER_REQUEST);
    // TODO if accounts is empty we need to return the first hash after the requested endHash

    final var worldStateProof = new WorldStateProofProvider(storage);
    final ArrayDeque<Bytes> proof =
        new ArrayDeque<>(
            worldStateProof.getAccountProofRelatedNodes(
                range.worldStateRootHash(), Hash.wrap(range.startKeyHash())));
    if (!accounts.isEmpty()) {
      proof.addAll(
          worldStateProof.getAccountProofRelatedNodes(
              range.worldStateRootHash(), Hash.wrap(accounts.lastKey())));
    }

    return AccountRangeMessage.create(accounts, proof);
  }

  private MessageData constructGetStorageRangeResponse(final MessageData message) {
    // TODO implement
    return StorageRangeMessage.create(new ArrayDeque<>(), new ArrayDeque<>());
  }

  private MessageData constructGetBytecodesResponse(final MessageData message) {
    // TODO implement once code is stored by hash
    return ByteCodesMessage.create(new ArrayDeque<>());
  }

  private MessageData constructGetTrieNodesResponse(final MessageData message) {
    // TODO: what is this expecting?  account state tries or storage tries?
    return TrieNodesMessage.create(new ArrayDeque<>());
  }
}
