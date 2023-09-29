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
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.ByteCodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetStorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetTrieNodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.eth.messages.snap.StorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.TrieNodesMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.trie.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.bonsai.cache.CachedBonsaiWorldView;
import org.hyperledger.besu.ethereum.worldstate.FlatWorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import kotlin.Pair;
import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** See https://github.com/ethereum/devp2p/blob/master/caps/snap.md */
@SuppressWarnings("unused")
class SnapServer {
  private static final Logger LOGGER = LoggerFactory.getLogger(SnapServer.class);
  private static final int MAX_ENTRIES_PER_REQUEST = 100000;
  private static final int MAX_RESPONSE_SIZE = 2 * 1024 * 1024;
  private static final AccountRangeMessage EMPTY_ACCOUNT_RANGE =
      AccountRangeMessage.create(new HashMap<>(), new ArrayDeque<>());
  private static final StorageRangeMessage EMPTY_STORAGE_RANGE =
      StorageRangeMessage.create(new ArrayDeque<>(), Collections.emptyList());
  private static final TrieNodesMessage EMPTY_TRIE_NODES_MESSAGE =
      TrieNodesMessage.create(new ArrayList<>());

  private final EthMessages snapMessages;
  private final Function<Hash, Optional<FlatWorldStateStorage>> worldStateStorageProvider;

  SnapServer(final EthMessages snapMessages, final WorldStateArchive archive) {
    this.snapMessages = snapMessages;
    // TODO remove dirty bonsai cast:
    this.worldStateStorageProvider =
        rootHash ->
            ((BonsaiWorldStateProvider) archive)
                .getCachedWorldStorageManager()
                .getStorageByRootHash(rootHash)
                .map(CachedBonsaiWorldView::getWorldStateStorage);
  }

  SnapServer(
      final EthMessages snapMessages,
      final Function<Hash, Optional<FlatWorldStateStorage>> worldStateStorageProvider) {
    this.snapMessages = snapMessages;
    this.worldStateStorageProvider = worldStateStorageProvider;
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

  MessageData constructGetAccountRangeResponse(final MessageData message) {
    final GetAccountRangeMessage getAccountRangeMessage = GetAccountRangeMessage.readFrom(message);
    final GetAccountRangeMessage.Range range = getAccountRangeMessage.range(true);
    final int maxResponseBytes = Math.min(range.responseBytes().intValue(), MAX_RESPONSE_SIZE);

    // TODO: drop to TRACE
    LOGGER.info(
        "Receive get account range message from {} to {}",
        range.startKeyHash().toHexString(),
        range.endKeyHash().toHexString());

    var worldStateHash = getAccountRangeMessage.range(true).worldStateRootHash();

    return worldStateStorageProvider
        .apply(worldStateHash)
        .map(
            storage -> {
              NavigableMap<Bytes32, Bytes> accounts =
                  storage.streamFlatAccounts(
                      range.startKeyHash(),
                      range.endKeyHash(),
                      new StatefulPredicate(maxResponseBytes));

              if (accounts.isEmpty()) {
                // fetch next account after range, if it exists
                accounts = storage.streamFlatAccounts(range.endKeyHash(), UInt256.MAX_VALUE, 1L);
              }

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
            })
        .orElse(EMPTY_ACCOUNT_RANGE);
  }

  MessageData constructGetStorageRangeResponse(final MessageData message) {
    final GetStorageRangeMessage getStorageRangeMessage = GetStorageRangeMessage.readFrom(message);
    final GetStorageRangeMessage.StorageRange range = getStorageRangeMessage.range(true);
    final int maxResponseBytes = Math.min(range.responseBytes().intValue(), MAX_RESPONSE_SIZE);

    // TODO: drop to TRACE
    LOGGER
        .atInfo()
        .setMessage("Receive get storage range message from {} to {} for {}")
        .addArgument(() -> range.startKeyHash().toHexString())
        .addArgument(() -> range.endKeyHash())
        .addArgument(
            () ->
                range.hashes().stream()
                    .map(Bytes32::toHexString)
                    .collect(Collectors.joining(",", "[", "]")))
        .log();

    return worldStateStorageProvider
        .apply(range.worldStateRootHash())
        .map(
            storage -> {
              // reusable predicate to limit by rec count and bytes:
              var statefulPredicate = new StatefulPredicate(maxResponseBytes);
              // for the first account, honor startHash
              Bytes32 startKeyBytes = range.startKeyHash();
              Bytes32 endKeyBytes = range.endKeyHash();

              ArrayDeque<NavigableMap<Bytes32, Bytes>> collectedStorages = new ArrayDeque<>();
              ArrayList<Bytes> collectedProofs = new ArrayList<>();
              final var worldStateProof = new WorldStateProofProvider(storage);

              for (var forAccountHash : range.hashes()) {
                // start key proof
                collectedProofs.addAll(
                    worldStateProof.getStorageProofRelatedNodes(
                        getAccountStorageRoot(forAccountHash, storage),
                        forAccountHash,
                        Hash.wrap(startKeyBytes)));

                var accountStorages =
                    storage.streamFlatStorages(
                        Hash.wrap(forAccountHash), startKeyBytes, endKeyBytes, statefulPredicate);
                collectedStorages.add(accountStorages);

                // last key proof
                collectedProofs.addAll(
                    worldStateProof.getStorageProofRelatedNodes(
                        getAccountStorageRoot(forAccountHash, storage),
                        forAccountHash,
                        Hash.wrap(accountStorages.lastKey())));

                if (statefulPredicate.shouldGetMore()) {
                  // reset startkeyBytes for subsequent accounts
                  startKeyBytes = Bytes32.ZERO;
                } else {
                  break;
                }
              }

              return StorageRangeMessage.create(collectedStorages, collectedProofs);
            })
        .orElse(EMPTY_STORAGE_RANGE);
  }

  private MessageData constructGetBytecodesResponse(final MessageData message) {
    // TODO implement once code is stored by hash
    return ByteCodesMessage.create(new ArrayDeque<>());
  }

  MessageData constructGetTrieNodesResponse(final MessageData message) {
    final GetTrieNodesMessage getTrieNodesMessage = GetTrieNodesMessage.readFrom(message);
    final GetTrieNodesMessage.TrieNodesPaths triePaths = getTrieNodesMessage.paths(true);
    // TODO: drop to TRACE
    LOGGER
        .atInfo()
        .setMessage("Receive get trie nodes message of size {}")
        .addArgument(() -> triePaths.paths().size())
        .log();

    //TODO: implement limits
    return worldStateStorageProvider
        .apply(triePaths.worldStateRootHash())
        .map(
            storage -> {
              ArrayList<Bytes> trieNodes = new ArrayList<>();
              for (var triePath : triePaths.paths()) {
                // first element is paths is account
                if (triePath.size() == 1) {
                  // if there is only one path, presume it should be compact encoded account path
                  storage.getTrieNodeUnsafe(triePath.get(0)).ifPresent(trieNodes::add);
                } else {
                  // otherwise the first element should be account hash, and subsequent paths
                  // are compact encoded account storage paths

                  final Bytes accountPrefix = triePath.get(0);

                  triePath.subList(1, triePath.size()).stream()
                      .forEach(
                          path ->
                              storage
                                  .getTrieNodeUnsafe(Bytes.concatenate(accountPrefix, path))
                                  .ifPresent(trieNodes::add));
                }
              }
              return TrieNodesMessage.create(trieNodes);
            })
        .orElse(EMPTY_TRIE_NODES_MESSAGE);
  }

  static class StatefulPredicate implements Predicate<Pair<Bytes32, Bytes>> {

    final AtomicInteger byteLimit = new AtomicInteger(0);
    final AtomicInteger recordLimit = new AtomicInteger(0);
    final AtomicBoolean shouldContinue = new AtomicBoolean(true);
    final int maxResponseBytes;

    StatefulPredicate(final int maxResponseBytes) {
      this.maxResponseBytes = maxResponseBytes;
    }

    public boolean shouldGetMore() {
      return shouldContinue.get();
    }

    @Override
    public boolean test(final Pair<Bytes32, Bytes> pair) {
      var underRecordLimit = recordLimit.addAndGet(1) < MAX_ENTRIES_PER_REQUEST;
      var underByteLimit =
          byteLimit.accumulateAndGet(
                  0,
                  (cur, __) -> {
                    var rlpOutput = new BytesValueRLPOutput();
                    rlpOutput.startList();
                    rlpOutput.writeBytes(pair.getFirst());
                    rlpOutput.writeRLPBytes(pair.getSecond());
                    rlpOutput.endList();
                    return cur + rlpOutput.encoded().size();
                  })
              < maxResponseBytes;
      if (underRecordLimit && underByteLimit) {
        return true;
      } else {
        shouldContinue.set(false);
        return false;
      }
    }
  }

  Hash getAccountStorageRoot(final Bytes32 accountHash, final WorldStateStorage storage) {
    return storage
        .getTrieNodeUnsafe(Bytes.concatenate(accountHash, Bytes.EMPTY))
        .map(Hash::hash)
        .orElse(Hash.EMPTY_TRIE_HASH);
  }
}
