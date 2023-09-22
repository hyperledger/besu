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
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.eth.messages.snap.StorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.TrieNodesMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.Collections;
import java.util.HashMap;
import java.util.NavigableMap;
import java.util.Optional;
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

  private final EthMessages snapMessages;
  private final Function<Hash, Optional<WorldStateStorage>> worldStateStorageProvider;

  SnapServer(final EthMessages snapMessages, final WorldStateArchive archive) {
    this.snapMessages = snapMessages;
    // TODO implement worldstate storage retrieval by root hash in WorldStateArchive:
    this.worldStateStorageProvider = __ -> null;
  }

  SnapServer(
      final EthMessages snapMessages,
      final Function<Hash, Optional<WorldStateStorage>> worldStateStorageProvider) {
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
                      takeWhilePredicate(maxResponseBytes));

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

  private MessageData constructGetStorageRangeResponse(final MessageData message) {
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

    return EMPTY_STORAGE_RANGE;
    //    return worldStateStorageProvider
    //        .apply(range.worldStateRootHash())
    //        .map(
    //            storage -> {
    //              NavigableMap<Bytes32, Bytes> accounts =
    //                  storage.streamFlatAccounts(
    //                      range.startKeyHash(), range.endKeyHash(), MAX_ENTRIES_PER_REQUEST);
    //
    //              // for the first account, honor startHash
    //              Bytes32 startKeyBytes = range.startKeyHash();
    //              Bytes32 endKeyBytes = range.endKeyHash();
    //              NavigableMap<Bytes32, Bytes> collectedStorages = new TreeMap<>();
    //              ArrayList<Bytes> collectedProofs = new ArrayList<>();
    //              for (var forAccountHash : range.hashes()) {
    //                var accountStorages =
    //                    storage.streamFlatStorages(
    //                        Hash.wrap(forAccountHash),
    //                        startKeyBytes,
    //                        endKeyBytes,
    //                        MAX_ENTRIES_PER_REQUEST);
    //                boolean shouldGetMore = false;
    ////                    visitCollectedStorage(collectedStorages, collectedProofs,
    // accountStorages, maxResponseBytes);
    //                // todo proofs for this accountHash
    //
    //                if (shouldGetMore) {
    //                  // reset startkeyBytes for subsequent accounts
    //                  startKeyBytes = Bytes32.ZERO;
    //                } else {
    //                  break;
    //                }
    //              }
    //
    //              return StorageRangeMessage.create(new ArrayDeque<>(), Collections.emptyList());
    //            })
    //        .orElse(EMPTY_STORAGE_RANGE);
  }

  private MessageData constructGetBytecodesResponse(final MessageData message) {
    // TODO implement once code is stored by hash
    return ByteCodesMessage.create(new ArrayDeque<>());
  }

  private MessageData constructGetTrieNodesResponse(final MessageData message) {
    // TODO: what is this expecting?  account state tries or storage tries?
    return TrieNodesMessage.create(new ArrayDeque<>());
  }

  private static Predicate<Pair<Bytes32, Bytes>> takeWhilePredicate(final int maxResponseBytes) {
    final AtomicInteger byteLimit = new AtomicInteger(0);
    final AtomicInteger recordLimit = new AtomicInteger(0);
    return pair ->
        recordLimit.addAndGet(1) < MAX_ENTRIES_PER_REQUEST
            && byteLimit.accumulateAndGet(
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
  }
}
