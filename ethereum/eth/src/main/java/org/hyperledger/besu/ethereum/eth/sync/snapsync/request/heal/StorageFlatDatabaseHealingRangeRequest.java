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
package org.hyperledger.besu.ethereum.eth.sync.snapsync.request.heal;

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.MAX_RANGE;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.MIN_RANGE;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.findNewBeginElementInRange;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RequestType.STORAGE_RANGE;

import org.apache.tuweni.rlp.RLP;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapWorldDownloadState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.StorageRangeDataRequest;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.RangeStorageEntriesCollector;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** Returns a list of accounts and the merkle proofs of an entire range */
@SuppressWarnings({"MismatchedQueryAndUpdateOfCollection", "TooBroadScope", "ModifiedButNotUsed"})
public class StorageFlatDatabaseHealingRangeRequest extends SnapDataRequest {

  private final Hash accountHash;
  private final Bytes32 storageRoot;
  private final Bytes32 startKeyHash;
  private final Bytes32 endKeyHash;

  private List<Bytes> proofs;
  private TreeMap<Bytes32, Bytes> slots;

  private Optional<Boolean> isProofValid;

  private static Long valid = 0L;

  public StorageFlatDatabaseHealingRangeRequest(
      final Hash rootHash,
      final Bytes32 accountHash,
      final Bytes32 storageRoot,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash) {
    super(STORAGE_RANGE, rootHash);
    this.accountHash = Hash.wrap(accountHash);
    this.storageRoot = storageRoot;
    this.startKeyHash = startKeyHash;
    this.endKeyHash = endKeyHash;
    this.isProofValid = Optional.empty();
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(
      final SnapWorldDownloadState downloadState,
      final WorldStateStorage worldStateStorage,
      final SnapSyncState snapSyncState) {
    final List<SnapDataRequest> childRequests = new ArrayList<>();
    if(slots.isEmpty() && storageRoot.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)){
      return Stream.empty();
    }
    findNewBeginElementInRange(storageRoot, proofs, slots, endKeyHash)
        .ifPresent(
            missingRightElement -> {
              final int nbRanges = findNbRanges(slots);
              RangeManager.generateRanges(missingRightElement, endKeyHash, nbRanges)
                  .forEach(
                      (key, value) -> {
                        final StorageRangeDataRequest storageRangeDataRequest =
                            createStorageRangeDataRequest(
                                getRootHash(), accountHash, storageRoot, key, value);
                        childRequests.add(storageRangeDataRequest);
                      });
            });
    return childRequests.stream();
  }

  private int findNbRanges(final TreeMap<Bytes32, Bytes> slots) {
    if (startKeyHash.equals(MIN_RANGE) && endKeyHash.equals(MAX_RANGE)) {
      return MAX_RANGE
          .toUnsignedBigInteger()
          .divide(
              slots.lastKey().toUnsignedBigInteger().subtract(startKeyHash.toUnsignedBigInteger()))
          .intValue();
    }
    return 1;
  }

  public Hash getAccountHash() {
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

  @Override
  public boolean isResponseReceived() {
    return true;
  }

  public void addLocalData(
      final WorldStateProofProvider worldStateProofProvider,
      final TreeMap<Bytes32, Bytes> slots,
      final ArrayDeque<Bytes> proofs) {
    if(storageRoot.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH) && slots.isEmpty()){
      isProofValid = Optional.of(true);
    }else {
      if (!slots.isEmpty() || !proofs.isEmpty()) {
        if (!worldStateProofProvider.isValidRangeProof(
                startKeyHash, endKeyHash, storageRoot, proofs, slots)) {
          System.out.println(
                  "slot proof invalid for account "
                          + accountHash
                          + " "
                          + proofs.toString()
                          + " "
                          + storageRoot
                          + " "
                          + MerkleTrie.EMPTY_TRIE_NODE_HASH
                          + " "
                          + slots.size());
          isProofValid = Optional.of(false);
        } else {
          isProofValid = Optional.of(true);
        }
      }
    }
    this.slots = slots;
    this.proofs = proofs;
  }

  @Override
  protected int doPersist(
      final WorldStateStorage worldStateStorage,
      final WorldStateStorage.Updater updater,
      final SnapWorldDownloadState downloadState,
      final SnapSyncState snapSyncState) {

    if (!isProofValid.orElse(false)) {

      final BonsaiWorldStateKeyValueStorage.Updater bonsaiUpdater =
          (BonsaiWorldStateKeyValueStorage.Updater) updater;

      System.out.println(
          "Range not valid from "
                  +" "+accountHash+" "
                  + " "
                  +storageRoot+
                  " "
              + startKeyHash
                  +" "
                  +slots.size()
                  +" "
                  +proofs.size()
              + " "
              + isResponseReceived()
              + " fixed "
      + " "
      +valid);

      final MerkleTrie<Bytes, Bytes> storageTrie =
          new StoredMerklePatriciaTrie<>(
              (location, hash) ->
                  worldStateStorage.getAccountStorageTrieNode(accountHash, location, hash),
              storageRoot,
              Function.identity(),
              Function.identity());

      final RangeStorageEntriesCollector collector =
          RangeStorageEntriesCollector.createCollector(
              startKeyHash, endKeyHash, Integer.MAX_VALUE, Integer.MAX_VALUE);
      final TrieIterator<Bytes> visitor = RangeStorageEntriesCollector.createVisitor(collector);
      final TreeMap<Bytes32, Bytes> slots =
          (TreeMap<Bytes32, Bytes>)
              storageTrie.entriesFrom(
                  root ->
                      RangeStorageEntriesCollector.collectEntries(
                          collector, visitor, root, startKeyHash));

      Map<Bytes32, Bytes> keysAdd = new TreeMap<>();
      Map<Bytes32, Bytes> keysToDelete = new TreeMap<>(slots);
      slots.forEach(
          (key, value) -> {
            if (keysToDelete.containsKey(key)) {
              keysToDelete.remove(key);
            } else {
              keysAdd.put(key, value);
              bonsaiUpdater.putStorageValueBySlotHash(accountHash, Hash.wrap(key), Bytes32.leftPad(RLP.decodeValue(value)));
            }
          });

      keysToDelete.forEach(
          (key, value) -> bonsaiUpdater.removeStorageValueBySlotHash(accountHash, Hash.wrap(key)));
    } else {
      valid+=1;
    }
    return 0;
  }
}
