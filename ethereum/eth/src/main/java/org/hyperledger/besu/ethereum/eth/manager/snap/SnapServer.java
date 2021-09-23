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
package org.hyperledger.besu.ethereum.eth.manager.snap;

import org.hyperledger.besu.ethereum.bonsai.BonsaiPersistedWorldState;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.messages.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.SnapV1;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

class SnapServer {

  private static final Logger LOGGER = LogManager.getLogger();

  private static final int MAX_ENTRIES_PER_REQUEST = 100000;

  private static final int MAX_RESPONSE_SIZE = 2 * 1024 * 1024;

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
    final GetAccountRangeMessage.Range range = getAccountRangeMessage.range(true);

    final int maxResponseBytes = Math.min(range.responseBytes().intValue(), MAX_RESPONSE_SIZE);

    LOGGER.info(
        "Receive get account range message from {} to {}",
        range.startKeyHash().toHexString(),
        range.endKeyHash().toHexString());
    final StoredMerklePatriciaTrie<Bytes, Bytes> trie =
        new StoredMerklePatriciaTrie<>(
            (location, key) -> {
              System.out.println("ici");
              return worldState.getWorldStateStorage().getAccountStateTrieNode(location, key);
            },
            range.worldStateRootHash(),
            Function.identity(),
            Function.identity());
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            trie.entriesFrom(
                root ->
                    SnapStorageEntriesCollector.collectEntries(
                        root,
                        range.startKeyHash(),
                        range.endKeyHash(),
                        MAX_ENTRIES_PER_REQUEST,
                        maxResponseBytes));
    final List<Bytes> proof =
        new ArrayList<>(
            worldStateArchive.getAccountProofRelatedNodes(
                range.worldStateRootHash(), Hash.wrap(range.startKeyHash())));
    if (!accounts.isEmpty()) {
      proof.addAll(
          worldStateArchive.getAccountProofRelatedNodes(
              range.worldStateRootHash(), Hash.wrap(accounts.lastKey())));
    }
    return AccountRangeMessage.create(accounts, proof);
  }
}
