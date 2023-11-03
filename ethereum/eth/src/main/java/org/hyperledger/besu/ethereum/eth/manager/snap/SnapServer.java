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
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.ByteCodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetByteCodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetStorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetTrieNodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.eth.messages.snap.StorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.TrieNodesMessage;
import org.hyperledger.besu.ethereum.eth.sync.DefaultSynchronizer;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.trie.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.bonsai.cache.CachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.worldstate.FlatWorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.BesuEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
class SnapServer implements BesuEvents.InitialSyncCompletionListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(SnapServer.class);
  private static final int PRIME_STATE_ROOT_CACHE_LIMIT = 128;
  private static final int MAX_ENTRIES_PER_REQUEST = 100000;
  private static final int MAX_RESPONSE_SIZE = 2 * 1024 * 1024;
  private static final AccountRangeMessage EMPTY_ACCOUNT_RANGE =
      AccountRangeMessage.create(new HashMap<>(), new ArrayDeque<>());
  private static final StorageRangeMessage EMPTY_STORAGE_RANGE =
      StorageRangeMessage.create(new ArrayDeque<>(), Collections.emptyList());
  private static final TrieNodesMessage EMPTY_TRIE_NODES_MESSAGE =
      TrieNodesMessage.create(new ArrayList<>());
  private static final ByteCodesMessage EMPTY_BYTE_CODES_MESSAGE =
      ByteCodesMessage.create(new ArrayDeque<>());

  static final Hash HASH_LAST = Hash.wrap(Bytes32.leftPad(Bytes.fromHexString("FF"), (byte) 0xFF));

  private final AtomicBoolean isStarted = new AtomicBoolean(false);
  private final AtomicLong listenerId = new AtomicLong();
  private final EthMessages snapMessages;
  private final Function<Optional<Hash>, Optional<BonsaiWorldStateKeyValueStorage>>
      worldStateStorageProvider;

  SnapServer(final EthMessages snapMessages, final ProtocolContext protocolContext) {
    this(
        snapMessages,
        rootHash ->
            ((BonsaiWorldStateProvider) protocolContext.getWorldStateArchive())
                .getCachedWorldStorageManager()
                .flatMap(storageManager -> storageManager.getStorageByRootHash(rootHash)));

    var archive = protocolContext.getWorldStateArchive();
    if (archive.isFlatArchive()) {
      var cachedStorageManager = ((FlatWorldStateArchive) archive).getCachedWorldStorageManager();
      var blockchain = protocolContext.getBlockchain();

      // prime state-root-to-blockhash cache
      primeWorldStateArchive(cachedStorageManager, blockchain);

      // subscribe to initial sync completed events to start/stop snap server:
      protocolContext
          .getSynchronizer()
          .filter(z -> z instanceof DefaultSynchronizer)
          .map(DefaultSynchronizer.class::cast)
          .ifPresentOrElse(
              z -> this.listenerId.set(z.subscribeInitialSync(this)),
              () -> LOGGER.warn("SnapServer created without reference to sync status"));
    }
  }

  /**
   * Create a snap server without registering a listener for worldstate initial sync events or
   * priming worldstates by root hash.
   */
  SnapServer(
      final EthMessages snapMessages,
      final Function<Optional<Hash>, Optional<BonsaiWorldStateKeyValueStorage>>
          worldStateStorageProvider) {
    this.snapMessages = snapMessages;
    this.worldStateStorageProvider = worldStateStorageProvider;
    registerResponseConstructors();
  }

  @Override
  public void onInitialSyncCompleted() {
    start();
  }

  @Override
  public void onInitialSyncRestart() {
    stop();
  }

  public SnapServer start() {
    isStarted.set(true);
    return this;
  }

  public SnapServer stop() {
    isStarted.set(false);
    return this;
  }

  private void primeWorldStateArchive(
      final Optional<CachedWorldStorageManager> storageManager, final Blockchain blockchain) {
    // at startup, prime the latest worldstates by roothash:
    storageManager.ifPresent(
        cache -> cache.primeRootToBlockHashCache(blockchain, PRIME_STATE_ROOT_CACHE_LIMIT));
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

    if (!isStarted.get()) {
      return EMPTY_ACCOUNT_RANGE;
    }

    final GetAccountRangeMessage getAccountRangeMessage = GetAccountRangeMessage.readFrom(message);
    final GetAccountRangeMessage.Range range = getAccountRangeMessage.range(true);
    final int maxResponseBytes = Math.min(range.responseBytes().intValue(), MAX_RESPONSE_SIZE);

    LOGGER
        .atTrace()
        .setMessage("Receive getAccountRangeMessage for {} from {} to {}")
        .addArgument(range.worldStateRootHash()::toHexString)
        .addArgument(range.startKeyHash()::toHexString)
        .addArgument(range.endKeyHash()::toHexString)
        .log();
    try {
      var worldStateHash = getAccountRangeMessage.range(true).worldStateRootHash();

      return worldStateStorageProvider
          .apply(Optional.of(worldStateHash))
          .map(
              storage -> {
                NavigableMap<Bytes32, Bytes> accounts =
                    storage.streamFlatAccounts(
                        range.startKeyHash(),
                        range.endKeyHash(),
                        new StatefulPredicate(
                            "account",
                            maxResponseBytes,
                            (pair) -> {
                              var rlpOutput = new BytesValueRLPOutput();
                              rlpOutput.startList();
                              rlpOutput.writeBytes(pair.getFirst());
                              rlpOutput.writeRLPBytes(pair.getSecond());
                              rlpOutput.endList();
                              return rlpOutput.encodedSize();
                            }));

                if (accounts.isEmpty()) {
                  // fetch next account after range, if it exists
                  LOGGER.debug(
                      "found no accounts in range, taking first value starting from {}",
                      range.endKeyHash().toHexString());
                  accounts = storage.streamFlatAccounts(range.endKeyHash(), UInt256.MAX_VALUE, 1L);
                }

                final var worldStateProof = new WorldStateProofProvider(storage);
                final List<Bytes> proof =
                    worldStateProof.getAccountProofRelatedNodes(
                        range.worldStateRootHash(), Hash.wrap(range.startKeyHash()));

                if (!accounts.isEmpty()) {
                  proof.addAll(
                      worldStateProof.getAccountProofRelatedNodes(
                          range.worldStateRootHash(), Hash.wrap(accounts.lastKey())));
                }
                var resp = AccountRangeMessage.create(accounts, proof);
                if (accounts.isEmpty()) {
                  LOGGER.warn(
                      "returned empty account range message for {} to  {}, proof count {}",
                      range.startKeyHash(),
                      range.endKeyHash(),
                      proof.size());
                }
                LOGGER.debug(
                    "returned account range message with {} accounts and {} proofs",
                    accounts.size(),
                    proof.size());
                LOGGER.trace(
                    "returned accounts {}",
                    accounts.keySet().stream()
                        .map(Bytes::toHexString)
                        .collect(Collectors.joining("\t\n")));
                return resp;
              })
          .orElseGet(
              () -> {
                // TODO: demote to debug
                LOGGER.info("returned empty account range due to worldstate not present");
                return EMPTY_ACCOUNT_RANGE;
              });
    } catch (Exception ex) {
      LOGGER.error("Unexpected exception serving account range request", ex);
    }
    return EMPTY_ACCOUNT_RANGE;
  }

  MessageData constructGetStorageRangeResponse(final MessageData message) {
    if (!isStarted.get()) {
      return EMPTY_STORAGE_RANGE;
    }

    final GetStorageRangeMessage getStorageRangeMessage = GetStorageRangeMessage.readFrom(message);
    final GetStorageRangeMessage.StorageRange range = getStorageRangeMessage.range(true);
    final int maxResponseBytes = Math.min(range.responseBytes().intValue(), MAX_RESPONSE_SIZE);

    LOGGER
        .atTrace()
        .setMessage("Receive get storage range message size {} from {} to {} for {}")
        .addArgument(message::getSize)
        .addArgument(() -> range.startKeyHash().toHexString())
        .addArgument(
            () -> Optional.ofNullable(range.endKeyHash()).map(Hash::toHexString).orElse("''"))
        .addArgument(
            () ->
                range.hashes().stream()
                    .map(Bytes32::toHexString)
                    .collect(Collectors.joining(",", "[", "]")))
        .log();
    try {
      return worldStateStorageProvider
          .apply(Optional.of(range.worldStateRootHash()))
          .map(
              storage -> {
                // reusable predicate to limit by rec count and bytes:
                var statefulPredicate =
                    new StatefulPredicate(
                        "storage",
                        maxResponseBytes,
                        (pair) -> {
                          var slotRlpOutput = new BytesValueRLPOutput();
                          slotRlpOutput.startList();
                          slotRlpOutput.writeBytes(pair.getFirst());
                          slotRlpOutput.writeBytes(pair.getSecond());
                          slotRlpOutput.endList();
                          return slotRlpOutput.encodedSize();
                        });

                // only honor start and end hash if request is for a single account's storage:
                Bytes32 startKeyBytes, endKeyBytes;
                if (range.hashes().size() > 1) {
                  startKeyBytes = Bytes32.ZERO;
                  endKeyBytes = HASH_LAST;
                } else {
                  startKeyBytes = range.startKeyHash();
                  endKeyBytes = range.endKeyHash();
                }

                ArrayDeque<NavigableMap<Bytes32, Bytes>> collectedStorages = new ArrayDeque<>();
                Set<Bytes> proofNodes = new LinkedHashSet<>();
                final var worldStateProof = new WorldStateProofProvider(storage);

                for (var forAccountHash : range.hashes()) {

                  var accountStorages =
                      storage.streamFlatStorages(
                          Hash.wrap(forAccountHash), startKeyBytes, endKeyBytes, statefulPredicate);
                  // don't send empty storage ranges
                  if (accountStorages.size() > 0) {
                    collectedStorages.add(accountStorages);
                  }

                  // if this a partial storage range was requested, or we interrupted storage due
                  // to request limits, send proofs:
                  if (!(startKeyBytes.equals(Hash.ZERO) && endKeyBytes.equals(HASH_LAST))
                      || !statefulPredicate.shouldGetMore()) {
                    // send a proof for the left side
                    proofNodes.addAll(
                        worldStateProof.getStorageProofRelatedNodes(
                            getAccountStorageRoot(forAccountHash, storage),
                            forAccountHash,
                            Hash.wrap(startKeyBytes)));
                    // and last account, if we have keys:
                    if (!accountStorages.isEmpty()) {
                      proofNodes.addAll(
                          worldStateProof.getStorageProofRelatedNodes(
                              getAccountStorageRoot(forAccountHash, storage),
                              forAccountHash,
                              Hash.wrap(accountStorages.lastKey())));
                    }
                    break;
                  }
                }

                var resp =
                    StorageRangeMessage.create(collectedStorages, proofNodes.stream().toList());

                LOGGER.debug(
                    "returned storage range message with {} storages and {} proofs",
                    collectedStorages.size(),
                    proofNodes.size());
                return resp;
              })
          .orElseGet(
              () -> {
                LOGGER.info("returned empty storage range due to missing worldstate");
                return EMPTY_STORAGE_RANGE;
              });
    } catch (Exception ex) {
      LOGGER.error("Unexpected exception serving storage range request", ex);
      return EMPTY_STORAGE_RANGE;
    }
  }

  MessageData constructGetBytecodesResponse(final MessageData message) {

    if (!isStarted.get()) {
      return EMPTY_BYTE_CODES_MESSAGE;
    }

    final GetByteCodesMessage getByteCodesMessage = GetByteCodesMessage.readFrom(message);
    final GetByteCodesMessage.CodeHashes codeHashes = getByteCodesMessage.codeHashes(true);
    final int maxResponseBytes = Math.min(codeHashes.responseBytes().intValue(), MAX_RESPONSE_SIZE);
    LOGGER
        .atTrace()
        .setMessage("Receive get bytecodes message for {} hashes")
        .addArgument(codeHashes.hashes()::size)
        .log();

    // there is no worldstate root or block header for us to use, so default to head.  This
    // can cause problems for self-destructed contracts pre-shanghai.  for now since this impl
    // is deferring to #5889, we can just get any flat code storage and know we are not deleting
    // code for now.
    try {
      return worldStateStorageProvider
          .apply(Optional.empty())
          .map(
              storage -> {
                List<Bytes> codeBytes = new ArrayDeque<>();
                for (Bytes32 codeHash : codeHashes.hashes()) {
                  Optional<Bytes> optCode = storage.getCode(codeHash, null);
                  if (optCode.isPresent()) {
                    if (sumListBytes(codeBytes) + optCode.get().size() > maxResponseBytes) {
                      break;
                    }
                    codeBytes.add(optCode.get());
                  }
                }
                return ByteCodesMessage.create(codeBytes);
              })
          .orElseGet(
              () -> {
                LOGGER.info("returned empty byte codes message due to missing worldstate");
                return EMPTY_BYTE_CODES_MESSAGE;
              });
    } catch (Exception ex) {
      LOGGER.error("Unexpected exception serving bytecodes request", ex);
      return EMPTY_BYTE_CODES_MESSAGE;
    }
  }

  MessageData constructGetTrieNodesResponse(final MessageData message) {

    if (!isStarted.get()) {
      return EMPTY_TRIE_NODES_MESSAGE;
    }

    final GetTrieNodesMessage getTrieNodesMessage = GetTrieNodesMessage.readFrom(message);
    final GetTrieNodesMessage.TrieNodesPaths triePaths = getTrieNodesMessage.paths(true);
    final int maxResponseBytes = Math.min(triePaths.responseBytes().intValue(), MAX_RESPONSE_SIZE);
    LOGGER
        .atTrace()
        .setMessage("Receive get trie nodes message of size {}")
        .addArgument(() -> triePaths.paths().size())
        .log();

    try {
      // TODO: implement limits
      return worldStateStorageProvider
          .apply(Optional.of(triePaths.worldStateRootHash()))
          .map(
              storage -> {
                ArrayList<Bytes> trieNodes = new ArrayList<>();
                for (var triePath : triePaths.paths()) {
                  // first element in paths is account
                  if (triePath.size() == 1) {
                    // if there is only one path, presume it should be compact encoded account path
                    var optStorage =
                        storage.getTrieNodeUnsafe(CompactEncoding.decode(triePath.get(0)));
                    if (optStorage.isPresent()) {
                      if (sumListBytes(trieNodes) + optStorage.get().size() > maxResponseBytes) {
                        break;
                      }
                      trieNodes.add(optStorage.get());
                    }

                  } else {
                    // otherwise the first element should be account hash, and subsequent paths
                    // are compact encoded account storage paths

                    final Bytes accountPrefix = triePath.get(0);

                    List<Bytes> storagePaths = triePath.subList(1, triePath.size());
                    for (var path : storagePaths) {
                      var optStorage =
                          storage.getTrieNodeUnsafe(
                              Bytes.concatenate(accountPrefix, CompactEncoding.decode(path)));
                      if (optStorage.isPresent()) {
                        if (sumListBytes(trieNodes) + optStorage.get().size() > maxResponseBytes) {
                          break;
                        }
                        trieNodes.add(optStorage.get());
                      }
                    }
                  }
                }
                var resp = TrieNodesMessage.create(trieNodes);
                LOGGER.info("returned trie nodes message with {} entries", trieNodes.size());
                return resp;
              })
          .orElseGet(
              () -> {
                LOGGER.info("returned empty trie nodes message due to missing worldstate");
                return EMPTY_TRIE_NODES_MESSAGE;
              });
    } catch (Exception ex) {
      LOGGER.error("Unexpected exception serving trienodes request", ex);
      return EMPTY_TRIE_NODES_MESSAGE;
    }
  }

  static class StatefulPredicate implements Predicate<Pair<Bytes32, Bytes>> {

    final AtomicInteger byteLimit = new AtomicInteger(0);
    final AtomicInteger recordLimit = new AtomicInteger(0);
    final AtomicBoolean shouldContinue = new AtomicBoolean(true);
    final Function<Pair<Bytes32, Bytes>, Integer> encodingSizeAccumulator;
    final int maxResponseBytes;
    final String forWhat;

    StatefulPredicate(
        final String forWhat,
        final int maxResponseBytes,
        final Function<Pair<Bytes32, Bytes>, Integer> encodingSizeAccumulator) {
      this.maxResponseBytes = maxResponseBytes;
      this.forWhat = forWhat;
      this.encodingSizeAccumulator = encodingSizeAccumulator;
    }

    public boolean shouldGetMore() {
      return shouldContinue.get();
    }

    @Override
    public boolean test(final Pair<Bytes32, Bytes> pair) {
      LOGGER
          .atTrace()
          .setMessage("{} pre-accumulate limits, bytes: {} , stream count: {}")
          .addArgument(() -> forWhat)
          .addArgument(byteLimit::get)
          .addArgument(recordLimit::get)
          .log();

      var underRecordLimit = recordLimit.addAndGet(1) <= MAX_ENTRIES_PER_REQUEST;
      var underByteLimit =
          byteLimit.accumulateAndGet(0, (cur, __) -> cur + encodingSizeAccumulator.apply(pair))
              < maxResponseBytes + 1;
      if (underRecordLimit && underByteLimit) {
        return true;
      } else {
        shouldContinue.set(false);
        LOGGER
            .atDebug()
            .setMessage("{} post-accumulate limits, bytes: {} , stream count: {}")
            .addArgument(() -> forWhat)
            .addArgument(byteLimit::get)
            .addArgument(recordLimit::get)
            .log();
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

  private int sumListBytes(final List<Bytes> listOfBytes) {
    return listOfBytes.stream().map(Bytes::size).reduce((a, b) -> a + b).orElse(0);
  }
}
