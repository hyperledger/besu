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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.MAX_RANGE;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.findNewBeginElementInRange;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RequestType.STORAGE_RANGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetStorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.StorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.MissingNode;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredNodeFactory;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import kotlin.collections.ArrayDeque;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** Returns a list of storages and the merkle proofs of an entire range */
public class StorageRangeDataRequest extends SnapDataRequest {

  private static final Logger LOG = LogManager.getLogger();

  private final GetStorageRangeMessage request;
  private GetStorageRangeMessage.StorageRange range;
  private StorageRangeMessage.SlotRangeData response;
  private boolean isTaskCompleted = true;
  private final int depth;
  private final long priority;

  protected StorageRangeDataRequest(
      final Hash rootHash,
      final ArrayDeque<Bytes32> accountsHashes,
      final ArrayDeque<Bytes32> storageRoots,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final int depth,
      final long priority) {
    super(STORAGE_RANGE, rootHash);
    this.depth = depth;
    this.priority = priority;
    LOG.trace(
        "create get storage range data request for {} accounts {} with root hash={} from {} to {}",
        accountsHashes.size(),
        (accountsHashes.size() == 1) ? accountsHashes.get(0) : "...",
        rootHash,
        startKeyHash,
        endKeyHash);
    request =
        GetStorageRangeMessage.create(
            rootHash,
            accountsHashes,
            Optional.of(storageRoots),
            startKeyHash,
            endKeyHash,
            BigInteger.valueOf(524288));
  }

  @Override
  protected int doPersist(final WorldStateStorage worldStateStorage, final Updater updater) {

    final AtomicInteger nbNodesSaved = new AtomicInteger();

    if (getData().isPresent()) {
      final StorageRangeMessage.SlotRangeData slotRangeData = getResponse();

      Map<Bytes32, Bytes> proofsEntries = Collections.synchronizedMap(new HashMap<>());
      for (Bytes proof : slotRangeData.proofs()) {
        proofsEntries.put(Hash.hash(proof), proof);
      }

      final ArrayDeque<Bytes32> storageRoots =
          getStorageRangeMessage().getStorageRoots().orElseThrow();

      for (int i = 0; i < slotRangeData.slots().size(); i++) {

        final Hash accountHash = Hash.wrap(getRange().hashes().get(i));

        final StoredNodeFactory<Bytes> snapStoredNodeFactory =
            new StoredNodeFactory<>(
                (location, hash) -> Optional.ofNullable(proofsEntries.get(hash)),
                Function.identity(),
                Function.identity()) {
              @Override
              public Optional<Node<Bytes>> retrieve(final Bytes location, final Bytes32 hash)
                  throws MerkleTrieException {
                return super.retrieve(location, hash)
                    .or(() -> Optional.of(new MissingNode<>(hash, location)));
              }
            };
        MerklePatriciaTrie<Bytes, Bytes> trie =
            new StoredMerklePatriciaTrie<>(snapStoredNodeFactory, storageRoots.get(i));

        for (Map.Entry<Bytes32, Bytes> slot : slotRangeData.slots().get(i).entrySet()) {
          trie.put(slot.getKey(), slot.getValue());
        }

        trie.commit(
            (location, nodeHash, value) -> {
              updater.putAccountStorageTrieNode(accountHash, location, nodeHash, value);
              nbNodesSaved.getAndIncrement();
            });
      }
    }

    return nbNodesSaved.get();
  }

  @Override
  protected boolean isTaskCompleted(
      final WorldDownloadState<SnapDataRequest> downloadState,
      final SnapSyncState fastSyncState,
      final EthPeers ethPeers,
      final WorldStateProofProvider worldStateProofProvider) {
    final GetStorageRangeMessage.StorageRange requestData = getRange();
    final StorageRangeMessage.SlotRangeData responseData = getResponse();

    isTaskCompleted = true;

    if (responseData.slots().isEmpty() && responseData.proofs().isEmpty()) {
      isTaskCompleted = false;
    }

    // reject if the peer sent more slots than requested
    if (requestData.hashes().size() < responseData.slots().getSize()) {
      isTaskCompleted = false;
    }

    for (int i = 0; i < responseData.slots().getSize(); i++) {
      // if this is not the last account in the list we must validate the full range
      final boolean isLastRange = i == responseData.slots().getSize() - 1;
      final Hash endHash = isLastRange ? requestData.endKeyHash() : MAX_RANGE;
      final Optional<List<Bytes>> proofs =
          isLastRange ? Optional.of(responseData.proofs()) : Optional.empty();
      if (!worldStateProofProvider.isValidRangeProof(
          requestData.startKeyHash(),
          endHash,
          request.getStorageRoots().orElseThrow().get(i),
          proofs,
          responseData.slots().get(i))) {
        isTaskCompleted = false;
      }
    }

    if (!isTaskCompleted
        && getOriginalRootHash().compareTo(requestData.worldStateRootHash()) != 0) {
      LOG.trace(
          "Invalidated root hash detected {} during get storage range data request {}",
          getOriginalRootHash());
      if (requestData.hashes().size() > 1) {
        // split this range in order to fill more account quickly
        AtomicLong counter = new AtomicLong(getPriority() * 16);
        RangeManager.generateRanges(requestData.hashes().first(), requestData.hashes().last(), 16)
            .forEach(
                (min, max) ->
                    downloadState.enqueueRequest(
                        createAccountRangeDataRequest(
                            requestData.worldStateRootHash(),
                            min,
                            max,
                            getDepth() + 1,
                            counter.incrementAndGet())));
      }
      return true;
    }

    return isTaskCompleted;
  }

  public GetStorageRangeMessage getStorageRangeMessage() {
    return request;
  }

  public GetStorageRangeMessage.StorageRange getRange() {
    if (range == null) {
      range = request.range(false);
    }
    return range;
  }

  public StorageRangeMessage.SlotRangeData getResponse() {
    if (response == null) {
      response = new StorageRangeMessage(getData().orElseThrow()).slotsData(true);
    }
    return response;
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(final WorldStateStorage worldStateStorage) {
    final GetStorageRangeMessage.StorageRange requestData = getRange();
    final StorageRangeMessage.SlotRangeData responseData = getResponse();
    final List<SnapDataRequest> childRequests = new ArrayList<>();

    // flag storages if peers cannot find them
    if (isTaskCompleted) {
      // new request if there are some missing accounts
      if (responseData.slots().isEmpty()) {
        return Stream.empty();
      } else {
        final int lastSlotIndex = responseData.slots().size();
        final boolean isLastRange = lastSlotIndex == requestData.hashes().size();
        final Hash endKeyHash = isLastRange ? requestData.endKeyHash() : MAX_RANGE;
        final Bytes32 lastSlotRootHash =
            getStorageRangeMessage().getStorageRoots().orElseThrow().get(lastSlotIndex - 1);

        // new request if there are some missing storage slots for the last account sent
        // create a specific request for this big storage
        findNewBeginElementInRange(
                lastSlotRootHash, responseData.proofs(), responseData.slots().last(), endKeyHash)
            .ifPresent(
                missingRightElement -> {
                  childRequests.add(
                      createStorageRangeDataRequest(
                          new ArrayDeque<>(List.of(requestData.hashes().get(lastSlotIndex - 1))),
                          new ArrayDeque<>(List.of(lastSlotRootHash)),
                          missingRightElement,
                          endKeyHash,
                          depth,
                          priority));
                });

        // new request if we are missing accounts in the response
        if (requestData.hashes().size() > lastSlotIndex) {
          final ArrayDeque<Bytes32> missingAccounts =
              new ArrayDeque<>(
                  requestData.hashes().stream().skip(lastSlotIndex).collect(Collectors.toList()));
          final ArrayDeque<Bytes32> missingStorageRoots =
              new ArrayDeque<>(
                  getStorageRangeMessage().getStorageRoots().orElseThrow().stream()
                      .skip(lastSlotIndex)
                      .collect(Collectors.toList()));

          childRequests.add(
              createStorageRangeDataRequest(
                  missingAccounts,
                  missingStorageRoots,
                  requestData.startKeyHash(),
                  requestData.endKeyHash(),
                  depth,
                  priority));
        }
      }
    }
    return childRequests.stream();
  }

  @Override
  public void clear() {
    range = null;
    response = null;
  }

  @Override
  public long getPriority() {
    return priority;
  }

  @Override
  public int getDepth() {
    return depth;
  }
}
