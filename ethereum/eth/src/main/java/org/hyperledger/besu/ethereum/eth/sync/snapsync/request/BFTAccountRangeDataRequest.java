/*
 * Copyright contributors to Hyperledger Besu.
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

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncMetricsManager.Step.DOWNLOAD;
import static org.hyperledger.besu.ethereum.trie.RangeManager.MAX_RANGE;
import static org.hyperledger.besu.ethereum.trie.RangeManager.MIN_RANGE;
import static org.hyperledger.besu.ethereum.trie.RangeManager.findNewBeginElementInRange;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncProcessState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapWorldDownloadState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.StackTrie;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.stream.Stream;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Returns a list of accounts and the merkle proofs of an entire range */
public class BFTAccountRangeDataRequest extends AccountRangeDataRequest {

  private static final Logger LOG = LoggerFactory.getLogger(BFTAccountRangeDataRequest.class);

  private Optional<Boolean> isProofValid;
  private boolean zeroAccounts = false;

  protected BFTAccountRangeDataRequest(
      final Hash rootHash,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final Optional<Bytes32> startStorageRange,
      final Optional<Bytes32> endStorageRange) {
    super(rootHash, startKeyHash, endKeyHash, startStorageRange, endStorageRange);
  }

  protected BFTAccountRangeDataRequest(
      final Hash rootHash, final Bytes32 startKeyHash, final Bytes32 endKeyHash) {
    this(rootHash, startKeyHash, endKeyHash, Optional.empty(), Optional.empty());
  }

  @Override
  public void addResponse(
      final WorldStateProofProvider worldStateProofProvider,
      final NavigableMap<Bytes32, Bytes> accounts,
      final ArrayDeque<Bytes> proofs) {
    if (accounts.isEmpty()) {
      zeroAccounts = true;
      isProofValid = Optional.of(true);
    } else {
      if (!proofs.isEmpty()) {
        if (!worldStateProofProvider.isValidRangeProof(
            startKeyHash, endKeyHash, getRootHash(), proofs, accounts)) {
          // this happens on repivot and on bad proofs
          LOG.atTrace()
              .setMessage("invalid range proof received for account range {} {}")
              .addArgument(accounts.firstKey())
              .addArgument(accounts.lastKey())
              .log();
          isProofValid = Optional.of(false);
        } else {
          stackTrie.addElement(startKeyHash, proofs, accounts);
          isProofValid = Optional.of(true);
        }
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
      final WorldStateStorageCoordinator worldStateStorageCoordinator,
      final SnapSyncProcessState snapSyncState) {
    final List<SnapDataRequest> childRequests = new ArrayList<>();

    if (!zeroAccounts) {
      final StackTrie.TaskElement taskElement = stackTrie.getElement(startKeyHash);
      // new request is added if the response does not match all the requested range
      findNewBeginElementInRange(
              getRootHash(), taskElement.proofs(), taskElement.keys(), endKeyHash)
          .ifPresentOrElse(
              missingRightElement -> {
                downloadState
                    .getMetricsManager()
                    .notifyRangeProgress(DOWNLOAD, missingRightElement, endKeyHash);
                childRequests.add(
                    createBFTAccountRangeDataRequest(
                        getRootHash(), missingRightElement, endKeyHash));
              },
              () ->
                  downloadState
                      .getMetricsManager()
                      .notifyRangeProgress(DOWNLOAD, endKeyHash, endKeyHash));

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
    }
    return childRequests.stream();
  }

  public static BFTAccountRangeDataRequest deserialize(final RLPInput in) {
    in.enterList();
    in.skipNext(); // skip request type
    final Hash rootHash = Hash.wrap(in.readBytes32());
    final Bytes32 startKeyHash = in.readBytes32();
    final Bytes32 endKeyHash = in.readBytes32();
    in.leaveList();
    return createBFTAccountRangeDataRequest(rootHash, startKeyHash, endKeyHash);
  }
}
