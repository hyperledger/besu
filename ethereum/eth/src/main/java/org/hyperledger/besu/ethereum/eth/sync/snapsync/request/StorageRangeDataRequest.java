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
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.trie.CommitVisitor;
import org.hyperledger.besu.ethereum.trie.InnerNodeDiscoveryManager;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.SnapPutVisitor;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
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
      final WorldDownloadState<SnapDataRequest> downloadState) {
    if (!isProofValid) {
      return 0;
    }

    final Bytes32 storageRoot =
        proofs.isEmpty() ? MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH : getStorageRoot();

    final Map<Bytes32, Bytes> proofsEntries = Collections.synchronizedMap(new HashMap<>());
    for (Bytes proof : proofs) {
      proofsEntries.put(Hash.hash(proof), proof);
    }

    final InnerNodeDiscoveryManager<Bytes> snapStoredNodeFactory =
        new InnerNodeDiscoveryManager<>(
            (location, hash) -> Optional.ofNullable(proofsEntries.get(hash)),
            Function.identity(),
            Function.identity(),
            startKeyHash,
            slots.lastKey(),
            true);

    final Hash wrappedAccountHash = Hash.wrap(accountHash);

    final MerklePatriciaTrie<Bytes, Bytes> trie =
        new StoredMerklePatriciaTrie<>(snapStoredNodeFactory, storageRoot);

    for (Map.Entry<Bytes32, Bytes> account : slots.entrySet()) {
      trie.put(account.getKey(), new SnapPutVisitor<>(snapStoredNodeFactory, account.getValue()));
    }

    // search incomplete nodes in the range
    final AtomicInteger nbNodesSaved = new AtomicInteger();
    final NodeUpdater nodeUpdater =
        (location, hash, value) -> {
          updater.putAccountStorageTrieNode(wrappedAccountHash, location, hash, value);
          nbNodesSaved.getAndIncrement();
        };

    trie.commit(
        nodeUpdater,
        (new CommitVisitor<>(nodeUpdater) {
          @Override
          public void maybeStoreNode(final Bytes location, final Node<Bytes> node) {
            if (!node.isNeedHeal()) {
              super.maybeStoreNode(location, node);
            }
          }
        }));

    return nbNodesSaved.get();
  }

  @Override
  public boolean checkProof(
      final WorldDownloadState<SnapDataRequest> downloadState,
      final WorldStateProofProvider worldStateProofProvider) {
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
  public boolean isDataPresent() {
    return !slots.isEmpty() || !proofs.isEmpty();
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(
      final WorldStateStorage worldStateStorage, final SnapSyncState snapSyncState) {
    final List<SnapDataRequest> childRequests = new ArrayList<>();

    if (!isProofValid) {
      return Stream.empty();
    }

    findNewBeginElementInRange(storageRoot, proofs, slots, endKeyHash)
        .ifPresent(
            missingRightElement -> {
              if (startKeyHash.equals(MIN_RANGE) && endKeyHash.equals(MAX_RANGE)) {
                // need to heal this account storage
                childRequests.add(
                    createAccountHealDataRequest(
                        Hash.wrap(storageRoot),
                        Hash.wrap(accountHash),
                        getRootHash(),
                        Bytes.EMPTY));
              }
              RangeManager.generateRanges(missingRightElement, endKeyHash, findNbRanges())
                  .forEach(
                      (key, value) ->
                          childRequests.add(
                              createStorageRangeDataRequest(accountHash, storageRoot, key, value)));
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
      if (nbRangesNeeded >= 16) {
        return 16;
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
    if (proofs == null) {
      new Exception().printStackTrace(System.out);
    }
    this.proofs = proofs;
  }

  public void setSlots(final TreeMap<Bytes32, Bytes> slots) {
    if (slots == null) {
      new Exception().printStackTrace(System.out);
    }
    this.slots = slots;
  }
}
