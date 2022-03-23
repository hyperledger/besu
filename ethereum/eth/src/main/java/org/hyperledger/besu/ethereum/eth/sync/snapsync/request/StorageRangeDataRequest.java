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
package org.hyperledger.besu.ethereum.eth.sync.snapsync.request;

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.MAX_RANGE;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.MIN_RANGE;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.findNewBeginElementInRange;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RequestType.STORAGE_RANGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapWorldDownloadState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.StackTrie;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Returns a list of storages and the merkle proofs of an entire range */
public class StorageRangeDataRequest extends SnapDataRequest {

  private static final Logger LOG = LoggerFactory.getLogger(StorageRangeDataRequest.class);

  private final Bytes32 accountHash;
  private final Bytes32 storageRoot;
  private final Bytes32 startKeyHash;
  private final Bytes32 endKeyHash;

  private StackTrie stackTrie;
  private TreeMap<Bytes32, Bytes> slots;
  private ArrayDeque<Bytes> proofs;
  private boolean isProofValid;

  protected StorageRangeDataRequest(
      final Hash rootHash,
      final Bytes32 accountHash,
      final Bytes32 storageRoot,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash) {
    super(STORAGE_RANGE, rootHash);
    this.accountHash = accountHash;
    this.storageRoot = storageRoot;
    this.startKeyHash = startKeyHash;
    this.endKeyHash = endKeyHash;
    this.proofs = new ArrayDeque<>();
    this.slots = new TreeMap<>();
    addStackTrie(Optional.empty());
    LOG.trace(
        "create get storage range data request for account {} with root hash={} from {} to {}",
        accountHash,
        rootHash,
        startKeyHash,
        endKeyHash);
  }

  @Override
  protected int doPersist(
      final WorldStateStorage worldStateStorage,
      final Updater updater,
      final WorldDownloadState<SnapDataRequest> downloadState,
      final SnapSyncState snapSyncState) {

    if (isProofValid) {
      stackTrie.addKeys(slots);
      stackTrie.addProofs(proofs);
    }

    // search incomplete nodes in the range
    final AtomicInteger nbNodesSaved = new AtomicInteger();
    final AtomicReference<Updater> updaterTmp = new AtomicReference<>(worldStateStorage.updater());
    final NodeUpdater nodeUpdater =
        (location, hash, value) -> {
          // create small batch in order to commit small amount of nodes at the same time
          updaterTmp.get().putAccountStorageTrieNode(Hash.wrap(accountHash), location, hash, value);
          if (nbNodesSaved.getAndIncrement() % 1000 == 0) {
            updaterTmp.get().commit();
            updaterTmp.set(worldStateStorage.updater());
          }
        };
    stackTrie.commit(nodeUpdater);

    updaterTmp.get().commit();

    return nbNodesSaved.get();
  }

  @Override
  public boolean checkProof(
      final WorldDownloadState<SnapDataRequest> downloadState,
      final WorldStateProofProvider worldStateProofProvider,
      final SnapSyncState snapSyncState) {
    if (!worldStateProofProvider.isValidRangeProof(
        startKeyHash, endKeyHash, storageRoot, proofs, slots)) {
      downloadState.enqueueRequest(
          createAccountDataRequest(
              getRootHash(), Hash.wrap(accountHash), startKeyHash, endKeyHash));
      isProofValid = false;
    } else {
      isProofValid = true;
    }
    return isProofValid;
  }

  @Override
  public boolean isValid() {
    return !slots.isEmpty() || !proofs.isEmpty();
  }

  @Override
  public boolean isExpired(final SnapSyncState snapSyncState) {
    return snapSyncState.isExpired(this);
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(
      final SnapWorldDownloadState downloadState,
      final WorldStateStorage worldStateStorage,
      final SnapSyncState snapSyncState) {
    final List<SnapDataRequest> childRequests = new ArrayList<>();

    if (!isProofValid) {
      return Stream.empty();
    }

    findNewBeginElementInRange(storageRoot, proofs, slots, endKeyHash)
        .ifPresent(
            missingRightElement -> {
              final int nbRanges = findNbRanges();
              RangeManager.generateRanges(missingRightElement, endKeyHash, nbRanges)
                  .forEach(
                      (key, value) -> {
                        final StorageRangeDataRequest storageRangeDataRequest =
                            createStorageRangeDataRequest(accountHash, storageRoot, key, value);
                        storageRangeDataRequest.addStackTrie(Optional.of(stackTrie));
                        childRequests.add(storageRangeDataRequest);
                      });
              if (!snapSyncState.isHealInProgress()
                  && startKeyHash.equals(MIN_RANGE)
                  && endKeyHash.equals(MAX_RANGE)) {
                // need to heal this account storage
                downloadState.addInconsistentAccount(CompactEncoding.bytesToPath(accountHash));
              }
            });

    return childRequests.stream();
  }

  private int findNbRanges() {
    if (startKeyHash.equals(MIN_RANGE) && endKeyHash.equals(MAX_RANGE)) {
      final int nbRangesNeeded =
          MAX_RANGE
              .toUnsignedBigInteger()
              .divide(
                  slots
                      .lastKey()
                      .toUnsignedBigInteger()
                      .subtract(startKeyHash.toUnsignedBigInteger()))
              .intValue();
      if (nbRangesNeeded >= MAX_CHILD) {
        return MAX_CHILD;
      }
      return nbRangesNeeded;
    }
    return 1;
  }

  public Bytes32 getAccountHash() {
    return accountHash;
  }

  public Bytes32 getStorageRoot() {
    return storageRoot;
  }

  public Bytes32 getStartKeyHash() {
    return startKeyHash;
  }

  public Bytes32 getEndKeyHash() {
    return endKeyHash;
  }

  public void setProofs(final ArrayDeque<Bytes> proofs) {
    this.proofs = proofs;
  }

  public void setSlots(final TreeMap<Bytes32, Bytes> slots) {
    this.slots = slots;
  }

  public void addStackTrie(final Optional<StackTrie> maybeStackTrie) {
    stackTrie =
        maybeStackTrie
            .filter(StackTrie::addSegment)
            .orElse(new StackTrie(getRootHash(), 1, 256, startKeyHash));
  }
}
