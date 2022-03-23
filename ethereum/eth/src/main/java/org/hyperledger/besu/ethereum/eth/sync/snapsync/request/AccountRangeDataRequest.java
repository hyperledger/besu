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
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RequestType.ACCOUNT_RANGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapWorldDownloadState;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.CommitVisitor;
import org.hyperledger.besu.ethereum.trie.InnerNodeDiscoveryManager;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.SnapPutVisitor;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
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

/** Returns a list of accounts and the merkle proofs of an entire range */
public class AccountRangeDataRequest extends SnapDataRequest {

  private static final Logger LOG = LoggerFactory.getLogger(AccountRangeDataRequest.class);

  protected final Bytes32 startKeyHash;
  protected final Bytes32 endKeyHash;

  protected TreeMap<Bytes32, Bytes> accounts;
  protected ArrayDeque<Bytes> proofs;

  private final Optional<Bytes32> startStorageRange;
  private final Optional<Bytes32> endStorageRange;

  protected AccountRangeDataRequest(
      final Hash rootHash,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final Optional<Bytes32> startStorageRange,
      final Optional<Bytes32> endStorageRange) {
    super(ACCOUNT_RANGE, rootHash);
    this.startKeyHash = startKeyHash;
    this.endKeyHash = endKeyHash;
    this.accounts = new TreeMap<>();
    this.proofs = new ArrayDeque<>();
    this.startStorageRange = startStorageRange;
    this.endStorageRange = endStorageRange;
    LOG.trace(
        "create get account range data request with root hash={} from {} to {}",
        rootHash,
        startKeyHash,
        endKeyHash);
  }

  protected AccountRangeDataRequest(
      final Hash rootHash, final Bytes32 startKeyHash, final Bytes32 endKeyHash) {
    this(rootHash, startKeyHash, endKeyHash, Optional.empty(), Optional.empty());
  }

  protected AccountRangeDataRequest(
      final Hash rootHash,
      final Hash accountHash,
      final Bytes32 startStorageRange,
      final Bytes32 endStorageRange) {
    this(
        rootHash,
        accountHash,
        accountHash,
        Optional.of(startStorageRange),
        Optional.of(endStorageRange));
  }

  @Override
  protected int doPersist(
      final WorldStateStorage worldStateStorage,
      final Updater updater,
      final WorldDownloadState<SnapDataRequest> downloadState,
      final SnapSyncState snapSyncState) {

    if (startStorageRange.isPresent() && endStorageRange.isPresent()) {
      // not store the new account if we just want to complete the account thanks to another
      // rootHash
      return 0;
    }

    final Bytes32 storageRoot =
        proofs.isEmpty() ? MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH : getRootHash();

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
            accounts.lastKey(),
            true);

    final MerklePatriciaTrie<Bytes, Bytes> trie =
        new StoredMerklePatriciaTrie<>(snapStoredNodeFactory, storageRoot);

    for (Map.Entry<Bytes32, Bytes> account : accounts.entrySet()) {
      trie.put(account.getKey(), new SnapPutVisitor<>(snapStoredNodeFactory, account.getValue()));
    }

    // search incomplete nodes in the range
    final AtomicInteger nbNodesSaved = new AtomicInteger();
    final NodeUpdater nodeUpdater =
        (location, hash, value) -> {
          updater.putAccountStateTrieNode(location, hash, value);
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
      final WorldStateProofProvider worldStateProofProvider,
      final SnapSyncState snapSyncState) {

    // validate the range proof
    if (!worldStateProofProvider.isValidRangeProof(
        startKeyHash, endKeyHash, getRootHash(), proofs, accounts)) {
      clear();
      return false;
    }
    return true;
  }

  @Override
  public boolean isValid() {
    return !accounts.isEmpty() || !proofs.isEmpty();
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(
      final SnapWorldDownloadState downloadState,
      final WorldStateStorage worldStateStorage,
      final SnapSyncState snapSyncState) {
    final List<SnapDataRequest> childRequests = new ArrayList<>();

    // new request is added if the response does not match all the requested range
    findNewBeginElementInRange(getRootHash(), proofs, accounts, endKeyHash)
        .ifPresent(
            missingRightElement ->
                childRequests.add(
                    createAccountRangeDataRequest(getRootHash(), missingRightElement, endKeyHash)));

    // find missing storages and code
    for (Map.Entry<Bytes32, Bytes> account : accounts.entrySet()) {
      final StateTrieAccountValue accountValue =
          StateTrieAccountValue.readFrom(RLP.input(account.getValue()));
      if (!accountValue.getStorageRoot().equals(Hash.EMPTY_TRIE_HASH)) {
        childRequests.add(
            createStorageRangeDataRequest(
                account.getKey(),
                accountValue.getStorageRoot(),
                startStorageRange.orElse(MIN_RANGE),
                endStorageRange.orElse(MAX_RANGE)));
      }
      if (!accountValue.getCodeHash().equals(Hash.EMPTY)) {
        childRequests.add(createBytecodeRequest(account.getKey(), accountValue.getCodeHash()));
      }
    }
    return childRequests.stream();
  }

  public Bytes32 getStartKeyHash() {
    return startKeyHash;
  }

  public Bytes32 getEndKeyHash() {
    return endKeyHash;
  }

  public TreeMap<Bytes32, Bytes> getAccounts() {
    return accounts;
  }

  public void setAccounts(final TreeMap<Bytes32, Bytes> accounts) {
    this.accounts = accounts;
  }

  public ArrayDeque<Bytes> getProofs() {
    return proofs;
  }

  public void setProofs(final ArrayDeque<Bytes> proofs) {
    this.proofs = proofs;
  }

  @Override
  public void clear() {
    accounts = new TreeMap<>();
    proofs = new ArrayDeque<>();
  }
}
