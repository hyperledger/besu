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
import org.hyperledger.besu.ethereum.bonsai.BonsaiPersistedWorldState;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.ByteCodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetByteCodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetStorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetTrieNodes;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.eth.messages.snap.StorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.TrieNodes;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import kotlin.collections.ArrayDeque;
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
            (location, key) ->
                worldState.getWorldStateStorage().getAccountStateTrieNode(location, key),
            range.worldStateRootHash(),
            Function.identity(),
            Function.identity());

    final SnapStorageEntriesCollector collector =
        SnapStorageEntriesCollector.createCollector(
            range.startKeyHash(), range.endKeyHash(), MAX_ENTRIES_PER_REQUEST, maxResponseBytes);
    final TrieIterator<Bytes> visitor = SnapStorageEntriesCollector.createVisitor(collector);

    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            trie.entriesFrom(
                root ->
                    SnapStorageEntriesCollector.collectEntries(
                        collector, visitor, root, range.startKeyHash()));

    final List<Bytes> proof =
        new ArrayDeque<>(
            worldStateArchive.getAccountProofRelatedNodes(
                range.worldStateRootHash(), Hash.wrap(range.startKeyHash())));
    if (!accounts.isEmpty()) {
      proof.addAll(
          worldStateArchive.getAccountProofRelatedNodes(
              range.worldStateRootHash(), Hash.wrap(accounts.lastKey())));
    }

    return AccountRangeMessage.create(accounts, proof);
  }

  private MessageData constructGetStorageRangeResponse(
      final WorldStateArchive worldStateArchive, final MessageData message) {
    final GetStorageRangeMessage getStorageRangeMessage = GetStorageRangeMessage.readFrom(message);
    final BonsaiPersistedWorldState worldState =
        (BonsaiPersistedWorldState) worldStateArchive.getMutable();
    final GetStorageRangeMessage.StorageRange range = getStorageRangeMessage.range(true);

    final int maxResponseBytes = Math.min(range.responseBytes().intValue(), MAX_RESPONSE_SIZE);

    LOGGER.info("Receive get storage range message from {}", range.startKeyHash().toHexString());

    final SnapStorageEntriesCollector collector =
        SnapStorageEntriesCollector.createCollector(
            range.startKeyHash(), range.endKeyHash(), MAX_ENTRIES_PER_REQUEST, maxResponseBytes);
    final TrieIterator<Bytes> visitor = SnapStorageEntriesCollector.createVisitor(collector);

    final ArrayDeque<TreeMap<Bytes32, Bytes>> slots = new ArrayDeque<>();
    final List<Bytes> proofs = new ArrayDeque<>();

    final Iterator<Hash> iterator = range.accountHashes().iterator();
    while (iterator.hasNext() && visitor.getState().continueIterating()) {
      final Hash currentAccountHash = iterator.next();
      final StoredMerklePatriciaTrie<Bytes, Bytes> trie =
          new StoredMerklePatriciaTrie<>(
              (location, key) ->
                  worldState
                      .getWorldStateStorage()
                      .getAccountStorageTrieNode(currentAccountHash, location, key),
              range.worldStateRootHash(),
              Function.identity(),
              Function.identity());

      slots.add(
          (TreeMap<Bytes32, Bytes>)
              trie.entriesFrom(
                  root ->
                      SnapStorageEntriesCollector.collectEntries(
                          collector, visitor, root, range.startKeyHash())));

      proofs.addAll(
          worldStateArchive.getSlotProofRelatedNodes(
              range.worldStateRootHash(), currentAccountHash, Hash.wrap(range.startKeyHash())));
      if (!slots.last().isEmpty()) {
        proofs.addAll(
            worldStateArchive.getSlotProofRelatedNodes(
                range.worldStateRootHash(), currentAccountHash, Hash.wrap(slots.last().lastKey())));
      }
    }

    return StorageRangeMessage.create(slots, proofs);
  }

  private MessageData constructGetBytecodesResponse(
      final WorldStateArchive worldStateArchive, final MessageData message) {
    final GetByteCodesMessage getByteCodesMessage = GetByteCodesMessage.readFrom(message);
    final BonsaiPersistedWorldState worldState =
        (BonsaiPersistedWorldState) worldStateArchive.getMutable();
    final GetByteCodesMessage.ByteCodesRequest request = getByteCodesMessage.request(true);

    final int maxResponseBytes = Math.min(request.responseBytes().intValue(), MAX_RESPONSE_SIZE);
    final AtomicInteger currentResponseSize = new AtomicInteger();

    LOGGER.info("Receive get bytecodes message for {}", request.hashes());

    final ArrayList<Bytes> foundBytecodes = new ArrayList<>();
    for (Hash hash : request.hashes()) {
      final Optional<Bytes> code = worldState.getWorldStateStorage().getCode(hash, null);
      if (code.isEmpty()) {
        return DisconnectMessage.create(DisconnectReason.UNKNOWN);
      }
      foundBytecodes.add(code.get());

      currentResponseSize.addAndGet(foundBytecodes.size());
      if (currentResponseSize.get() > maxResponseBytes) {
        break;
      }
    }
    return ByteCodesMessage.create(foundBytecodes);
  }

  private MessageData constructGetTrieNodesResponse(
      final WorldStateArchive worldStateArchive, final MessageData message) {
    final GetTrieNodes getTrieNodes = GetTrieNodes.readFrom(message);
    final GetTrieNodes.TrieNodesPaths paths = getTrieNodes.paths(true);
    final BonsaiPersistedWorldState worldState =
        (BonsaiPersistedWorldState) worldStateArchive.getMutable();

    final int maxResponseBytes = Math.min(paths.responseBytes().intValue(), MAX_RESPONSE_SIZE);
    final AtomicInteger currentResponseSize = new AtomicInteger();

    LOGGER.info("Receive get trie nodes range message");

    final ArrayList<Bytes> trieNodes = new ArrayList<>();
    final Iterator<List<Bytes>> pathsIterator = paths.paths().iterator();

    final MerklePatriciaTrie<Bytes, Bytes> accountTrie =
        new StoredMerklePatriciaTrie<>(
            (location, key) ->
                worldState.getWorldStateStorage().getAccountStateTrieNode(location, key),
            paths.worldStateRootHash(),
            Function.identity(),
            Function.identity());

    while (pathsIterator.hasNext()) {
      List<Bytes> pathset = pathsIterator.next();
      if (pathset.size() == 1) {
        accountTrie
            .getNodeWithPath(CompactEncoding.decode(pathset.get(0)))
            .map(Node::getRlp)
            .ifPresent(
                rlp -> {
                  trieNodes.add(rlp);
                  currentResponseSize.addAndGet(rlp.size());
                });
      } else {
        final Hash accountHash = Hash.wrap(Bytes32.wrap(pathset.get(0)));
        for (int i = 1; i < pathset.size(); i++) {
          final MerklePatriciaTrie<Bytes, Bytes> storageTrie =
              new StoredMerklePatriciaTrie<>(
                  (location, key) ->
                      worldState
                          .getWorldStateStorage()
                          .getAccountStorageTrieNode(accountHash, location, key),
                  paths.worldStateRootHash(),
                  Function.identity(),
                  Function.identity());
          storageTrie
              .getNodeWithPath(CompactEncoding.decode(pathset.get(i)))
              .map(Node::getRlp)
              .ifPresent(
                  rlp -> {
                    trieNodes.add(rlp);
                    currentResponseSize.addAndGet(rlp.size());
                  });
        }
      }
      if (currentResponseSize.get() > maxResponseBytes) {
        break;
      }
    }
    return TrieNodes.create(trieNodes);
  }
}
