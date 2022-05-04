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
import org.hyperledger.besu.ethereum.eth.sync.snapsync.StackTrie;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
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
  private final Optional<Bytes32> startStorageRange;
  private final Optional<Bytes32> endStorageRange;

  private final StackTrie stackTrie;
  private Optional<Boolean> isProofValid;

  protected AccountRangeDataRequest(
      final Hash rootHash,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final Optional<Bytes32> startStorageRange,
      final Optional<Bytes32> endStorageRange) {
    super(ACCOUNT_RANGE, rootHash);
    this.startKeyHash = startKeyHash;
    this.endKeyHash = endKeyHash;
    this.startStorageRange = startStorageRange;
    this.endStorageRange = endStorageRange;
    this.isProofValid = Optional.empty();
    this.stackTrie = new StackTrie(rootHash, startKeyHash);
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
      final SnapWorldDownloadState downloadState,
      final SnapSyncState snapSyncState) {

    if (startStorageRange.isPresent() && endStorageRange.isPresent()) {
      // not store the new account if we just want to complete the account thanks to another
      // rootHash
      return 0;
    }

    // search incomplete nodes in the range
    final AtomicInteger nbNodesSaved = new AtomicInteger();
    final NodeUpdater nodeUpdater =
        (location, hash, value) -> {
          updater.putAccountStateTrieNode(location, hash, value);
          nbNodesSaved.getAndIncrement();
        };

    stackTrie.commit(nodeUpdater);

    downloadState.getMetricsManager().notifyAccountsDownloaded(stackTrie.getElementsCount().get());

    return nbNodesSaved.get();
  }

  public void addResponse(
      final WorldStateProofProvider worldStateProofProvider,
      final TreeMap<Bytes32, Bytes> accounts,
      final ArrayDeque<Bytes> proofs) {
    if (!accounts.isEmpty() || !proofs.isEmpty()) {
      if (!worldStateProofProvider.isValidRangeProof(
          startKeyHash, endKeyHash, getRootHash(), proofs, accounts)) {
        isProofValid = Optional.of(false);
      } else {
        stackTrie.addElement(startKeyHash, proofs, accounts);
        isProofValid = Optional.of(true);
      }
    }
  }

  @Override
  public boolean isResponseReceived() {
    return isProofValid.orElse(false);
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(
      final SnapWorldDownloadState downloadState,
      final WorldStateStorage worldStateStorage,
      final SnapSyncState snapSyncState) {
    final List<SnapDataRequest> childRequests = new ArrayList<>();

    final StackTrie.TaskElement taskElement = stackTrie.getElement(startKeyHash);
    // new request is added if the response does not match all the requested range
    findNewBeginElementInRange(getRootHash(), taskElement.proofs(), taskElement.keys(), endKeyHash)
        .ifPresentOrElse(
            missingRightElement -> {
              downloadState
                  .getMetricsManager()
                  .notifyStateDownloaded(missingRightElement, endKeyHash);
              childRequests.add(
                  createAccountRangeDataRequest(getRootHash(), missingRightElement, endKeyHash));
            },
            () -> downloadState.getMetricsManager().notifyStateDownloaded(endKeyHash, endKeyHash));

    // find missing storages and code
    for (Map.Entry<Bytes32, Bytes> account : taskElement.keys().entrySet()) {
      final StateTrieAccountValue accountValue =
          StateTrieAccountValue.readFrom(RLP.input(account.getValue()));
      if (!accountValue.getStorageRoot().equals(Hash.EMPTY_TRIE_HASH)) {
        childRequests.add(
            createStorageRangeDataRequest(
                getRootHash(),
                account.getKey(),
                accountValue.getStorageRoot(),
                startStorageRange.orElse(MIN_RANGE),
                endStorageRange.orElse(MAX_RANGE)));
      }
      if (!accountValue.getCodeHash().equals(Hash.EMPTY)) {
        childRequests.add(
            createBytecodeRequest(account.getKey(), getRootHash(), accountValue.getCodeHash()));
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

  @VisibleForTesting
  public TreeMap<Bytes32, Bytes> getAccounts() {
    return stackTrie.getElement(startKeyHash).keys();
  }
}
